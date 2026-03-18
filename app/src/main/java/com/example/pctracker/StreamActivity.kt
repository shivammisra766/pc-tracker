package com.example.pctracker

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class StreamActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var statusText: TextView
    private lateinit var hintText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on while streaming
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_stream)

        imageView = findViewById(R.id.streamImage)
        statusText = findViewById(R.id.streamStatus)
        hintText = findViewById(R.id.streamHint)

        showWaiting()

        // --- BACK BUTTON HANDLING ---
        // Intercept the back press to stop the stream on the PC side
        val backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Tell the PC to kill the stream
                FirestoreCommandSender.sendCommand("stop_stream")

                // Safely close this Activity
                finish()
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback)
        // ----------------------------

        // StreamManager calls this on the RTDB listener thread.
        // Post to main thread before touching any views.
        StreamManager.onFrameUpdated = {
            runOnUiThread {
                val frame = StreamManager.currentFrame
                if (frame != null) {
                    imageView.setImageBitmap(frame)
                    showStreaming()
                }
            }
        }

        StreamManager.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up local resources when the activity is destroyed
        StreamManager.stop()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // ── UI states ─────────────────────────────────────────────────────

    private fun showWaiting() {
        statusText.visibility = View.VISIBLE
        hintText.visibility = View.VISIBLE
        statusText.text = "Waiting for stream…"
        hintText.text = "Sending start_stream command to your PC..."
    }

    private fun showStreaming() {
        // Only hide the text if it's currently visible to save UI rendering cycles
        if (statusText.visibility != View.GONE) {
            statusText.visibility = View.GONE
            hintText.visibility = View.GONE
        }
    }
}