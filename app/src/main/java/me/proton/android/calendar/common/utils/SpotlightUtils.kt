package me.proton.android.calendar.common.utils

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.android.synthetic.main.dialog_spotlight.view.*
import kotlinx.android.synthetic.main.dialog_spotlight_v5.view.dialog_spotlight_v5_learn_more_button
import kotlinx.android.synthetic.main.dialog_spotlight_v5.view.dialog_spotlight_v5_description
import kotlinx.android.synthetic.main.dialog_spotlight_v5.view.dialog_spotlight_v5_got_it_button
import kotlinx.android.synthetic.main.dialog_spotlight_v5.view.dialog_spotlight_v5_title
import me.proton.android.calendar.BuildConfig
import me.proton.android.calendar.R
import me.proton.android.calendar.common.FeatureFlag.SPOTLIGHT
import me.proton.android.calendar.common.REBRANDING_UPDATED_PROTON_URL
import me.proton.android.calendar.common.SPOTLIGHT_VERSION_CODES
import me.proton.android.calendar.common.SharedPreferencesKeys
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener

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

    private fun Resources.getRebrandingDialogContent(): Pair<String, String> {
        return Pair(
            this.getString(R.string.spotlight_v5_dialog_rebranding_title),
            this.getString(R.string.spotlight_v5_dialog_rebranding_description)
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
            128 -> {
                // Rebranding
                val monthViewContent = this.resources.getRebrandingDialogContent()
                this.displayV5SpotlightDialog(
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
            .setCancelable(true)
            .setPositiveButton(R.string.spotlight_dialog_confirmation_button) { _, _ ->
                // Nothing to do here
            }
            .setOnDismissListener {
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

    private fun Context.displayV5SpotlightDialog(
        title: String,
        description: String
    ) {
        val materialDialogBuilder = MaterialAlertDialogBuilder(this)
            .setCancelable(true)
            .setOnDismissListener {
                // Set current version name as last spotlight shown
                this.setLastSpotlightShown(BuildConfig.VERSION_CODE)
            }

        val view = LayoutInflater.from(this)
            .inflate(R.layout.dialog_spotlight_v5, null, false)

        view.dialog_spotlight_v5_title.text = title
        view.dialog_spotlight_v5_description.text = description

        var dialog: AlertDialog? = null
        view.dialog_spotlight_v5_got_it_button.setOnSingleClickListener {
            dialog?.dismiss()
        }
        view.dialog_spotlight_v5_learn_more_button.setOnSingleClickListener {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(REBRANDING_UPDATED_PROTON_URL))
            startActivity(browserIntent)
            dialog?.dismiss()
        }

        materialDialogBuilder.setView(view)
        dialog = materialDialogBuilder.show()
    }
}
