package me.proton.android.calendar.presentation.settings

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import kotlinx.android.synthetic.main.item_calendar_color_picker.view.*
import me.proton.android.calendar.R
import me.proton.android.calendar.common.AndroidUtils
import me.proton.android.calendar.common.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.AndroidUtils.visibleOrGone
import me.proton.core.util.kotlin.equalsNoCase

class CalendarColorListAdapter(
    val colors: List<String>,
    val selectedColor: String?,
    val listener: (String) -> Unit
): BaseAdapter() {

    override fun getCount(): Int = colors.size

    override fun getItem(position: Int): String = colors[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = LayoutInflater.from(parent?.context).inflate(R.layout.item_calendar_color_picker, null)

        val colorItemFilled: ImageView = view.findViewById(R.id.item_calendar_color_picker_filled)
        val colorItemMain: ImageView = view.findViewById(R.id.item_calendar_color_picker_main)
        val colorItemOuter: ImageView = view.findViewById(R.id.item_calendar_color_picker_outer)
        val colorItemInner: ImageView = view.findViewById(R.id.item_calendar_color_picker_inner)

        val color = getItem(position)

        colorItemFilled.backgroundTintList = ColorStateList.valueOf(
            Color.parseColor(color)
        )

        colorItemMain.backgroundTintList = ColorStateList.valueOf(
            Color.parseColor(color)
        )

        colorItemOuter.backgroundTintList = ColorStateList.valueOf(
            Color.parseColor(AndroidUtils.darkenCalendarColor(color))
        )

        val selected = color.equalsNoCase(selectedColor)
        colorItemFilled.visibleOrGone(!selected)
        colorItemMain.visibleOrGone(selected)
        colorItemOuter.visibleOrGone(selected)
        colorItemInner.visibleOrGone(selected)

        view.setOnSingleClickListener {
            listener(color)
        }

        return view
    }
}
