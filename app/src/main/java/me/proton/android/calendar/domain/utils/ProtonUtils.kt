package me.proton.android.calendar.domain.utils

interface ProtonUtils {
    fun validateEmail(email: CharSequence): Boolean
    fun canonicalizeProtonEmails(emails: List<String>): Map<String, String>
    fun canonicalizeProtonEmail(email: String): String
    fun isProtonDomain(email: String): Boolean
}
