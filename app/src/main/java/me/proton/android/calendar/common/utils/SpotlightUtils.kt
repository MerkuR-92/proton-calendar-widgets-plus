package me.proton.android.calendar.common.utils

import android.content.Context
import android.content.res.Resources
import android.view.LayoutInflater
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.android.synthetic.main.dialog_spotlight.view.*
import me.proton.android.calendar.BuildConfig
import me.proton.android.calendar.R
import me.proton.android.calendar.common.FeatureFlag.SPOTLIGHT
import me.proton.android.calendar.common.SPOTLIGHT_VERSION_CODES
import me.proton.android.calendar.common.SharedPreferencesKeys

object SpotlightUtils {

    private fun Context.getLastSpotlightShown(): Int {
        return PreferenceManager.getDefaultSharedPreferences(this).getInt(SharedPreferencesKeys.LAST_SPOTLIGHT_SHOWN, 0)
    }

    private fun Context.setLastSpotlightShown(versionCode: Int) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val editor = sharedPreferences.edit()
        editor.putInt(SharedPreferencesKeys.LAST_SPOTLIGHT_SHOWN, versionCode)
        editor.apply()
    }

    private fun Resources.getMonthViewDialogContent(): Pair<String, String> {
        return Pair(
            this.getString(R.string.spotlight_dialog_month_view_title),
            this.getString(R.string.spotlight_dialog_month_view_description)
        )
    }

    fun Context.showLastSpotlightDialog() {
        if (!SPOTLIGHT) return

        val lastSpotlightShown = this.getLastSpotlightShown()
        val lastSpotlightVersionCode = SPOTLIGHT_VERSION_CODES.maxOrNull() ?: 0 // Should never be null

        // Return if we have already shown the last spotlight dialog
        if (lastSpotlightShown >= lastSpotlightVersionCode) return

        when (lastSpotlightVersionCode) {
            112 -> {
                // Month view
                val monthViewContent = this.resources.getMonthViewDialogContent()
                this.displaySpotlightDialog(
                    monthViewContent.first,
                    monthViewContent.second
                )
            }
            else -> {
                // Do nothing if we don't have any dialog to show for that version code
            }
        }
    }

    private fun Context.displaySpotlightDialog(
        title: String,
        description: String
    ) {
        val materialDialogBuilder = MaterialAlertDialogBuilder(this)
            .setCancelable(false)
            .setPositiveButton(R.string.spotlight_dialog_month_confirmation_button) { _, _ ->
                // Set current version name as last spotlight shown
                this.setLastSpotlightShown(BuildConfig.VERSION_CODE)
            }

        val view = LayoutInflater.from(this)
            .inflate(R.layout.dialog_spotlight, null, false)

        view.dialog_spotlight_title.text = title
        view.dialog_spotlight_description.text = description

        materialDialogBuilder.setView(view)
        materialDialogBuilder.show()
    }
}
