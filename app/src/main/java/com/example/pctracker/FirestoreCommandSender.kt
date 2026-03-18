package com.example.pctracker

import com.google.firebase.firestore.FirebaseFirestore

object FirestoreCommandSender {

    private const val SECRET = "YOUR_SECRET_KEY"

    fun sendCommand(action: String, app: String? = null) {

        val db = FirebaseFirestore.getInstance()

        val data = HashMap<String, Any>()

        data["action"] = action
        data["timestamp"] = System.currentTimeMillis()
        data["key"] = SECRET

        if (app != null) {
            data["app"] = app
        }

        db.collection("commands")
            .document("pc")
            .set(data)
    }
}