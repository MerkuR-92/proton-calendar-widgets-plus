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
import me.proton.android.calendar.common.CALENDAR_PROVIDER_VERSION_CODE
import me.proton.android.calendar.common.EASY_SWITCH_VERSION_CODE
import me.proton.android.calendar.common.FeatureFlag.IMPORT_ASSISTANT
import me.proton.android.calendar.common.FeatureFlag.IMPORT_ICS
import me.proton.android.calendar.common.FeatureFlag.SHOW_EVENT_SEARCH
import me.proton.android.calendar.common.FeatureFlag.SPOTLIGHT
import me.proton.android.calendar.common.IMPORT_VERSION_CODE
import me.proton.android.calendar.common.MONTH_VIEW_VERSION_CODE
import me.proton.android.calendar.common.REBRANDING_VERSION_CODE
import me.proton.android.calendar.common.SEARCH_VERSION_CODE
import me.proton.android.calendar.common.SPOTLIGHT_VERSION_CODES
import me.proton.android.calendar.common.SharedPreferencesKeys
import me.proton.android.calendar.common.WEEK_VIEW_VERSION_CODE
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

    private fun getMonthViewDialogContent(): Pair<Int, Int> {
        return Pair(
            R.string.spotlight_dialog_month_view_title,
            R.string.spotlight_dialog_month_view_description
        )
    }

    private fun getRebrandingDialogContent(): Pair<Int, Int> {
        return Pair(
            R.string.spotlight_v5_dialog_rebranding_title,
            R.string.spotlight_v5_dialog_rebranding_description
        )
    }

    private fun getAutoAddedInvitesDialogContent(): Pair<Int, Int> {
        return Pair(
            R.string.spotlight_dialog_auto_invites_title,
            R.string.spotlight_dialog_auto_invites_description
        )
    }

    private fun getEasySwitchDialogContent(): Pair<Int, Int> {
        return Pair(
            R.string.import_from_google_title,
            R.string.spotlight_dialog_easy_switch_description
        )
    }

    private fun getWeekViewDialogContent(): Pair<Int, Int> {
        return Pair(
            R.string.spotlight_dialog_week_view_title,
            R.string.spotlight_dialog_week_view_description
        )
    }

    private fun getImportDialogContent(): Pair<Int, Int> {
        return Pair(
            R.string.spotlight_dialog_import_title,
            R.string.spotlight_dialog_import_description
        )
    }

    private fun getSearchDialogContent(): Pair<Int, Int> {
        return Pair(
            R.string.spotlight_dialog_search_title,
            R.string.spotlight_dialog_search_description
        )
    }

    private fun getCalendarProviderDialogContent(): Pair<Int, Int> {
        return Pair(
            R.string.spotlight_dialog_calendar_provider_title,
            R.string.spotlight_dialog_calendar_provider_description
        )
    }

    fun Activity.showLastSpotlightDialog(positiveCallback: ((lastSpotlightVersionCode: Int) -> Unit)? = null) {
        if (!SPOTLIGHT) return

        val lastSpotlightShown = this.getLastSpotlightShown()
        val lastSpotlightVersionCode = SPOTLIGHT_VERSION_CODES.maxOrNull() ?: 0 // Should never be null

        // Return if we have already shown the last spotlight dialog
        if (lastSpotlightShown >= lastSpotlightVersionCode) return

        when (lastSpotlightVersionCode) {
            MONTH_VIEW_VERSION_CODE -> {
                // Month view
                val monthViewContent = getMonthViewDialogContent()
                this.displaySpotlightDialog(
                    monthViewContent.first,
                    monthViewContent.second
                )
            }
            REBRANDING_VERSION_CODE -> {
                // Rebranding
                val rebrandingContent = getRebrandingDialogContent()
                this.displayV5SpotlightDialog(
                    rebrandingContent.first,
                    rebrandingContent.second
                )
            }
            EASY_SWITCH_VERSION_CODE -> {
                // Display auto added invites dialog, followed by easy switch dialog
                val autoAddedInvitesContent = getAutoAddedInvitesDialogContent()
                this.displaySpotlightDialog(
                    autoAddedInvitesContent.first,
                    autoAddedInvitesContent.second,
                    materialPositiveButtonText = R.string.spotlight_dialog_auto_invites_positive_button,
                    customOnDismissCallback = {
                        // Easy switch
                        val easySwitchContent = getEasySwitchDialogContent()
                        val positiveButtonCallback = View.OnClickListener {
                            // Open import from google view
                            (this as MainActivity).showImportGoogleAuthDialog()
                        }
                        this.displaySpotlightDialog(
                            easySwitchContent.first,
                            easySwitchContent.second,
                            customPositiveButtonText = R.string.spotlight_dialog_easy_switch_positive_button,
                            customNegativeButtonText = R.string.spotlight_dialog_easy_switch_negative_button,
                            customPositiveButtonCallback = positiveButtonCallback
                        )
                    }
                )
            }
            WEEK_VIEW_VERSION_CODE -> {
                // Week view
                val weekViewContent = getWeekViewDialogContent()
                this.displaySpotlightDialog(
                    weekViewContent.first,
                    weekViewContent.second,
                    materialPositiveButtonText = R.string.spotlight_v5_dialog_got_it_button
                )
            }
            IMPORT_VERSION_CODE -> {
                // Import
                if (!IMPORT_ICS) return
                val weekViewContent = getImportDialogContent()
                this.displaySpotlightDialog(
                    weekViewContent.first,
                    weekViewContent.second,
                    materialPositiveButtonText = R.string.spotlight_v5_dialog_got_it_button
                )
            }
            CALENDAR_PROVIDER_VERSION_CODE -> {
                // Calendar provider, default view setting, shared calendar write permissions, shared calendar edit setting, new languages
                val content = getCalendarProviderDialogContent()
                this.displaySpotlightDialog(
                    content.first,
                    content.second,
                    materialPositiveButtonText = R.string.spotlight_v5_dialog_got_it_button
                )
            }
            SEARCH_VERSION_CODE -> {
                if (!SHOW_EVENT_SEARCH) return

                val content = getSearchDialogContent()
                this.displaySpotlightDialog(
                    content.first,
                    content.second,
                    materialPositiveButtonText = R.string.spotlight_v5_dialog_got_it_button,
                    customPositiveButtonCallback = {
                        positiveCallback?.invoke(lastSpotlightVersionCode)
                    }
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
        materialPositiveButtonText: Int? = null,
        customPositiveButtonText: Int? = null,
        customNegativeButtonText: Int? = null,
        customPositiveButtonCallback: View.OnClickListener? = null,
        customOnDismissCallback: View.OnClickListener? = null
    ) {
        val materialDialogBuilder = MaterialAlertDialogBuilder(this)
            .setCancelable(true)

        val view = LayoutInflater.from(this)
            .inflate(R.layout.dialog_spotlight, null, false)

        materialDialogBuilder.setOnDismissListener {
            customOnDismissCallback?.onClick(view)
            // Set current version name as last spotlight shown
            this.setLastSpotlightShown(BuildConfig.VERSION_CODE)
        }

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
            materialDialogBuilder.setPositiveButton(materialPositiveButtonText ?: R.string.spotlight_dialog_confirmation_button) { _, _ ->
                // Nothing to do here
                customPositiveButtonCallback?.onClick(view)
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
