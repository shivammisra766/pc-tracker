package com.example.pctracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PCLogAdapter

    // UI Elements for Live Stats
    private lateinit var cpuText: TextView
    private lateinit var memText: TextView
    private lateinit var diskText: TextView

    // Job to handle the auto-refresh loop
    private var autoRefreshJob: Job? = null

    private val pcDataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.e("PC_TRACKER", "📺 UI RECEIVER TRIGGERED")

            // Extract the stats if they are attached to the broadcast intent
            val cpu = intent?.getStringExtra("cpu")
            val mem = intent?.getStringExtra("mem")
            val disk = intent?.getStringExtra("disk")

            if (cpu != null && mem != null && disk != null) {
                updateStatsUI(cpu, mem, disk)
            }

            loadLogs()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.e("PC_TRACKER", "🚀 MainActivity onCreate")

        // Initialize RecyclerView
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = PCLogAdapter()
        recyclerView.adapter = adapter

        // Bind Buttons
        val shutdownBtn = findViewById<MaterialButton>(R.id.shutdownBtn)
        val restartBtn = findViewById<MaterialButton>(R.id.restartBtn)
        val lockBtn = findViewById<MaterialButton>(R.id.lockBtn)
        val streamStartBtn = findViewById<MaterialButton>(R.id.streamStartBtn)

        // Bind the circular Stats Layout & Text Views
        val statsBtn = findViewById<LinearLayout>(R.id.statsBtn)
        cpuText = findViewById(R.id.cpuText)
        memText = findViewById(R.id.memText)
        diskText = findViewById(R.id.diskText)

        // --- CLICK LISTENERS ---

        shutdownBtn.setOnClickListener {
            FirestoreCommandSender.sendCommand("shutdown")
        }

        restartBtn.setOnClickListener {
            FirestoreCommandSender.sendCommand("restart")
        }

        lockBtn.setOnClickListener {
            FirestoreCommandSender.sendCommand("lock")
        }

        statsBtn.setOnClickListener {
            // Manual refresh fallback
            FirestoreCommandSender.sendCommand("get_stats")
        }

        streamStartBtn.setOnClickListener {
            FirestoreCommandSender.sendCommand("start_stream")
            val intent = Intent(this, StreamActivity::class.java)
            startActivity(intent)
        }

        loadLogs()
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            pcDataReceiver,
            IntentFilter("PC_DATA_UPDATE"),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Start polling for stats when the app is open
        startAutoRefresh()
    }

    override fun onStop() {
        super.onStop()
        // Stop polling to save battery when app is hidden
        autoRefreshJob?.cancel()

        try {
            unregisterReceiver(pcDataReceiver)
        } catch (e: Exception) {
            Log.e("PC_TRACKER", "Receiver already unregistered")
        }
    }

    private fun startAutoRefresh() {
        autoRefreshJob = lifecycleScope.launch {
            while (isActive) {
                // Request live stats from PC
                FirestoreCommandSender.sendCommand("get_stats")
                // Wait 5 seconds before asking again
                delay(5000)
            }
        }
    }

    private fun updateStatsUI(cpu: String, mem: String, disk: String) {
        cpuText.text = "CPU $cpu%"
        memText.text = "RAM $mem%"
        diskText.text = "DSK $disk%"
    }
    private fun loadLogs() {
        lifecycleScope.launch {
            val entities = withContext(Dispatchers.IO) {
                PCLogDatabase
                    .getInstance(this@MainActivity)
                    .dao()
                    .getAll()
            }

            Log.e("PC_TRACKER", "📊 LOAD LOGS → size=${entities.size}")

            // Explicitly sort by timestamp from largest (newest) to smallest (oldest)
            val data = entities.sortedByDescending { it.timestamp }.map {
                PCEntry(
                    app = it.app,
                    title = it.title,
                    timestamp = it.timestamp
                )
            }

            adapter.update(data)

            if (data.isNotEmpty()) {
                // Keep the view pinned to the very top where the newest log is
                recyclerView.scrollToPosition(0)
            }
        }
    }}