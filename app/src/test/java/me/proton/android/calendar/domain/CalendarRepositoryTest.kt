package me.proton.android.calendar.domain

import android.util.Log
import assertk.assertThat
import assertk.assertions.isEqualTo
import biweekly.property.RecurrenceId
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import me.proton.android.calendar.CalendarWidgetRefresher
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.toDate
import me.proton.android.calendar.common.logger.TestsLogger
import me.proton.android.calendar.data.CalendarsRepositoryImpl
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.EventsByUidApiResponse
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.usecase.FetchEventsUseCase
import me.proton.android.calendar.domain.usecase.TransformEventUseCase
import me.proton.android.calendar.domain.usecase.UpdateAlarmsUseCase
import me.proton.core.domain.entity.UserId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*

@ExperimentalCoroutinesApi
@FlowPreview
@ExperimentalSerializationApi
internal class CalendarRepositoryTest {

    private val calendarsApiMock: CalendarsApi = mockk()

    private lateinit var appDatabaseMock: AppDatabase

    private val transformEventUseCaseMock: TransformEventUseCase = mockk()
    private val fetchEventsUseCaseMock: FetchEventsUseCase = mockk()
    private val updateAlarmsUseCaseMock: UpdateAlarmsUseCase = mockk()
    private val calendarWidgetRefresherMock: CalendarWidgetRefresher = mockk()

    private val testsLogger = TestsLogger
    private val json = Json { this.ignoreUnknownKeys = true }

    private val userId = UserId("IXFh2TE4LI11sd0GYf94r7fddHNMdZvicfoWMACCjPTS-oNjpBjeclhKlIs6N48-GB5w-zM6uqX_9HFgEnzhYQ==")

    @BeforeEach
    fun `before each`() {
        clearAllMocks()
        mockkStatic(Log::class)
        coEvery { Log.isLoggable(any(), any()) } returns true
        appDatabaseMock = mockk()

        coEvery { appDatabaseMock.calendarsDao().flowCalendars() } returns flowOf(listOf())
        coEvery { appDatabaseMock.eventsDao().selectSkeletonEventsFlow() } returns flowOf(listOf())
    }

    @Test
    fun `isStandaloneSingleEdit test with standalone single edit`() {
        runBlocking {
            coEvery { calendarsApiMock.getEventsByUid(userId, any(), 0, 100) } returns ApiResponse.Success(
                mockEventsByUidStandaloneSingleEdit()
            )

            val isStandaloneSingleEdit = getCalendarRepository().isStandaloneSingleEdit(
                userId,
                "eventUId", // We do not care about eventUid because we mock getEventsByUid
                RecurrenceId(
                    LocalDate.of(2021, 9, 24).toDate(TimeZone.getDefault().id),
                    false
                ),
                TimeZone.getDefault().id,
                0
            )

            assertThat(isStandaloneSingleEdit).isEqualTo(true)
        }
    }

    @Test
    fun `isStandaloneSingleEdit test recurring with two single edits and one ex date`() {
        runBlocking {
            coEvery { calendarsApiMock.getEventsByUid(userId, any(), 0, 100) } returns ApiResponse.Success(
                mockEventsByUidRecurringWithSingleEditsAndExDate()
            )

            val isStandaloneSingleEdit = getCalendarRepository().isStandaloneSingleEdit(
                userId,
                "eventUId", // We do not care about eventUid because we mock getEventsByUid
                RecurrenceId(
                    LocalDate.of(2021, 9, 24).toDate(TimeZone.getDefault().id),
                    false
                ),
                TimeZone.getDefault().id,
                0
            )

            assertThat(isStandaloneSingleEdit).isEqualTo(false)
        }
    }

    @Test
    fun `isStandaloneSingleEdit test recurring with two occurrences and one single edit`() {
        runBlocking {
            coEvery { calendarsApiMock.getEventsByUid(userId, any(), 0, 100) } returns ApiResponse.Success(
                mockEventsByUidRecurringWithSingleEdit()
            )

            val isStandaloneSingleEdit = getCalendarRepository().isStandaloneSingleEdit(
                userId,
                "eventUId", // We do not care about eventUid because we mock getEventsByUid
                RecurrenceId(
                    LocalDate.of(2021, 9, 24).toDate(TimeZone.getDefault().id),
                    false
                ),
                TimeZone.getDefault().id,
                0
            )

            assertThat(isStandaloneSingleEdit).isEqualTo(false)
        }
    }

    @Test
    fun `isStandaloneSingleEdit test with orphan single edit`() {
        runBlocking {
            coEvery { calendarsApiMock.getEventsByUid(userId, any(), 0, 100) } returns ApiResponse.Success(
                mockEventsByUidOrphanSingleEdit()
            )

            val isStandaloneSingleEdit = getCalendarRepository().isStandaloneSingleEdit(
                userId,
                "eventUId", // We do not care about eventUid because we mock getEventsByUid
                RecurrenceId(
                    LocalDate.of(2021, 9, 24).toDate(TimeZone.getDefault().id),
                    false
                ),
                TimeZone.getDefault().id,
                0
            )

            assertThat(isStandaloneSingleEdit).isEqualTo(false)
        }
    }

    @Test
    fun `isStandaloneSingleEdit test with only single edits in chain`() {
        runBlocking {
            coEvery { calendarsApiMock.getEventsByUid(userId, any(), 0, 100) } returns ApiResponse.Success(
                mockEventsByUidOnlySingleEdits()
            )

            val isStandaloneSingleEdit = getCalendarRepository().isStandaloneSingleEdit(
                userId,
                "eventUId", // We do not care about eventUid because we mock getEventsByUid
                RecurrenceId(
                    LocalDate.of(2021, 9, 24).toDate(TimeZone.getDefault().id),
                    false
                ),
                TimeZone.getDefault().id,
                0
            )

            assertThat(isStandaloneSingleEdit).isEqualTo(false)
        }
    }

    @Test
    fun `isOrphanSingleEdit test with orphan single edit`() {
        runBlocking {
            coEvery { calendarsApiMock.getEventsByUid(userId, any(), 0, 100) } returns ApiResponse.Success(
                mockEventsByUidOrphanSingleEdit()
            )

            val isOrphanSingleEdit = getCalendarRepository().isOrphanSingleEdit(
                userId,
                "eventUId" // We do not care about eventUid because we mock getEventsByUid
            )

            assertThat(isOrphanSingleEdit).isEqualTo(true)
        }
    }

    @Test
    fun `isOrphanSingleEdit test recurring with two occurrences and one single edit`() {
        runBlocking {
            coEvery { calendarsApiMock.getEventsByUid(userId, any(), 0, 100) } returns ApiResponse.Success(
                mockEventsByUidRecurringWithSingleEdit()
            )

            val isOrphanSingleEdit = getCalendarRepository().isOrphanSingleEdit(
                userId,
                "eventUid" // We do not care about eventUid because we mock getEventsByUid
            )

            assertThat(isOrphanSingleEdit).isEqualTo(false)
        }
    }

    @Test
    fun `isOrphanSingleEdit test recurring with only single edits`() {
        runBlocking {
            coEvery { calendarsApiMock.getEventsByUid(userId, any(), 0, 100) } returns ApiResponse.Success(
                mockEventsByUidOnlySingleEdits()
            )

            val isOrphanSingleEdit = getCalendarRepository().isOrphanSingleEdit(
                userId,
                "eventUid" // We do not care about eventUid because we mock getEventsByUid
            )

            assertThat(isOrphanSingleEdit).isEqualTo(false)
        }
    }

    @Test
    fun `isOrphanSingleEdit test recurring with two single edits and one ex date`() {
        runBlocking {
            coEvery { calendarsApiMock.getEventsByUid(userId, any(), 0, 100) } returns ApiResponse.Success(
                mockEventsByUidRecurringWithSingleEditsAndExDate()
            )

            val isOrphanSingleEdit = getCalendarRepository().isOrphanSingleEdit(
                userId,
                "eventUid" // We do not care about eventUid because we mock getEventsByUid
            )

            assertThat(isOrphanSingleEdit).isEqualTo(false)
        }
    }

    @Test
    fun `isOrphanSingleEdit test recurring with standalone single edit`() {
        runBlocking {
            coEvery { calendarsApiMock.getEventsByUid(userId, any(), 0, 100) } returns ApiResponse.Success(
                mockEventsByUidStandaloneSingleEdit()
            )

            val isOrphanSingleEdit = getCalendarRepository().isOrphanSingleEdit(
                userId,
                "eventUid" // We do not care about eventUid because we mock getEventsByUid
            )

            assertThat(isOrphanSingleEdit).isEqualTo(false)
        }
    }

    private fun getCalendarRepository(): CalendarsRepository {
        return CalendarsRepositoryImpl(
            appDatabaseMock,
            transformEventUseCaseMock,
            testsLogger,
            fetchEventsUseCaseMock,
            updateAlarmsUseCaseMock,
            calendarsApiMock,
            json,
            calendarWidgetRefresherMock
        )
    }

    private fun mockEventsByUidOrphanSingleEdit(): EventsByUidApiResponse {
        // Orphan single edit
        return EventsByUidApiResponse(
            events = listOf(
                EventEntity( // Parent
                    id = "eventIdSingleEdit",
                    calendarId = "calendarId",
                    sharedEventId = "sharedEventId",
                    calendarKeyPacket = null,
                    createTime = 0L,
                    modifyTime = 0L,
                    permissions = 1,
                    sharedKeyPacket = "sharedKeyPacket",
                    sharedEvents = listOf(
                        Json.decodeFromString<JsonElement>(
                            "{\"Type\":2,\"Data\":\"BEGIN:VCALENDAR\\r\\nVERSION:2.0\\r\\nBEGIN:VEVENT\\r\\nUID:4trl5eflr8jo9vtotb0gmtsf4c@google.com\\r\\nDTSTAMP:20210923T074702Z\\r\\nDTSTART;VALUE=DATE:20210924\\r\\nRECURRENCE-ID;VALUE=DATE:20210924\\r\\nORGANIZER;CN=garciabenjamin30@gmail.com:mailto:garciabenjamin30@gmail.com\\r\\nSEQUENCE:0\\r\\nEND:VEVENT\\r\\nEND:VCALENDAR\",\"Signature\":null,\"Author\":\"benjaminlovesdebugging@pm.me\"}"
                        )
                    ),
                    calendarEvents = emptyList(),
                    personalEvents = emptyList(),
                    attendeesEvents = emptyList(),
                    attendees = emptyList(),
                    isProtonProtonInvite = 0
                )
            )
        )
    }

    private fun mockEventsByUidRecurringWithSingleEdit(): EventsByUidApiResponse {
        // Recurring with two occurrences and one single edit
        return EventsByUidApiResponse(
            events = listOf(
                EventEntity( // Parent
                    id = "eventIdParent",
                    calendarId = "calendarId",
                    sharedEventId = "sharedEventId",
                    calendarKeyPacket = null,
                    createTime = 0L,
                    modifyTime = 0L,
                    permissions = 1,
                    sharedKeyPacket = "sharedKeyPacket",
                    sharedEvents = listOf(
                        Json.decodeFromString<JsonElement>(
                            "{\"Type\":2,\"Data\":\"BEGIN:VCALENDAR\\r\\nVERSION:2.0\\r\\nBEGIN:VEVENT\\r\\nUID:4rus20gb7bkamq3l8q1gmm8n8g@google.com\\r\\nDTSTAMP:20210923T074445Z\\r\\nDTSTART;VALUE=DATE:20210923\\r\\nRRULE:FREQ=DAILY;COUNT=3\\r\\nORGANIZER;CN=garciabenjamin30@gmail.com:mailto:garciabenjamin30@gmail.com\\r\\nSEQUENCE:0\\r\\nEND:VEVENT\\r\\nEND:VCALENDAR\",\"Signature\":null,\"Author\":\"benjaminlovesdebugging@pm.me\"}"
                        )
                    ),
                    calendarEvents = emptyList(),
                    personalEvents = emptyList(),
                    attendeesEvents = emptyList(),
                    attendees = emptyList(),
                    isProtonProtonInvite = 0
                ),
                EventEntity( // Single edit
                    id = "eventIdSingleEdit",
                    calendarId = "calendarId",
                    sharedEventId = "sharedEventId",
                    calendarKeyPacket = null,
                    createTime = 0L,
                    modifyTime = 0L,
                    permissions = 3,
                    sharedKeyPacket = "sharedKeyPacket",
                    sharedEvents = listOf(
                        Json.decodeFromString<JsonElement>(
                            "{\"Type\":0,\"Data\":\"BEGIN:VCALENDAR\\r\\nPRODID:-//Google Inc//Google Calendar 70.9054//EN\\r\\nVERSION:2.0\\r\\nBEGIN:VEVENT\\r\\nDTSTART;VALUE=DATE:20210924\\r\\nDTEND;VALUE=DATE:20210925\\r\\nDTSTAMP:20210923T080645Z\\r\\nORGANIZER;CN=garciabenjamin30@gmail.com:mailto:garciabenjamin30@gmail.com\\r\\nUID:4rus20gb7bkamq3l8q1gmm8n8g@google.com\\r\\nRECURRENCE-ID;VALUE=DATE:20210924\\r\\nSEQUENCE:0\\r\\nEND:VEVENT\\r\\nEND:VCALENDAR\",\"Signature\":null,\"Author\":\"garciabenjamin30@gmail.com\"}"
                        )
                    ),
                    calendarEvents = emptyList(),
                    personalEvents = emptyList(),
                    attendeesEvents = emptyList(),
                    attendees = emptyList(),
                    isProtonProtonInvite = 0
                )
            )
        )
    }

    private fun mockEventsByUidOnlySingleEdits(): EventsByUidApiResponse {
        // Recurring with only single edit (3)
        return EventsByUidApiResponse(
            events = listOf(
                EventEntity( // Parent
                    id = "eventIdParent",
                    calendarId = "calendarId",
                    sharedEventId = "sharedEventId",
                    calendarKeyPacket = null,
                    createTime = 0L,
                    modifyTime = 0L,
                    permissions = 1,
                    sharedKeyPacket = "sharedKeyPacket",
                    sharedEvents = listOf(
                        Json.decodeFromString<JsonElement>(
                            "{\"Type\":2,\"Data\":\"BEGIN:VCALENDAR\\r\\nVERSION:2.0\\r\\nBEGIN:VEVENT\\r\\nUID:4rus20gb7bkamq3l8q1gmm8n8g@google.com\\r\\nDTSTAMP:20210923T074445Z\\r\\nDTSTART;VALUE=DATE:20210923\\r\\nRRULE:FREQ=DAILY;COUNT=3\\r\\nORGANIZER;CN=garciabenjamin30@gmail.com:mailto:garciabenjamin30@gmail.com\\r\\nSEQUENCE:0\\r\\nEND:VEVENT\\r\\nEND:VCALENDAR\",\"Signature\":null,\"Author\":\"benjaminlovesdebugging@pm.me\"}"
                        )
                    ),
                    calendarEvents = emptyList(),
                    personalEvents = emptyList(),
                    attendeesEvents = emptyList(),
                    attendees = emptyList(),
                    isProtonProtonInvite = 0
                ),
                EventEntity( // Single edit 1
                    id = "eventIdSingleEdit1",
                    calendarId = "calendarId",
                    sharedEventId = "sharedEventId",
                    calendarKeyPacket = null,
                    createTime = 0L,
                    modifyTime = 0L,
                    permissions = 3,
                    sharedKeyPacket = "sharedKeyPacket",
                    sharedEvents = listOf(
                        Json.decodeFromString<JsonElement>(
                            "{\"Type\":0,\"Data\":\"BEGIN:VCALENDAR\\r\\nPRODID:-//Google Inc//Google Calendar 70.9054//EN\\r\\nVERSION:2.0\\r\\nBEGIN:VEVENT\\r\\nDTSTART;VALUE=DATE:20210923\\r\\nDTEND;VALUE=DATE:20210924\\r\\nDTSTAMP:20210923T090014Z\\r\\nORGANIZER;CN=garciabenjamin30@gmail.com:mailto:garciabenjamin30@gmail.com\\r\\nUID:4rus20gb7bkamq3l8q1gmm8n8g@google.com\\r\\nRECURRENCE-ID;VALUE=DATE:20210923\\r\\nSEQUENCE:0\\r\\nEND:VEVENT\\r\\nEND:VCALENDAR\",\"Signature\":null,\"Author\":\"garciabenjamin30@gmail.com\"}"
                        )
                    ),
                    calendarEvents = emptyList(),
                    personalEvents = emptyList(),
                    attendeesEvents = emptyList(),
                    attendees = emptyList(),
                    isProtonProtonInvite = 0
                ),
                EventEntity( // Single edit 2
                    id = "eventIdSingleEdit2",
                    calendarId = "calendarId",
                    sharedEventId = "sharedEventId",
                    calendarKeyPacket = null,
                    createTime = 0L,
                    modifyTime = 0L,
                    permissions = 3,
                    sharedKeyPacket = "sharedKeyPacket",
                    sharedEvents = listOf(
                        Json.decodeFromString<JsonElement>(
                            "{\"Type\":0,\"Data\":\"BEGIN:VCALENDAR\\r\\nPRODID:-//Google Inc//Google Calendar 70.9054//EN\\r\\nVERSION:2.0\\r\\nBEGIN:VEVENT\\r\\nDTSTART;VALUE=DATE:20210924\\r\\nDTEND;VALUE=DATE:20210925\\r\\nDTSTAMP:20210923T080645Z\\r\\nORGANIZER;CN=garciabenjamin30@gmail.com:mailto:garciabenjamin30@gmail.com\\r\\nUID:4rus20gb7bkamq3l8q1gmm8n8g@google.com\\r\\nRECURRENCE-ID;VALUE=DATE:20210924\\r\\nSEQUENCE:0\\r\\nEND:VEVENT\\r\\nEND:VCALENDAR\",\"Signature\":null,\"Author\":\"garciabenjamin30@gmail.com\"}"
                        )
                    ),
                    calendarEvents = emptyList(),
                    personalEvents = emptyList(),
                    attendeesEvents = emptyList(),
                    attendees = emptyList(),
                    isProtonProtonInvite = 0
                ),
                EventEntity( // Single edit 3
                    id = "eventIdSingleEdit3",
                    calendarId = "calendarId",
                    sharedEventId = "sharedEventId",
                    calendarKeyPacket = null,
                    createTime = 0L,
                    modifyTime = 0L,
                    permissions = 3,
                    sharedKeyPacket = "sharedKeyPacket",
                    sharedEvents = listOf(
                        Json.decodeFromString<JsonElement>(
                            "{\"Type\":0,\"Data\":\"BEGIN:VCALENDAR\\r\\nPRODID:-//Google Inc//Google Calendar 70.9054//EN\\r\\nVERSION:2.0\\r\\nBEGIN:VEVENT\\r\\nDTSTART;VALUE=DATE:20210925\\r\\nDTEND;VALUE=DATE:20210926\\r\\nDTSTAMP:20210923T090022Z\\r\\nORGANIZER;CN=garciabenjamin30@gmail.com:mailto:garciabenjamin30@gmail.com\\r\\nUID:4rus20gb7bkamq3l8q1gmm8n8g@google.com\\r\\nRECURRENCE-ID;VALUE=DATE:20210925\\r\\nSEQUENCE:0\\r\\nEND:VEVENT\\r\\nEND:VCALENDAR\",\"Signature\":null,\"Author\":\"garciabenjamin30@gmail.com\"}"
                        )
                    ),
                    calendarEvents = emptyList(),
                    personalEvents = emptyList(),
                    attendeesEvents = emptyList(),
                    attendees = emptyList(),
                    isProtonProtonInvite = 0
                )
            )
        )
    }

    private fun mockEventsByUidRecurringWithSingleEditsAndExDate(): EventsByUidApiResponse {
        // Recurring with two single edits and one ex date (no normal occurrences)
        return EventsByUidApiResponse(
            events = listOf(
                EventEntity( // Parent
                    id = "eventIdParent",
                    calendarId = "calendarId",
                    sharedEventId = "sharedEventId",
                    calendarKeyPacket = null,
                    createTime = 0L,
                    modifyTime = 0L,
                    permissions = 1,
                    sharedKeyPacket = "sharedKeyPacket",
                    sharedEvents = listOf(
                        Json.decodeFromString<JsonElement>(
                            "{\"Type\":2,\"Data\":\"BEGIN:VCALENDAR\\r\\nVERSION:2.0\\r\\nPRODID:-//Proton Technologies//AndroidCalendar 0.25.3//EN\\r\\nBEGIN:VEVENT\\r\\nUID:4rus20gb7bkamq3l8q1gmm8n8g@google.com\\r\\nDTSTAMP:20210923T074445Z\\r\\nDTSTART;VALUE=DATE:20210923\\r\\nDTEND;VALUE=DATE:20210924\\r\\nRRULE:FREQ=DAILY;COUNT=3\\r\\nSEQUENCE:0\\r\\nEXDATE;VALUE=DATE:20210923\\r\\nORGANIZER;CN=garciabenjamin30@gmail.com:mailto:garciabenjamin30@gmail.com\\r\\nEND:VEVENT\\r\\nEND:VCALENDAR\\r\\n\",\"Signature\":\"-----BEGIN PGP SIGNATURE-----\\nVersion: GopenPGP 2.2.1\\nComment: https://gopenpgp.org\\n\\nwsBzBAABCgAnBQJhTElJCRCx+3wR1+oHYhYhBAh54WjgdlZnDZztcLH7fBHX6gdi\\nAAChRQf/TNDmfTnShgqV2J5fQU8KTISYGboQPx2cK2CaStH1aqKZEud2ld6zXxEQ\\n9dF1dDNGPRn7FEb6IWg+1dFhSSNMPAsqoWfaBv+j4g7o0SXB57qWfkPHjOo70g33\\nIkfmJuqyS825S/6S7nzzYv6SQd/GqtJ/P9Igt9itgWbXOfQgMDFnKgjjJeop7Wkp\\no0gw3va6rmv+eEbjCiTjx1m1R+CEHx2btrk51U/dyiIlwBdpYbWeMmfsm3YMTjmi\\nFFg2jHIL9vo5lICn6Giu41z51scMtwoNsqscLOsxtjxorXhOl85S/EG4PwPgVSKd\\nyo+mygzeo2xioIByVT0gap9C6GfF3w==\\n=Z6Dw\\n-----END PGP SIGNATURE-----\",\"Author\":\"benjaminlovesdebugging@pm.me\"}"
                        )
                    ),
                    calendarEvents = emptyList(),
                    personalEvents = emptyList(),
                    attendeesEvents = emptyList(),
                    attendees = emptyList(),
                    isProtonProtonInvite = 0
                ),
                EventEntity( // Single edit 1
                    id = "eventIdSingleEdit1",
                    calendarId = "calendarId",
                    sharedEventId = "sharedEventId",
                    calendarKeyPacket = null,
                    createTime = 0L,
                    modifyTime = 0L,
                    permissions = 3,
                    sharedKeyPacket = "sharedKeyPacket",
                    sharedEvents = listOf(
                        Json.decodeFromString<JsonElement>(
                            "{\"Type\":0,\"Data\":\"BEGIN:VCALENDAR\\r\\nPRODID:-//Google Inc//Google Calendar 70.9054//EN\\r\\nVERSION:2.0\\r\\nBEGIN:VEVENT\\r\\nDTSTART;VALUE=DATE:20210924\\r\\nDTEND;VALUE=DATE:20210925\\r\\nDTSTAMP:20210923T080645Z\\r\\nORGANIZER;CN=garciabenjamin30@gmail.com:mailto:garciabenjamin30@gmail.com\\r\\nUID:4rus20gb7bkamq3l8q1gmm8n8g@google.com\\r\\nRECURRENCE-ID;VALUE=DATE:20210924\\r\\nSEQUENCE:0\\r\\nEND:VEVENT\\r\\nEND:VCALENDAR\",\"Signature\":null,\"Author\":\"garciabenjamin30@gmail.com\"}"
                        )
                    ),
                    calendarEvents = emptyList(),
                    personalEvents = emptyList(),
                    attendeesEvents = emptyList(),
                    attendees = emptyList(),
                    isProtonProtonInvite = 0
                ),
                EventEntity( // Single edit 2
                    id = "eventIdSingleEdit2",
                    calendarId = "calendarId",
                    sharedEventId = "sharedEventId",
                    calendarKeyPacket = null,
                    createTime = 0L,
                    modifyTime = 0L,
                    permissions = 3,
                    sharedKeyPacket = "sharedKeyPacket",
                    sharedEvents = listOf(
                        Json.decodeFromString<JsonElement>(
                            "{\"Type\":0,\"Data\":\"BEGIN:VCALENDAR\\r\\nPRODID:-//Google Inc//Google Calendar 70.9054//EN\\r\\nVERSION:2.0\\r\\nBEGIN:VEVENT\\r\\nDTSTART;VALUE=DATE:20210925\\r\\nDTEND;VALUE=DATE:20210926\\r\\nDTSTAMP:20210923T090022Z\\r\\nORGANIZER;CN=garciabenjamin30@gmail.com:mailto:garciabenjamin30@gmail.com\\r\\nUID:4rus20gb7bkamq3l8q1gmm8n8g@google.com\\r\\nRECURRENCE-ID;VALUE=DATE:20210925\\r\\nSEQUENCE:0\\r\\nEND:VEVENT\\r\\nEND:VCALENDAR\",\"Signature\":null,\"Author\":\"garciabenjamin30@gmail.com\"}"
                        )
                    ),
                    calendarEvents = emptyList(),
                    personalEvents = emptyList(),
                    attendeesEvents = emptyList(),
                    attendees = emptyList(),
                    isProtonProtonInvite = 0
                )
            )
        )
    }

    private fun mockEventsByUidStandaloneSingleEdit(): EventsByUidApiResponse {
        return EventsByUidApiResponse(
            events = listOf(
                EventEntity( // Parent
                    id = "eventIdParent",
                    calendarId = "calendarId",
                    sharedEventId = "sharedEventId",
                    calendarKeyPacket = null,
                    createTime = 0L,
                    modifyTime = 0L,
                    permissions = 1,
                    sharedKeyPacket = "sharedKeyPacket",
                    sharedEvents = listOf(
                        Json.decodeFromString<JsonElement>(
                            "{\"Type\":2,\"Data\":\"BEGIN:VCALENDAR\\r\\nVERSION:2.0\\r\\nPRODID:-//Proton Technologies//AndroidCalendar 0.25.3//EN\\r\\nBEGIN:VEVENT\\r\\nUID:4rus20gb7bkamq3l8q1gmm8n8g@google.com\\r\\nDTSTAMP:20210923T074445Z\\r\\nDTSTART;VALUE=DATE:20210923\\r\\nDTEND;VALUE=DATE:20210924\\r\\nRRULE:FREQ=DAILY;COUNT=3\\r\\nSEQUENCE:0\\r\\nEXDATE;VALUE=DATE:20210923\\r\\nEXDATE;VALUE=DATE:20210925\\r\\nORGANIZER;CN=garciabenjamin30@gmail.com:mailto:garciabenjamin30@gmail.com\\r\\nEND:VEVENT\\r\\nEND:VCALENDAR\\r\\n\",\"Signature\":\"-----BEGIN PGP SIGNATURE-----\\nVersion: GopenPGP 2.2.1\\nComment: https://gopenpgp.org\\n\\nwsBzBAABCgAnBQJhTEuhCRCx+3wR1+oHYhYhBAh54WjgdlZnDZztcLH7fBHX6gdi\\nAACSdAf+I3/PraHuFtmCwHtC9du8Q8ei8srVjKgKmhNcfB9fSPs0olYwp0Y/4gPa\\nTy0QefqYbPtRPjGvjwAmDUNncmQG4CH3NyfFMqzRqPa74xBOM24XcH4c4fnwpenu\\nRe3q9Md+zoSD7fIydr58euJhlNuarejUSBhlYDdZhd53RYigPuSM3DMPPp8Gy5Nt\\njUI+hbhD36g6IW43jxgwt4Zfow8IlLlFMYbdsX0OP3Sw14USdbOYWNtLNucXXiRx\\nMdStbTUML7Ofg+mUP4aQnwsqcrPbWPSsqvjBB7dpE3TnoKgRE2UeYL5hIdBMMVRg\\nJXUjW+b9CZBQLLXPpQ1Ia9ep1TN04A==\\n=a2n8\\n-----END PGP SIGNATURE-----\",\"Author\":\"benjaminlovesdebugging@pm.me\"}"
                        )
                    ),
                    calendarEvents = emptyList(),
                    personalEvents = emptyList(),
                    attendeesEvents = emptyList(),
                    attendees = emptyList(),
                    isProtonProtonInvite = 0
                ),
                EventEntity( // Single edit 1
                    id = "eventIdSingleEdit1",
                    calendarId = "calendarId",
                    sharedEventId = "sharedEventId",
                    calendarKeyPacket = null,
                    createTime = 0L,
                    modifyTime = 0L,
                    permissions = 3,
                    sharedKeyPacket = "sharedKeyPacket",
                    sharedEvents = listOf(
                        Json.decodeFromString<JsonElement>(
                            "{\"Type\":0,\"Data\":\"BEGIN:VCALENDAR\\r\\nPRODID:-//Google Inc//Google Calendar 70.9054//EN\\r\\nVERSION:2.0\\r\\nBEGIN:VEVENT\\r\\nDTSTART;VALUE=DATE:20210924\\r\\nDTEND;VALUE=DATE:20210925\\r\\nDTSTAMP:20210923T080645Z\\r\\nORGANIZER;CN=garciabenjamin30@gmail.com:mailto:garciabenjamin30@gmail.com\\r\\nUID:4rus20gb7bkamq3l8q1gmm8n8g@google.com\\r\\nRECURRENCE-ID;VALUE=DATE:20210924\\r\\nSEQUENCE:0\\r\\nEND:VEVENT\\r\\nEND:VCALENDAR\",\"Signature\":null,\"Author\":\"garciabenjamin30@gmail.com\"}"
                        )
                    ),
                    calendarEvents = emptyList(),
                    personalEvents = emptyList(),
                    attendeesEvents = emptyList(),
                    attendees = emptyList(),
                    isProtonProtonInvite = 0)
            )
        )
    }
}
