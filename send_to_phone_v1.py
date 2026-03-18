import base64
import io
import logging
import os
import subprocess
import sys
import threading
import time
import sqlite3

# Standard library for environment variables
from dotenv import load_dotenv 

import psutil
import requests
import win32gui
import win32process

try:
    import mss
    _USE_MSS = True
except ImportError:
    _USE_MSS = False

from PIL import Image
from google.auth.transport.requests import Request
from google.cloud import firestore
from google.oauth2 import service_account

import firebase_admin
from firebase_admin import credentials as fb_credentials
from firebase_admin import db as rtdb

# Load secrets from .env file
load_dotenv()

# ═══════════════════════════════════════════════════════════
#  CONFIG (Environment Variables)
# ═══════════════════════════════════════════════════════════

_SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

# Values pulled from .env
SERVICE_ACCOUNT_FILE = os.getenv("SERVICE_ACCOUNT_PATH")
RTDB_URL = os.getenv("RTDB_URL")
COMMAND_SECRET = os.getenv("COMMAND_SECRET")

# Safe local paths (No longer leaks Windows username)
LOG_FILE = os.path.join(_SCRIPT_DIR, "pc_tracker.log")
DB_FILE  = os.path.join(_SCRIPT_DIR, "pc_tracker_queue.db")

SCOPES = ["https://www.googleapis.com/auth/firebase.messaging"]
SEND_INTERVAL = 2
STREAM_FPS     = 10
STREAM_WIDTH   = 640
STREAM_HEIGHT  = 360
STREAM_QUALITY = 50
SS_WIDTH   = 960
SS_HEIGHT  = 540
SS_QUALITY = 60
TOKEN_TTL = 50 * 60

# ═══════════════════════════════════════════════════════════
#  LOGGING
# ═══════════════════════════════════════════════════════════

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s  %(levelname)-8s  %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
    handlers=[
        logging.FileHandler(LOG_FILE, encoding="utf-8"),
        logging.StreamHandler(sys.stdout),
    ],
)
log = logging.getLogger("PCTracker")

# ═══════════════════════════════════════════════════════════
#  SQLITE PERSISTENT QUEUE
# ═══════════════════════════════════════════════════════════

_db_lock = threading.Lock()
_conn    = sqlite3.connect(DB_FILE, check_same_thread=False)

with _db_lock:
    _conn.execute("""
        CREATE TABLE IF NOT EXISTS event_queue (
            id    INTEGER PRIMARY KEY AUTOINCREMENT,
            app   TEXT    NOT NULL,
            title TEXT    NOT NULL
        )
    """)
    _conn.commit()

def queue_event(app: str, title: str) -> None:
    with _db_lock:
        _conn.execute("INSERT INTO event_queue (app, title) VALUES (?, ?)", (app, title))
        _conn.commit()

def _get_next_event():
    with _db_lock:
        return _conn.execute("SELECT id, app, title FROM event_queue ORDER BY id LIMIT 1").fetchone()

def _delete_event(event_id: int) -> None:
    with _db_lock:
        _conn.execute("DELETE FROM event_queue WHERE id = ?", (event_id,))
        _conn.commit()

# ═══════════════════════════════════════════════════════════
#  FIREBASE & FIRESTORE INITIALIZATION
# ═══════════════════════════════════════════════════════════

_rtdb_ref = None
_db = None

if SERVICE_ACCOUNT_FILE and os.path.exists(SERVICE_ACCOUNT_FILE):
    try:
        fb_cred = fb_credentials.Certificate(SERVICE_ACCOUNT_FILE)
        firebase_admin.initialize_app(fb_cred, {"databaseURL": RTDB_URL})
        _rtdb_ref = rtdb.reference("stream/frame")
        
        _creds_fs = service_account.Credentials.from_service_account_file(SERVICE_ACCOUNT_FILE)
        _db = firestore.Client(credentials=_creds_fs)
        log.info("Firebase services initialized successfully")
    except Exception as exc:
        log.error("Firebase initialization failed: %s", exc)
else:
    log.error("Service account file not found! Check your .env config.")

# ═══════════════════════════════════════════════════════════
#  FCM AUTH & SENDING
# ═══════════════════════════════════════════════════════════

_fcm_creds = None
_access_token = None
_token_fetched_at = 0.0
_FCM_URL = None
_token_lock = threading.Lock()

def _load_fcm_creds():
    global _fcm_creds, _FCM_URL
    if not SERVICE_ACCOUNT_FILE: return
    _fcm_creds = service_account.Credentials.from_service_account_file(SERVICE_ACCOUNT_FILE, scopes=SCOPES)
    _FCM_URL = f"https://fcm.googleapis.com/v1/projects/{_fcm_creds.project_id}/messages:send"

def get_access_token():
    global _access_token, _token_fetched_at
    with _token_lock:
        if time.time() - _token_fetched_at < TOKEN_TTL and _access_token:
            return _access_token
        try:
            if _fcm_creds is None: _load_fcm_creds()
            _fcm_creds.refresh(Request())
            _access_token = _fcm_creds.token
            _token_fetched_at = time.time()
            return _access_token
        except Exception as exc:
            log.error("FCM refresh error: %s", exc)
            return None

_device_token = None
_device_token_fetched = 0.0

def get_device_token():
    global _device_token, _device_token_fetched
    if _db is None: return None
    if _device_token and time.time() - _device_token_fetched < 300:
        return _device_token
    try:
        doc = _db.collection("devices").document("android").get()
        if doc.exists:
            _device_token = doc.to_dict().get("fcmToken")
            _device_token_fetched = time.time()
    except Exception:
        pass
    return _device_token

def _fcm_post(data_payload: dict) -> bool:
    token = get_device_token()
    tok = get_access_token()
    if not token or not tok or not _FCM_URL: return False
    body = {"message": {"token": token, "data": data_payload}}
    headers = {"Authorization": f"Bearer {tok}", "Content-Type": "application/json"}
    try:
        resp = requests.post(_FCM_URL, headers=headers, json=body, timeout=8)
        return resp.status_code == 200
    except Exception:
        return False

# ═══════════════════════════════════════════════════════════
#  SENDER WORKER
# ═══════════════════════════════════════════════════════════

def _sender_worker():
    while True:
        row = _get_next_event()
        if row is None:
            time.sleep(0.5)
            continue
        event_id, app, title = row
        if _fcm_post({"source": "pc", "app": app, "title": title}):
            _delete_event(event_id)
        else:
            time.sleep(3)

threading.Thread(target=_sender_worker, daemon=True, name="Sender").start()

# ═══════════════════════════════════════════════════════════
#  SCREEN & STREAMING
# ═══════════════════════════════════════════════════════════

_streaming = False

def capture_screen():
    try:
        if _USE_MSS:
            with mss.mss() as sct:
                raw = sct.grab(sct.monitors[1])
                return Image.frombytes("RGB", raw.size, raw.rgb)
        else:
            import pyautogui
            return pyautogui.screenshot()
    except: return None

def _encode_b64(img, width, height, quality):
    try:
        thumb = img.resize((width, height), Image.LANCZOS)
        buf = io.BytesIO()
        thumb.save(buf, format="JPEG", quality=quality, optimize=True)
        return base64.b64encode(buf.getvalue()).decode("ascii")
    except: return None

def _write_frame(b64: str, frame_type: str = "stream") -> bool:
    if _rtdb_ref is None: return False
    try:
        _rtdb_ref.set({
            "data": b64,
            "ts": int(time.time() * 1000),
            "type": frame_type,
            "active": True,
        })
        return True
    except: return False

def _stream_worker():
    interval = 1.0 / max(STREAM_FPS, 1)
    while True:
        if not _streaming:
            time.sleep(0.5)
            continue
        start = time.time()
        img = capture_screen()
        if img:
            b64 = _encode_b64(img, STREAM_WIDTH, STREAM_HEIGHT, STREAM_QUALITY)
            if b64: _write_frame(b64, "stream")
        elapsed = time.time() - start
        time.sleep(max(0, interval - elapsed))

threading.Thread(target=_stream_worker, daemon=True, name="Streamer").start()

# ═══════════════════════════════════════════════════════════
#  COMMANDS
# ═══════════════════════════════════════════════════════════

def _execute_command(action, data):
    global _streaming
    log.info("Action: %s", action)
    if action == "shutdown": subprocess.run("shutdown /s /t 0", shell=True)
    elif action == "lock": subprocess.run("rundll32.exe user32.dll,LockWorkStation", shell=True)
    elif action == "start_stream": _streaming = True
    elif action == "stop_stream":
        _streaming = False
        if _rtdb_ref: _rtdb_ref.set({"active": False})
    elif action == "get_stats":
        cpu = psutil.cpu_percent(interval=0.2)
        ram = psutil.virtual_memory().percent
        _fcm_post({"source": "pc_stats", "cpu": str(int(cpu)), "mem": str(int(ram))})

_last_command_ts = None

def _command_listener(doc_snapshot, changes, read_time):
    global _last_command_ts
    for doc in doc_snapshot:
        if not doc.exists: continue
        data = doc.to_dict()
        if data.get("key") != COMMAND_SECRET: continue
        ts = data.get("timestamp")
        if _last_command_ts is None: _last_command_ts = ts; return
        if ts == _last_command_ts: continue
        _last_command_ts = ts
        threading.Thread(target=_execute_command, args=(data.get("action"), data), daemon=True).start()

if _db:
    _db.collection("commands").document("pc").on_snapshot(_command_listener)

# ═══════════════════════════════════════════════════════════
#  MAIN LOOP
# ═══════════════════════════════════════════════════════════

def get_active_window():
    try:
        hwnd = win32gui.GetForegroundWindow()
        title = win32gui.GetWindowText(hwnd)
        _, pid = win32process.GetWindowThreadProcessId(hwnd)
        app = psutil.Process(pid).name()
        return app, title
    except: return None

def main():
    last_sent = None
    while True:
        try:
            info = get_active_window()
            if info:
                app, title = info
                key = f"{app}|{title}"
                if key != last_sent:
                    queue_event(app, title)
                    last_sent = key
            time.sleep(SEND_INTERVAL)
        except KeyboardInterrupt: break
        except Exception: time.sleep(2)

if __name__ == "__main__":
    main()