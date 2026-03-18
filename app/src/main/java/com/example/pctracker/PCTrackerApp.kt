package com.example.pctracker

import android.app.Application
import com.google.firebase.FirebaseApp

class PCTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}
