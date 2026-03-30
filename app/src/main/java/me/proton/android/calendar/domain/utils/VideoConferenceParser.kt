package me.proton.android.calendar.domain.utils

object VideoConferenceParser {

    // schemes optional when detecting

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

    fun containsVideoConferenceUrl(text: String?): Boolean =
        text != null && allRegexes.any { it.containsMatchIn(text) }

    fun extractProtonMeetUrl(text: String?): String? {
        if (text == null) return null
        val match = protonMeet.find(text) ?: return null
        val matched = match.value
        return if (matched.startsWith("https://")) matched else "https://$matched"
    }
}
