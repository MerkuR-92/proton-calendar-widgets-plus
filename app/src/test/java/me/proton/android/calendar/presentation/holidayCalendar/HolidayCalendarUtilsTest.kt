package me.proton.android.calendar.presentation.holidayCalendar

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import kotlinx.serialization.json.JsonObject
import me.proton.android.calendar.data.entity.ManagedHolidayCalendarEntity
import org.junit.jupiter.api.Test

internal class HolidayCalendarUtilsTest {

    @Test
    fun `collapses language variants of one country into a single row`() {
        val calendars = listOf(
            holidayCalendar(country = "Schweiz", countryCode = "ch", languageCode = "de"),
            holidayCalendar(country = "Suisse", countryCode = "ch", languageCode = "fr"),
            holidayCalendar(country = "Svizzera", countryCode = "ch", languageCode = "it"),
            holidayCalendar(country = "France", countryCode = "fr", languageCode = "fr"),
        )

        val result = HolidayCalendarUtils.distinctCountries(calendars, deviceLanguageCode = "en")

        assertThat(result.map { it.countryCode }).containsExactly("fr", "ch")
    }

    @Test
    fun `uses the device language name for the collapsed row`() {
        val calendars = listOf(
            holidayCalendar(country = "Schweiz", countryCode = "ch", languageCode = "de"),
            holidayCalendar(country = "Suisse", countryCode = "ch", languageCode = "fr"),
        )

        val result = HolidayCalendarUtils.distinctCountries(calendars, deviceLanguageCode = "fr")

        assertThat(result.single().country).isEqualTo("Suisse")
    }

    @Test
    fun `falls back to the first name alphabetically without a device language match`() {
        val calendars = listOf(
            holidayCalendar(country = "Suisse", countryCode = "ch", languageCode = "fr"),
            holidayCalendar(country = "Schweiz", countryCode = "ch", languageCode = "de"),
        )

        val result = HolidayCalendarUtils.distinctCountries(calendars, deviceLanguageCode = "en")

        assertThat(result.single().country).isEqualTo("Schweiz")
    }

    @Test
    fun `keeps different countries separate and sorted by name`() {
        val calendars = listOf(
            holidayCalendar(country = "United States", countryCode = "us"),
            holidayCalendar(country = "Germany", countryCode = "de"),
            holidayCalendar(country = "France", countryCode = "fr"),
        )

        val result = HolidayCalendarUtils.distinctCountries(calendars, deviceLanguageCode = "en")

        assertThat(result.map { it.country }).containsExactly("France", "Germany", "United States")
    }

    @Test
    fun `keeps same-named countries with different codes separate`() {
        val calendars = listOf(
            holidayCalendar(country = "Korea", countryCode = "kr"),
            holidayCalendar(country = "Korea", countryCode = "kp"),
        )

        val result = HolidayCalendarUtils.distinctCountries(calendars, deviceLanguageCode = "en")

        assertThat(result).hasSize(2)
    }

    @Test
    fun `languagesForCountry lists each language once`() {
        val calendars = listOf(
            holidayCalendar(countryCode = "ch", language = "Deutsch"),
            holidayCalendar(countryCode = "ch", language = "Français"),
            holidayCalendar(countryCode = "fr", language = "Français"),
        )

        val result = HolidayCalendarUtils.languagesForCountry(calendars, countryCode = "ch")

        assertThat(result).containsExactly("Deutsch", "Français")
    }

    @Test
    fun `defaultCalendarForCountry prefers device language, else first variant`() {
        val calendars = listOf(
            holidayCalendar(calendarId = "ch-de", countryCode = "ch", languageCode = "de"),
            holidayCalendar(calendarId = "ch-fr", countryCode = "ch", languageCode = "fr"),
        )

        assertThat(HolidayCalendarUtils.defaultCalendarForCountry(calendars, countryCode = "ch", deviceLanguageCode = "fr")?.calendarId)
            .isEqualTo("ch-fr")
        assertThat(HolidayCalendarUtils.defaultCalendarForCountry(calendars, countryCode = "ch", deviceLanguageCode = "en")?.calendarId)
            .isEqualTo("ch-de")
    }

    private fun holidayCalendar(
        calendarId: String = "id",
        country: String = "Country",
        countryCode: String,
        languageCode: String = "en",
        language: String = "English",
    ) = ManagedHolidayCalendarEntity(
        calendarId = calendarId,
        country = country,
        countryCode = countryCode,
        languageCode = languageCode,
        language = language,
        timezones = emptyList(),
        passphrase = "",
        sessionKey = JsonObject(emptyMap()),
        hidden = false,
    )
}
