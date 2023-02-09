package me.proton.android.calendar.common.utils

import android.content.Context
import android.content.DialogInterface
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.proton.android.calendar.R
import me.proton.android.calendar.common.PROTON_MAIL_DOMAINS
import me.proton.android.calendar.common.PROTON_MAIL_SHORT_DOMAIN
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.getLocaleForFormatting
import me.proton.android.calendar.domain.utils.ProtonUtils
import me.proton.core.presentation.utils.InputValidationResult

object ProtonUtilsImpl : ProtonUtils {

    override fun validateEmail(email: CharSequence): Boolean {
        val regex = InputValidationResult.EMAIL_VALIDATION_PATTERN.toRegex(RegexOption.IGNORE_CASE)
        return regex.matches(email)
    }

    override fun canonicalizeProtonEmails(emails: List<String>, forceCanonicalization: Boolean): Map<String, String> {
        val canonicalEmails = hashMapOf<String, String>()
        emails.forEach {
            val canonicalEmail = canonicalizeProtonEmail(it, forceCanonicalization)
            canonicalEmails[it] = canonicalEmail
        }
        return canonicalEmails
    }

    override fun canonicalizeProtonEmail(email: String, forceCanonicalization: Boolean): String {
        // If user uses a custom domain, we don't apply any canonicalization.
        //  Can be forced with forceCanonicalization when we are certain the address belongs to a Proton user.
        //  forceCanonicalization should be used when comparing email with an attendee email, but not for the organizer.
        //  forceCanonicalization should only be used when comparing email, but not when using the value (ex: DO NOT force when saving it or generating xpm tokens with it).
        if (!forceCanonicalization && !isProtonDomain(email)) return email.lowercase(getLocaleForFormatting())

        val regex = Regex("(?:\\.|\\-|\\_|\\+.*)(?=.*@)")
        return email.replace(regex, "").lowercase(getLocaleForFormatting())
    }

    override fun isProtonDomain(email: String): Boolean {
        return PROTON_MAIL_DOMAINS.any {
            email.endsWith("@$it", true)
        }
    }

    override fun isShortDomainAddress(email: String): Boolean {
        return email.endsWith(PROTON_MAIL_SHORT_DOMAIN)
    }

    override fun Context.displayEventDecryptionErrorDialog(isRecurring: Boolean, callback: DialogInterface.OnClickListener) {
        val confirmationMessage =
            if (isRecurring) R.string.event_decryption_error_dialog_confirmation_recurring
            else R.string.event_decryption_error_dialog_confirmation
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.event_decryption_error_dialog_title)
            .setMessage(R.string.event_decryption_error_dialog_message)
            .setPositiveButton(confirmationMessage, callback)
            .setNegativeButton(R.string.event_decryption_error_dialog_close) { _, _ -> }
            .show()
    }

    override fun Context.displayFreeUserCalendarLimitReached(manageCalendarsCallback: DialogInterface.OnClickListener?) {
        // Display limit reached for free user dialog
        val materialAlertDialogBuilder = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.create_calendar_limit_reached_title)
            .setMessage(R.string.create_calendar_limit_reached_free_description)
            .setNegativeButton(R.string.dialog_button_cancel) { _, _ -> }
        if (manageCalendarsCallback != null) {
            materialAlertDialogBuilder
                .setPositiveButton(R.string.create_calendar_limit_reached_manage, manageCalendarsCallback)
        }
        materialAlertDialogBuilder.show()
    }

    override fun Context.displayPaidUserCalendarLimitReached(manageCalendarsCallback: DialogInterface.OnClickListener?) {
        // Display limit reached for paid user dialog
        val materialAlertDialogBuilder = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.create_calendar_limit_reached_title)
            .setMessage(R.string.create_calendar_limit_reached_paid_description)
            .setNegativeButton(R.string.dialog_button_cancel) { _, _ -> }
        if (manageCalendarsCallback != null) {
            materialAlertDialogBuilder
                .setPositiveButton(R.string.create_calendar_limit_reached_manage, manageCalendarsCallback)
        }
        materialAlertDialogBuilder.show()
    }
}

