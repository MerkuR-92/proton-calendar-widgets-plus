package me.proton.android.calendar.presentation.importAssistant.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.item_import_status.view.item_import_status_account
import kotlinx.android.synthetic.main.item_import_status.view.item_import_status_badge
import kotlinx.android.synthetic.main.item_import_status.view.item_import_status_details
import kotlinx.android.synthetic.main.item_import_status.view.item_import_status_icon
import me.proton.android.calendar.R
import me.proton.android.calendar.common.logger.TimberLogger
import me.proton.android.calendar.common.utils.AndroidUtils.humanReadableByteCountSI
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.domain.model.Import
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class ImportStatusListAdapter(
    val listener: (Import, Action) -> Unit
): ListAdapter<Import, ImportStatusListAdapter.ViewHolder>(ImportDiffCallback()) {

    enum class Action {
        CANCEL,
        RESUME,
        DELETE
    }

    class ImportDiffCallback : DiffUtil.ItemCallback<Import>() {
        override fun areItemsTheSame(oldItem: Import, newItem: Import): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Import, newItem: Import): Boolean {
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
        private val details: TextView = view.item_import_status_details
        private val badge: View = view.item_import_status_badge
        private val icon: ImageView = view.item_import_status_icon

        fun bind(import : Import) {

            account.text = import.account
            val importSize = humanReadableByteCountSI(import.size?.toLong() ?: 0L)
            details.text =
                if (import.size != null) {
                    itemView.context.getString(
                        R.string.import_assistant_report_details,
                        importSize,
                        import.dateTime?.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))
                            ?: ""
                    )
                } else {
                    import.dateTime?.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))
                        ?: ""
                }

            icon.visibleOrGone(import.state != null)
            badge.visibleOrGone(import.state != null)
            if (import.state != null) {
                (badge as TextView).text = itemView.context.getString(
                    when (import.state) {
                        Import.ImportState.QUEUED,
                        Import.ImportState.RUNNING -> R.string.import_assistant_status_in_progress
                        Import.ImportState.DONE -> R.string.import_assistant_status_completed
                        Import.ImportState.FAILED -> R.string.import_assistant_status_failed
                        Import.ImportState.PAUSED -> R.string.import_assistant_status_paused
                        Import.ImportState.CANCELED -> R.string.import_assistant_status_canceled
                    }
                )
                badge.setTextColor(
                    ContextCompat.getColor(
                        itemView.context,
                        when (import.state) {
                            Import.ImportState.QUEUED,
                            Import.ImportState.RUNNING,
                            Import.ImportState.PAUSED -> R.color.text_norm
                            Import.ImportState.DONE,
                            Import.ImportState.FAILED,
                            Import.ImportState.CANCELED -> R.color.text_inverted
                        }
                    )
                )
                badge.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(
                        itemView.context,
                        when (import.state) {
                            Import.ImportState.QUEUED,
                            Import.ImportState.RUNNING,
                            Import.ImportState.PAUSED -> R.color.background_secondary
                            Import.ImportState.DONE -> R.color.notification_success
                            Import.ImportState.FAILED,
                            Import.ImportState.CANCELED -> R.color.notification_error
                        }
                    )
                )

                icon.setImageDrawable(
                    ContextCompat.getDrawable(
                        itemView.context,
                        when (import.state) {
                            Import.ImportState.QUEUED,
                            Import.ImportState.RUNNING -> R.drawable.ic_proton_cross
                            Import.ImportState.PAUSED -> R.drawable.ic_proton_play
                            Import.ImportState.DONE,
                            Import.ImportState.FAILED,
                            Import.ImportState.CANCELED -> R.drawable.ic_proton_trash
                        }
                    )
                )

                icon.setOnSingleClickListener {
                    when (import.state) {
                        Import.ImportState.QUEUED,
                        Import.ImportState.RUNNING -> listener(import, Action.CANCEL)
                        Import.ImportState.PAUSED -> listener(import, Action.RESUME)
                        Import.ImportState.DONE,
                        Import.ImportState.FAILED,
                        Import.ImportState.CANCELED -> listener(import, Action.DELETE)
                    }
                }
            }
        }

    }
}
