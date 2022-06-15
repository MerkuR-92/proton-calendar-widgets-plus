package me.proton.android.calendar.common.utils

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.android.synthetic.main.dialog_spotlight.view.dialog_spotlight_custom_negative_button
import kotlinx.android.synthetic.main.dialog_spotlight.view.dialog_spotlight_custom_positive_button
import kotlinx.android.synthetic.main.dialog_spotlight.view.dialog_spotlight_description
import kotlinx.android.synthetic.main.dialog_spotlight.view.dialog_spotlight_title
import kotlinx.android.synthetic.main.dialog_spotlight_v5.view.dialog_spotlight_v5_description
import kotlinx.android.synthetic.main.dialog_spotlight_v5.view.dialog_spotlight_v5_got_it_button
import kotlinx.android.synthetic.main.dialog_spotlight_v5.view.dialog_spotlight_v5_title
import me.proton.android.calendar.BuildConfig
import me.proton.android.calendar.R
import me.proton.android.calendar.common.FeatureFlag.SPOTLIGHT
import me.proton.android.calendar.common.SPOTLIGHT_VERSION_CODES
import me.proton.android.calendar.common.SharedPreferencesKeys
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.presentation.main.MainActivity

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

    private fun Resources.getMonthViewDialogContent(): Pair<Int, Int> {
        return Pair(
            R.string.spotlight_dialog_month_view_title,
            R.string.spotlight_dialog_month_view_description
        )
    }

    private fun Resources.getRebrandingDialogContent(): Pair<Int, Int> {
        return Pair(
            R.string.spotlight_v5_dialog_rebranding_title,
            R.string.spotlight_v5_dialog_rebranding_description
        )
    }

    private fun Resources.getEasySwitchDialogContent(): Pair<Int, Int> {
        return Pair(
            R.string.import_from_google_title,
            R.string.spotlight_dialog_easy_switch_description
        )
    }

    fun Activity.showLastSpotlightDialog() {
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
            145 -> {
                // Rebranding
                val rebrandingContent = this.resources.getRebrandingDialogContent()
                this.displayV5SpotlightDialog(
                    rebrandingContent.first,
                    rebrandingContent.second
                )
            }
            146 -> {
                // Easy switch
                val easySwitchContent = this.resources.getMonthViewDialogContent()
                val positiveButtonCallback = View.OnClickListener {
                    // Open import from google view
                    (this as MainActivity).showImportGoogleAuthDialog()
                }
                this.displaySpotlightDialog(
                    easySwitchContent.first,
                    easySwitchContent.second,
                    R.string.spotlight_dialog_easy_switch_positive_button,
                    R.string.spotlight_dialog_easy_switch_negative_button,
                    positiveButtonCallback
                )
            }
            else -> {
                // Do nothing if we don't have any dialog to show for that version code
            }
        }
    }

    private fun Context.displaySpotlightDialog(
        title: Int,
        description: Int,
        customPositiveButtonText: Int? = null,
        customNegativeButtonText: Int? = null,
        customPositiveButtonCallback: View.OnClickListener? = null
    ) {
        val materialDialogBuilder = MaterialAlertDialogBuilder(this)
            .setCancelable(true)
            .setOnDismissListener {
                // Set current version name as last spotlight shown
                this.setLastSpotlightShown(BuildConfig.VERSION_CODE)
            }

        val view = LayoutInflater.from(this)
            .inflate(R.layout.dialog_spotlight, null, false)

        // Support link in text with getText
        view.dialog_spotlight_title.text = getText(title)
        view.dialog_spotlight_description.text = getText(description)
        // Support click on link in text
        view.dialog_spotlight_description.movementMethod = LinkMovementMethod.getInstance()

        var dialog: AlertDialog? = null

        view.dialog_spotlight_custom_positive_button.visibleOrGone(customPositiveButtonText != null)
        if (customPositiveButtonText != null) {
            // Use custom positive button if text is provided
            view.dialog_spotlight_custom_positive_button.text = getText(customPositiveButtonText)
            view.dialog_spotlight_custom_positive_button.setOnSingleClickListener {
                dialog?.dismiss()
                customPositiveButtonCallback?.onClick(it)
            }
        } else {
            materialDialogBuilder.setPositiveButton(R.string.spotlight_dialog_confirmation_button) { _, _ ->
                // Nothing to do here
            }
        }

        view.dialog_spotlight_custom_negative_button.visibleOrGone(customNegativeButtonText != null)
        if (customNegativeButtonText != null) {
            // Use custom negative button if text is provided
            view.dialog_spotlight_custom_negative_button.text = getText(customNegativeButtonText)
            view.dialog_spotlight_custom_negative_button.setOnSingleClickListener {
                dialog?.dismiss()
            }
        }

        materialDialogBuilder.setView(view)
        dialog = materialDialogBuilder.show()
    }

    private fun Context.displayV5SpotlightDialog(
        title: Int,
        description: Int
    ) {
        val materialDialogBuilder = MaterialAlertDialogBuilder(this)
            .setCancelable(true)
            .setOnDismissListener {
                // Set current version name as last spotlight shown
                this.setLastSpotlightShown(BuildConfig.VERSION_CODE)
            }

        val view = LayoutInflater.from(this)
            .inflate(R.layout.dialog_spotlight_v5, null, false)

        // Support link in text with getText
        view.dialog_spotlight_v5_title.text = getString(title)
        view.dialog_spotlight_v5_description.text = getText(description)
        // Support click on link in text
        view.dialog_spotlight_v5_description.movementMethod = LinkMovementMethod.getInstance()

        var dialog: AlertDialog? = null
        view.dialog_spotlight_v5_got_it_button.setOnSingleClickListener {
            dialog?.dismiss()
        }

        materialDialogBuilder.setView(view)
        dialog = materialDialogBuilder.show()
    }
}
