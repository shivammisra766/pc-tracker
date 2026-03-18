package com.example.pctracker

import android.content.Intent
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PCFirebaseService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {

        val data = message.data
        Log.e("PC_TRACKER", "📩 DATA => $data")

        // 1. INTERCEPT LIVE STATS FIRST
        if (data["source"] == "pc_stats") {
            val cpu = data["cpu"] ?: "--"
            val mem = data["mem"] ?: "--"
            val disk = data["disk"] ?: "--"

            Log.e("PC_TRACKER", "📈 BROADCASTING STATS => CPU:$cpu MEM:$mem DISK:$disk")

            // Send directly to MainActivity UI
            val intent = Intent("PC_DATA_UPDATE")
            intent.putExtra("cpu", cpu)
            intent.putExtra("mem", mem)
            intent.putExtra("disk", disk)
            intent.setPackage(packageName)
            sendBroadcast(intent)

            // CRITICAL: Return immediately so it DOES NOT hit the database logic below
            return
        }

        // 2. HANDLE EVERYTHING ELSE (Images & Logs)
        when (data["type"]) {

            "screenshot" -> {
                val image = data["image"] ?: return
                ImageStore.saveScreenshot(applicationContext, image)
                Log.e("PC_TRACKER", "🖼 Screenshot saved (FCM fallback)")
            }

            "stream_frame" -> {
                val image = data["image"] ?: return
                StreamManager.updateFrame(image)
                Log.e("PC_TRACKER", "🎥 Stream frame updated (FCM fallback)")
            }

            // Normal activity log — window title notification
            else -> {
                val app   = data["app"]   ?: "PC"
                val title = data["title"] ?: "Activity changed"

                val entry = PCLogEntity(
                    app       = app,
                    title     = title,
                    timestamp = System.currentTimeMillis(),
                )

                CoroutineScope(Dispatchers.IO).launch {
                    PCLogDatabase
                        .getInstance(applicationContext)
                        .dao()
                        .insert(entry)
                    Log.e("PC_TRACKER", "💾 SAVED TO DB")
                }

                NotificationHelper.show(this, app, title)

                val intent = Intent("PC_DATA_UPDATE")
                intent.setPackage(packageName)
                sendBroadcast(intent)
            }
        }
    }

    // 3. AUTO-UPLOAD FCM TOKEN
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.e("PC_TRACKER", "🔄 NEW FCM TOKEN: $token")

        val db = FirebaseFirestore.getInstance()
        val tokenData = hashMapOf(
            "fcmToken" to token,
            "updatedAt" to System.currentTimeMillis()
        )

        // Save to the exact path your Python script is looking for
        db.collection("devices").document("android")
            .set(tokenData)
            .addOnSuccessListener {
                Log.e("PC_TRACKER", "✅ Token securely saved to Firestore!")
            }
            .addOnFailureListener { e ->
                Log.e("PC_TRACKER", "❌ Failed to save token", e)
            }
    }
}