package com.example.pctracker

import android.util.Log

object PCDataStore {

    private val entries = mutableListOf<PCEntry>()

    fun add(entry: PCEntry) {
        entries.add(0, entry) // newest on top
        Log.e("PC_TRACKER", "🧠 STORE ADD → size=${entries.size} | ${entry.app}")
    }

    fun getAll(): List<PCEntry> {
        Log.e("PC_TRACKER", "📤 STORE GET → size=${entries.size}")
        return entries.toList()
    }
}
