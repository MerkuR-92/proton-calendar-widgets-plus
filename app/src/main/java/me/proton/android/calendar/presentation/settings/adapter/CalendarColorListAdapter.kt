package me.proton.android.calendar.presentation.settings.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.databinding.ItemCalendarColorPickerBinding
import me.proton.core.presentation.utils.ProtonAccentColorCompat

class CalendarColorListAdapter(
    val colors: List<Int>,
    val selectedColor: Int?,
    val listener: (Int) -> Unit
): BaseAdapter() {

    override fun getCount(): Int = colors.size

    override fun getItem(position: Int): Int = colors[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val viewBinding = ItemCalendarColorPickerBinding.inflate(
            LayoutInflater.from(parent?.context),
            null,
            false
        )

        val colorItemFilled: ImageView = viewBinding.itemCalendarColorPickerFilled
        val colorItemMain: ImageView = viewBinding.itemCalendarColorPickerMain
        val colorItemOuter: ImageView = viewBinding.itemCalendarColorPickerOuter
        val colorItemInner: ImageView = viewBinding.itemCalendarColorPickerInner

        val color = getItem(position)

        colorItemFilled.backgroundTintList = ColorStateList.valueOf(color)

        colorItemMain.backgroundTintList = ColorStateList.valueOf(color)

        colorItemOuter.backgroundTintList = ColorStateList.valueOf(ProtonAccentColorCompat(color).intense)

        val selected = color == selectedColor
        colorItemFilled.visibleOrGone(!selected)
        colorItemMain.visibleOrGone(selected)
        colorItemOuter.visibleOrGone(selected)
        colorItemInner.visibleOrGone(selected)

        viewBinding.root.setOnSingleClickListener {
            listener(color)
        }

        return viewBinding.root
    }
}
