package me.proton.android.calendar.presentation.importAssistant.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.item_import_status.view.item_import_status_account
import me.proton.android.calendar.R

class ImportStatusListAdapter(
    val listener: (ImporterEntity) -> Unit
): ListAdapter<ImporterEntity, ImportStatusListAdapter.ViewHolder>(ImporterEntityDiffCallback()) {

    class ImporterEntityDiffCallback : DiffUtil.ItemCallback<ImporterEntity>() {
        override fun areItemsTheSame(oldItem: ImporterEntity, newItem: ImporterEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ImporterEntity, newItem: ImporterEntity): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_import_status, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val account: TextView = view.item_import_status_account

        fun bind(importerEntity : ImporterEntity) {

        }

    }
}
