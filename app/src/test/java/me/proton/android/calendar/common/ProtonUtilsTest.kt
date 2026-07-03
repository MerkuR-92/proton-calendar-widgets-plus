package me.proton.android.calendar.common

import biweekly.parameter.ParticipationStatus
import biweekly.property.Attendee
import kotlinx.serialization.json.JsonObject
import me.proton.android.calendar.common.utils.ProtonUtilsImpl
import me.proton.android.calendar.data.entity.ManagedHolidayCalendarEntity
import me.proton.core.contact.domain.entity.ContactEmail
import me.proton.core.contact.domain.entity.ContactEmailId
import me.proton.core.contact.domain.entity.ContactId
import me.proton.core.domain.entity.UserId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ProtonUtilsImplTest {

    private val userId = UserId("test-user-id")
    private val attendeeList = generateAttendeeList()

    @Test
    fun `matchAttendeesWithContacts - no proton or device contacts`() {
        // Given
        val deviceContacts = emptyList<Attendee>()
        val protonContacts = emptyList<ContactEmail>()

        // When
        val result = ProtonUtilsImpl.matchAttendeesWithContacts(attendeeList, deviceContacts, protonContacts)

        // Then
        assertEquals(attendeeList, result)
    }

    @Test
    fun `matchAttendeesWithContacts - proton contact with name`() {
        // Given
        val protonContactAttendee1 = ContactEmail(
            userId,
            ContactEmailId("1"),
            "Attendee 1",
            "attendee1@proton.me",
            defaults = 0,
            1,
            contactId = ContactId("contact_1"),
            "contact_external_pinned_key@email.com",
            labelIds = emptyList(),
            isProton = null,
            lastUsedTime = 0
        )


        val deviceContacts = emptyList<Attendee>()
        val protonContacts = listOf(protonContactAttendee1)

        // When
        val result = ProtonUtilsImpl.matchAttendeesWithContacts(attendeeList, deviceContacts, protonContacts)

        // Then
        assertEquals(result[0].commonName, "Attendee 1")
        assertEquals(result[0].email, attendeeList[0].email)
        assertEquals(result[0].participationStatus, attendeeList[0].participationStatus)
        assertEquals(result[1], attendeeList[1])
    }

    @Test
    fun `matchAttendeesWithContacts - device contact with name`() {
        // Given
        val deviceContactAttendee = Attendee(
            "Attendee 1",
            "attendee1@proton.me"
        )

        val deviceContacts = listOf(deviceContactAttendee)
        val protonContacts = emptyList<ContactEmail>()

        val result = ProtonUtilsImpl.matchAttendeesWithContacts(attendeeList, deviceContacts, protonContacts)

        assertEquals(result[0].commonName, "Attendee 1")
        assertEquals(result[0].email, attendeeList[0].email)
        assertEquals(result[0].participationStatus, attendeeList[0].participationStatus)
        assertEquals(result[1], attendeeList[1])
    }

    @Test
    fun `matchAttendeesWithContacts - no matching contact`() {
        val attendees = listOf(Attendee("attendee1", "attendee1@proton.me"))
        val deviceContacts = emptyList<Attendee>()
        val protonContacts = emptyList<ContactEmail>()

        val result = ProtonUtilsImpl.matchAttendeesWithContacts(attendees, deviceContacts, protonContacts)

        assertEquals("attendee1", result[0].commonName)
        assertEquals("attendee1@proton.me", result[0].email)
    }

    @Test
    fun `getMatchingDefaultHolidayCalendar - without a locale match, picks the first calendar in the list, not the alphabetical one`() {
        val zurich = "Europe/Zurich"
        val calendars = listOf(
            holidayCalendar(calendarId = "ch-fr", countryCode = "ch", languageCode = "fr", language = "Français", timezones = listOf(zurich)),
            holidayCalendar(calendarId = "ch-de", countryCode = "ch", languageCode = "de", language = "Deutsch", timezones = listOf(zurich)),
        )

        val result = ProtonUtilsImpl.getMatchingDefaultHolidayCalendar(
            holidayCalendars = calendars,
            primaryTimeZoneId = zurich,
            defaultLanguageCode = "en",
            defaultCountryCode = ""
        )

        assertEquals("ch-fr", result?.calendarId)
    }

    @Test
    fun `getMatchingDefaultHolidayCalendar - several countries and no locale match returns no suggestion`() {
        val sharedZone = "Europe/Brussels"
        val calendars = listOf(
            holidayCalendar(calendarId = "fr", countryCode = "fr", languageCode = "fr", language = "Français", timezones = listOf(sharedZone)),
            holidayCalendar(calendarId = "de", countryCode = "de", languageCode = "de", language = "Deutsch", timezones = listOf(sharedZone)),
        )

        val result = ProtonUtilsImpl.getMatchingDefaultHolidayCalendar(
            holidayCalendars = calendars,
            primaryTimeZoneId = sharedZone,
            defaultLanguageCode = "en",
            defaultCountryCode = ""
        )

        assertNull(result)
    }

    private fun holidayCalendar(
        calendarId: String,
        countryCode: String,
        languageCode: String,
        language: String,
        timezones: List<String>,
    ) = ManagedHolidayCalendarEntity(
        calendarId = calendarId,
        country = "Country",
        countryCode = countryCode,
        languageCode = languageCode,
        language = language,
        timezones = timezones,
        passphrase = "",
        sessionKey = JsonObject(emptyMap()),
        hidden = false,
    )

    private fun generateAttendeeList(): List<Attendee> {
        val attendee1 = Attendee(
            "attendee1",
            "attendee1@proton.me",
        )
        attendee1.participationStatus = ParticipationStatus.ACCEPTED

        val attendee2 = Attendee(
            "attendee2",
            "attendee2@proton.me",
        )
        attendee1.participationStatus = ParticipationStatus.TENTATIVE

        return listOf(attendee1, attendee2)
    }
}