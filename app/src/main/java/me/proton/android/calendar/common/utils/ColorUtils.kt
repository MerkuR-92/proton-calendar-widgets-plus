package me.proton.android.calendar.common.utils

import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.proton.android.calendar.R
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.databinding.ItemColorPickerBinding

object ColorUtils {

    data class ColorNameWithHex(
        val name: String,
        val hexString: String
    )

    fun getColorNameWithHexList(colorArray: Array<String>): List<ColorNameWithHex> {
        return colorArray.map { colorResource ->
            toColorNameWithHex(colorResource)
        }
    }

    fun getRandomCalendarColorHexString(colorArray: Array<String>): String {
        return toColorNameWithHex(colorArray.random()).hexString
    }

    fun getColorNameForHex(colorArray: Array<String>, colorHex: String): String? {
        return colorArray.map {
            toColorNameWithHex(it)
        }.firstOrNull {
            it.hexString == colorHex
        }?.name
    }

    private fun toColorNameWithHex(colorResource: String): ColorNameWithHex {
        val splitResult = colorResource.split("|")
        return ColorNameWithHex(
            name = splitResult[0],
            hexString = splitResult[1]
        )
    }

    fun displayColorPicker(
        context: Context,
        title: String?,
        colorArray: Array<String>,
        selectedColorHexString: String,
        defaultCalendarColorHexString: String? = null,
        callback: (selectedColorHexString: String) -> Unit
    ) {

        val adapter = object : ArrayAdapter<ColorNameWithHex>(context, R.layout.item_color_picker) {

            lateinit var dialog: DialogInterface

            val colorNameWithHexList = getColorNameWithHexList(colorArray)

            override fun getCount(): Int = colorArray.size

            override fun getItem(position: Int): ColorNameWithHex = colorNameWithHexList[position]

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val viewBinding = ItemColorPickerBinding.inflate(
                    LayoutInflater.from(parent?.context),
                    null,
                    false
                )

                val colorItemDot: ImageView = viewBinding.itemColorPickerDot
                val colorItemName: TextView = viewBinding.itemColorPickerName
                val colorItemNameDefault: TextView = viewBinding.itemColorPickerNameDefault

                val color = getItem(position)

                val selected = color.hexString == selectedColorHexString

                val colorStateList = ColorStateList.valueOf(color.hexString.toColorInt())
                if (selected) colorItemDot.setImageResource(R.drawable.ic_selected_color_dot)
                else colorItemDot.setImageResource(R.drawable.ic_color_dot)

                colorItemDot.imageTintList = colorStateList

                colorItemName.text = color.name
                colorItemNameDefault.visibleOrGone(defaultCalendarColorHexString == color.hexString)

                viewBinding.root.setOnSingleClickListener {
                    notifyDataSetChanged()
                    callback(color.hexString)
                    dialog.dismiss()
                }

                return viewBinding.root
            }

        }

        val builder = MaterialAlertDialogBuilder(context)
        title?.apply { builder.setTitle(this) }
        builder.setAdapter(adapter, null)
        builder.setNegativeButton(R.string.dialog_button_cancel, null)
        val dialog = builder.create()
        adapter.dialog = dialog
        dialog.show()
    }
}
