<div align="center">

# 🖥️ PCTracker

**Monitor and control your Windows PC from your Android phone — from anywhere in the world.**

[![Android](https://img.shields.io/badge/Android-Native-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![Python](https://img.shields.io/badge/Python-3.10+-3776AB?style=flat-square&logo=python&logoColor=white)](https://python.org)
[![Firebase](https://img.shields.io/badge/Firebase-FFCA28?style=flat-square&logo=firebase&logoColor=black)](https://firebase.google.com)
[![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)](LICENSE)

</div>

---

## What is PCTracker?

PCTracker is a personal remote monitoring and control system built with a native Android app and a lightweight Python background service running on Windows. It uses Firebase as the backbone — meaning everything works globally with zero extra infrastructure, no server to rent, no VPN to configure.

You get real-time visibility into what your PC is doing and full remote control over it, all from your phone.

---

## Features

### 📡 Live Monitoring
- **Active window tracking** — see exactly what app and window title is open on your PC in real time, pushed to your phone as notifications
- **Live screen streaming** — view your PC screen from your phone at up to 10 FPS (640×360) via Firebase Realtime Database
- **Screenshot on demand** — capture a full 960×540 screenshot and receive it instantly on your phone

### ⚡ Remote Control
Send any of these commands from your phone to your PC via Firestore:

| Command | What it does |
|---|---|
| `shutdown` | Shuts down the PC |
| `restart` | Restarts the PC |
| `lock` | Locks the Windows session |
| `sleep` | Puts the PC to sleep |
| `open_app` | Opens any application by name or path |
| `stats` | Returns CPU, RAM, and disk usage |
| `screenshot` | Sends a screenshot to your phone |
| `start_stream` | Starts live screen streaming |
| `stop_stream` | Stops live screen streaming |

### 🔒 Security
- All commands require a shared secret key — without it, commands are silently ignored
- Firebase security rules restrict write access to `stream/frame` (read-only for Android)
- No ports exposed on your PC, no public IP required
- Android broadcast receiver uses `RECEIVER_NOT_EXPORTED` — other apps cannot inject events

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                                                     │
│   Windows PC                                        │
│   ┌─────────────────────────────────────┐           │
│   │  pc_tracker_service.py              │           │
│   │                                     │           │
│   │  • Active window tracker            │           │
│   │  • SQLite persistent event queue    │           │
│   │  • FCM sender thread                │           │
│   │  • Firestore command listener       │           │
│   │  • RTDB frame writer (streaming)    │           │
│   └─────────────────────────────────────┘           │
│                                                     │
└────────────┬──────────────────────┬────────────────┘
             │                      │
       FCM (notifications)    Firestore (commands)
       RTDB  (stream frames)        │
             │                      │
┌────────────▼──────────────────────▼────────────────┐
│                                                     │
│   Firebase (Google Infrastructure)                  │
│   • Cloud Messaging  (FCM)                          │
│   • Firestore        (commands, device tokens)      │
│   • Realtime Database(stream frames)                │
│                                                     │
└────────────┬──────────────────────┬────────────────┘
             │                      │
     Notifications             Commands via
     Stream frames             Firestore write
             │                      │
┌────────────▼──────────────────────▼────────────────┐
│                                                     │
│   Android App                                       │
│   ┌─────────────────────────────────────┐           │
│   │  • PCFirebaseService  (FCM handler) │           │
│   │  • Room DB            (local logs)  │           │
│   │  • StreamManager      (RTDB listener│           │
│   │  • StreamActivity     (stream view) │           │
│   │  • MainActivity       (log feed)    │           │
│   └─────────────────────────────────────┘           │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### Why Firebase for streaming instead of WebSockets?
Most streaming solutions need a relay server in the cloud. Firebase Realtime Database eliminates that entirely — the PC writes frames directly to a database node and Android's `ValueEventListener` fires the instant the data changes. No server, no monthly cost, works from any network worldwide.

---

## Tech Stack

**Android App**
- Kotlin + Android SDK 34
- Firebase Cloud Messaging (FCM)
- Firebase Firestore
- Firebase Realtime Database
- Room (SQLite) for local log storage
- RecyclerView for the activity feed

**PC Service**
- Python 3.10+
- `firebase-admin` — Realtime Database writes
- `google-cloud-firestore` — command listener
- `mss` / `pyautogui` — screen capture
- `Pillow` — JPEG compression
- `pywin32` + `psutil` — window tracking
- `sqlite3` — persistent event queue

---

## Project Structure

```
pc-tracker/
│
├── pc_tracker_service.py          # Windows background service
├── pctracker-XXXX-firebase-adminsdk-XXXX.json  # ← DO NOT COMMIT
│
└── PCTracker/                     # Android app
    └── app/src/main/java/com/example/pctracker/
        ├── MainActivity.kt        # Activity log feed
        ├── PCFirebaseService.kt   # FCM message handler
        ├── PCLogDatabase.kt       # Room database
        ├── PCLogDao.kt            # Room DAO
        ├── PCLogEntity.kt         # Room entity
        ├── PCLogAdapter.kt        # RecyclerView adapter
        ├── PCEntry.kt             # UI model
        ├── StreamActivity.kt      # Live stream screen
        ├── StreamManager.kt       # RTDB stream listener
        ├── ImageStore.kt          # Screenshot storage
        ├── NotificationHelper.kt  # FCM notification builder
        ├── PCDataStore.kt         # In-memory store
        ├── PCTrackerApp.kt        # Application class
        └── PcLog.kt               # Data model
```

---

## Setup

### Prerequisites
- Windows 10/11 PC
- Android phone (API 26+)
- Firebase project (free Spark plan is sufficient)

---

### 1. Firebase Setup

1. Go to [Firebase Console](https://console.firebase.google.com) → Create project
2. Enable **Cloud Messaging**, **Firestore**, and **Realtime Database**
3. Download `google-services.json` → place it in `PCTracker/app/`
4. Download the **Admin SDK service account JSON** → place it next to `pc_tracker_service.py`
5. In Firestore, create collection `commands`, document `pc` with fields:
   ```
   action:    "none"
   timestamp: 0
   key:       "your_secret_key"
   ```
6. Set Firestore rules:
   ```js
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /commands/{doc}  { allow read, write: if true; }
       match /devices/{doc}   { allow read, write: if true; }
     }
   }
   ```
7. Set Realtime Database rules:
   ```json
   {
     "rules": {
       "stream": {
         ".read":  true,
         ".write": false
       }
     }
   }
   ```

---

### 2. PC Service Setup

```bash
pip install pywin32 psutil requests google-cloud-firestore firebase-admin mss pillow
```

Edit the config section at the top of `pc_tracker_service.py`:

```python
SERVICE_ACCOUNT_FILE = "your-firebase-adminsdk-xxxx.json"
RTDB_URL             = "https://your-project-default-rtdb.firebaseio.com"
COMMAND_SECRET       = "your_strong_secret_key"   # change this!
```

Run the service:
```bash
python pc_tracker_service.py
```

To auto-start on Windows boot, create a scheduled task:
- **Trigger:** At startup
- **Action:** `python C:\path\to\pc_tracker_service.py`
- **Run:** Whether user is logged on or not, with highest privileges

---

### 3. Android App Setup

1. Open the `PCTracker` folder in Android Studio
2. Place `google-services.json` in `app/`
3. Sync Gradle
4. Run on your phone

---

## Sending Commands

From the Android app, tap any command button. Internally it writes to Firestore:

```
Collection: commands
Document:   pc
Fields:
  action:    "shutdown"
  timestamp: <current millis>
  key:       "your_secret_key"
```

The PC service receives it within ~100ms via its realtime Firestore listener.

---

## Security Notes

> ⚠️ **Never commit your Firebase service account JSON or `google-services.json` to Git.**

The `.gitignore` already excludes them. If you accidentally push either file, rotate your Firebase credentials immediately in the Firebase Console.

The `COMMAND_SECRET` prevents anyone who can write to your Firestore database from sending commands to your PC. Use a strong random value — not `MY_SUPER_SECRET_KEY`.

---

## Limitations

| Limitation | Reason |
|---|---|
| Streaming stops when PC is locked | Windows blocks screen capture from locked sessions |
| ~10 FPS max on stream | Firebase RTDB propagation latency |
| Screenshot quality capped at 960×540 | Balance between quality and RTDB document size |
| Streaming not real-time video | Firebase is not a video streaming service; this uses frame-by-frame writes |

---

## Roadmap

- [ ] Wake-on-LAN support (power on PC remotely)
- [ ] Clipboard sync (phone ↔ PC)
- [ ] Multi-PC support (control multiple machines from one app)
- [ ] Command history log on Android
- [ ] Confirmation dialog before shutdown/restart
- [ ] File browser / file transfer

---

## License

MIT — do whatever you want with it.

---

<div align="center">
Built by <a href="https://github.com/shivammisra766">Shivam Misra</a>
</div>
