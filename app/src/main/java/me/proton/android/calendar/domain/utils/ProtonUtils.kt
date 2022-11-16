package me.proton.android.calendar.domain.utils

import android.content.Context
import android.content.DialogInterface

interface ProtonUtils {
    fun validateEmail(email: CharSequence): Boolean
    fun canonicalizeProtonEmails(emails: List<String>, forceCanonicalization: Boolean = false): Map<String, String>
    fun canonicalizeProtonEmail(email: String, forceCanonicalization: Boolean = false): String
    fun isProtonDomain(email: String): Boolean
    fun isShortDomainAddress(email: String): Boolean
    fun Context.displayEventDecryptionErrorDialog(isRecurring: Boolean, callback: DialogInterface.OnClickListener)
}
