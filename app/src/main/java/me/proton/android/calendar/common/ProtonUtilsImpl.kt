package me.proton.android.calendar.common

import me.proton.android.calendar.domain.utils.ProtonUtils
import me.proton.core.presentation.utils.InputValidationResult
import java.util.*

object ProtonUtilsImpl : ProtonUtils {

    override fun validateEmail(email: CharSequence): Boolean {
        val regex = InputValidationResult.EMAIL_VALIDATION_PATTERN.toRegex(RegexOption.IGNORE_CASE)
        return regex.matches(email)
    }

    override fun canonicalizeProtonEmails(emails: List<String>): Map<String, String> {
        val canonicalEmails = hashMapOf<String, String>()
        emails.forEach {
            val canonicalEmail = canonicalizeProtonEmail(it)
            canonicalEmails[it] = canonicalEmail
        }
        return canonicalEmails
    }

    override fun canonicalizeProtonEmail(email:String): String {
        // If user uses a custom domain, we don't apply any canonicalization
        if (!isProtonDomain(email)) return email

        val regex = Regex("(?:\\.|\\-|\\_|\\+.*)(?=.*@)")
        return email.replace(regex, "").toLowerCase(Locale.getDefault())
    }

    override fun isProtonDomain(email: String): Boolean {
        return PROTON_MAIL_DOMAINS.any {
            email.endsWith("@$it", true)
        }
    }

    override fun isShortDomainAddress(email: String): Boolean {
        return email.endsWith(PROTON_MAIL_SHORT_DOMAIN)
    }

}

