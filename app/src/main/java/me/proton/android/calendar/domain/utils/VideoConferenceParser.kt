package me.proton.android.calendar.domain.utils

object VideoConferenceParser {

    private val googleMeet =
        Regex("(https://)?meet\\.google\\.com/([a-z]{3}-[a-z]{4}-[a-z]{3})")

    private val protonMeet =
        Regex("(https://)?meet(?:\\.\\w+)?\\..+/join/id-(\\w+)#pwd-([^\\s#]+)")

    private val slack =
        Regex("\\b(https://)?app\\.slack\\.com/huddle/([^>\\s,]+)")

    private val teams =
        Regex("\\b(https://)?teams\\.live\\.com/meet/([^?\\s,]+)\\?p=([^>\\s,]+)")

    private val zoom = Regex(
        "(https://)?(?:[a-zA-Z0-9.-]+\\.)?zoom\\.us/(?:my|j)/([a-zA-Z0-9]+)(?:\\?pwd=([a-zA-Z0-9]+(?:\\.[a-zA-Z0-9]+)?))?"
    )

    private val allRegexes = listOf(
        protonMeet,
        zoom,
        googleMeet,
        slack,
        teams,
    )

    fun hasValidVideoConference(description: String?, location: String?): Boolean =
        listOfNotNull(description, location)
            .any { containsVideoConferenceUrl(it) }

    private fun containsVideoConferenceUrl(text: String): Boolean =
        allRegexes.any { regex -> findUrlWithOptionalScheme(text, regex) != null }

    private fun findUrlWithOptionalScheme(text: String, regex: Regex): String? {
        val match = regex.find(text) ?: return null
        val schemePart = match.groupValues.getOrNull(1).orEmpty()
        val value = match.value
        return if (schemePart.isNotEmpty()) value else "https://$value"
    }
}
