package com.example.pctracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PCLogAdapter : RecyclerView.Adapter<PCLogAdapter.VH>() {

    private val data = mutableListOf<PCEntry>()
    private val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val appText: TextView = view.findViewById(R.id.appText)
        val titleText: TextView = view.findViewById(R.id.titleText)
        val timeText: TextView = view.findViewById(R.id.timeText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pc_log, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = data[position]
        holder.appText.text = item.app
        holder.titleText.text = item.title
        holder.timeText.text = formatter.format(Date(item.timestamp))
    }

    override fun getItemCount(): Int = data.size

    fun update(newData: List<PCEntry>) {
        data.clear()
        data.addAll(newData)
        notifyDataSetChanged()
    }
}
