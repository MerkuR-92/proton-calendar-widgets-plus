package me.proton.android.calendar.presentation.importAssistant.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.item_badge.view.item_settings_calendar_badge
import kotlinx.android.synthetic.main.item_import_calendar.view.item_import_calendar_checkbox
import kotlinx.android.synthetic.main.item_import_calendar.view.item_import_calendar_destination_badge
import kotlinx.android.synthetic.main.item_import_calendar.view.item_import_calendar_destination_email
import kotlinx.android.synthetic.main.item_import_calendar.view.item_import_calendar_destination_layout
import kotlinx.android.synthetic.main.item_import_calendar.view.item_import_calendar_destination_options
import kotlinx.android.synthetic.main.item_import_calendar.view.item_import_calendar_destination_press
import kotlinx.android.synthetic.main.item_import_calendar.view.item_import_calendar_destination_title
import kotlinx.android.synthetic.main.item_import_calendar.view.item_import_calendar_press
import kotlinx.android.synthetic.main.item_import_calendar.view.item_import_calendar_secondary_press
import kotlinx.android.synthetic.main.item_import_calendar.view.item_import_calendar_source_email
import kotlinx.android.synthetic.main.item_import_calendar.view.item_import_calendar_source_title
import me.proton.android.calendar.R
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.domain.model.ImportCalendarMapping

class ImportCalendarMappingListAdapter(
    val importListener: (ImportCalendarMapping) -> Unit,
    val optionsListener: (ImportCalendarMapping) -> Unit
) : ListAdapter<ImportCalendarMapping, ImportCalendarMappingListAdapter.ViewHolder>(ExternalCalendarEntityDiffCallback()) {

    private var limitReached = false

    fun setLimitReached(limitReached: Boolean) {
        val valueChanged = this.limitReached != limitReached
        this.limitReached = limitReached
        if (valueChanged) notifyDataSetChanged()
    }

    class ExternalCalendarEntityDiffCallback : DiffUtil.ItemCallback<ImportCalendarMapping>() {
        override fun areItemsTheSame(oldItem: ImportCalendarMapping, newItem: ImportCalendarMapping): Boolean {
            return oldItem.sourceName == newItem.sourceName
        }

        override fun areContentsTheSame(oldItem: ImportCalendarMapping, newItem: ImportCalendarMapping): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_import_calendar, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val sourceTitle: TextView = view.item_import_calendar_source_title
        private val sourceEmail: TextView = view.item_import_calendar_source_email
        private val importCheckBox: CheckBox = view.item_import_calendar_checkbox
        private val primaryPressOverlay: View = view.item_import_calendar_press
        private val secondaryPressOverlay: View = view.item_import_calendar_secondary_press
        private val destinationLayout: ConstraintLayout = view.item_import_calendar_destination_layout
        private val destinationTitle: TextView = view.item_import_calendar_destination_title
        private val destinationEmail: TextView = view.item_import_calendar_destination_email
        private val destinationBadgeView: View = view.item_import_calendar_destination_badge
        private val destinationPressOverlay: View = view.item_import_calendar_destination_press
        private val destinationOptionsButton: ImageView = view.item_import_calendar_destination_options

        fun bind(importCalendarMapping : ImportCalendarMapping) {

            sourceTitle.setTextColor(
                ContextCompat.getColor(itemView.context,
                    if (importCalendarMapping.importCalendar) R.color.text_norm
                    else R.color.text_weak
                )
            )
            sourceTitle.text = importCalendarMapping.sourceName
            sourceEmail.text = importCalendarMapping.sourceEmail

            destinationLayout.visibleOrGone(importCalendarMapping.importCalendar)
            destinationTitle.text = importCalendarMapping.destinationName
            destinationEmail.text = importCalendarMapping.destinationEmail
            (destinationBadgeView as TextView).text = itemView.context.getString(
                if (importCalendarMapping.createDestinationCalendar) R.string.import_assistant_new_calendar_badge
                else R.string.import_assistant_merge_calendar_badge
            )

            importCheckBox.isChecked = importCalendarMapping.importCalendar
            importCheckBox.setOnSingleClickListener {
                importListener(importCalendarMapping)
            }

            primaryPressOverlay.visibleOrGone(!importCalendarMapping.importCalendar)
            primaryPressOverlay.setOnSingleClickListener {
                importCheckBox.performClick()
            }
            secondaryPressOverlay.visibleOrGone(importCalendarMapping.importCalendar)
            secondaryPressOverlay.setOnSingleClickListener {
                importCheckBox.performClick()
            }

            destinationPressOverlay.setOnSingleClickListener {
                optionsListener(importCalendarMapping)
            }

            destinationOptionsButton.setOnSingleClickListener {
                optionsListener(importCalendarMapping)
            }

            if (limitReached && importCalendarMapping.createDestinationCalendar) {
                destinationPressOverlay.background = ContextCompat.getDrawable(
                    itemView.context,
                    R.drawable.shape_background_secondary_rounded_error
                )
            } else {
                destinationPressOverlay.background = ContextCompat.getDrawable(
                    itemView.context,
                    R.drawable.shape_background_secondary_rounded
                )
            }
        }

    }
}
