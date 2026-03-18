package com.example.pctracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PCEntryAdapter(
    private var items: List<PCEntry>
) : RecyclerView.Adapter<PCEntryAdapter.ViewHolder>() {

    fun update(newItems: List<PCEntry>) {
        items = newItems
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appText: TextView = view.findViewById(R.id.appText)
        val titleText: TextView = view.findViewById(R.id.titleText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pc_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.appText.text = item.app
        holder.titleText.text = item.title
    }

    override fun getItemCount() = items.size
}
