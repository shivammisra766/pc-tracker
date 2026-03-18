package com.example.pctracker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.File
import java.io.FileOutputStream

object ImageStore {

    fun saveScreenshot(context: Context, base64: String) {

        val bytes = Base64.decode(base64, Base64.DEFAULT)

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        val file = File(context.filesDir, "latest_screen.jpg")

        val stream = FileOutputStream(file)

        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)

        stream.flush()
        stream.close()
    }
}