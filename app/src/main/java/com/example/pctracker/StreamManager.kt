package com.example.pctracker

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.google.firebase.database.*

object StreamManager {

    private const val TAG  = "StreamManager"
    private const val PATH = "stream/frame"

    @Volatile var currentFrame: Bitmap? = null
    var onFrameUpdated: (() -> Unit)? = null
    @Volatile var isStreamActive: Boolean = false

    private var listener: ValueEventListener? = null

    private val ref by lazy {
        FirebaseDatabase.getInstance().getReference(PATH)
    }

    fun start() {
        if (listener != null) return

        val l = object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {

                isStreamActive = snapshot.child("active")
                    .getValue(Boolean::class.java) ?: false

                val b64 = snapshot.child("data")
                    .getValue(String::class.java) ?: return

                val bmp = decodeBase64(b64)

                if (bmp != null) {
                    currentFrame = bmp
                    onFrameUpdated?.invoke()
                } else {
                    Log.w(TAG, "Frame decode failed")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "RTDB error: ${error.message}")
            }
        }

        ref.addValueEventListener(l)
        listener = l

        Log.i(TAG, "Stream started")
    }

    fun stop() {
        listener?.let { ref.removeEventListener(it) }
        listener = null
        onFrameUpdated = null

        Log.i(TAG, "Stream stopped")
    }

    fun decodeBase64(b64: String): Bitmap? {
        return try {
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Decode error: ${e.message}")
            null
        }
    }

    fun updateFrame(b64: String) {
        val bmp = decodeBase64(b64)

        if (bmp != null) {
            currentFrame = bmp
            onFrameUpdated?.invoke()
        } else {
            Log.w(TAG, "updateFrame: decode failed")
        }
    }

}