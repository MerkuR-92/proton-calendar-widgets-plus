package me.proton.android.calendar.domain

import android.util.Log
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import me.proton.android.calendar.CalendarWidgetRefresher
import me.proton.android.calendar.common.logger.TestsLogger
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.toDate
import me.proton.android.calendar.data.CalendarsRepositoryImpl
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.AttendeesInfoResponse
import me.proton.android.calendar.data.api.EventResponse
import me.proton.android.calendar.data.api.EventsByUidApiResponse
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.db.SearchDatabase
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.api.TestsApi
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.usecase.FetchEventsUseCase
import me.proton.android.calendar.domain.usecase.GetEventWithCommentsUseCase
import me.proton.android.calendar.domain.usecase.GetFetchedEventWindowsValidity
import me.proton.android.calendar.domain.usecase.IndexEventForSearchUseCase
import me.proton.android.calendar.domain.usecase.TransformEventUseCase
import me.proton.android.calendar.domain.usecase.UpdateAlarmsUseCase
import me.proton.android.calendar.domain.usecase.UpdateEventOccurrencesUseCase
import me.proton.android.calendar.domain.usecase.UpdateFetchedEventsMetadataUseCase
import me.proton.android.calendar.eventmanager.createEventEntity
import me.proton.android.calendar.eventmanager.createEventMetadata
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.domain.entity.UserId
import me.proton.core.network.domain.NetworkManager
import me.proton.core.user.domain.UserAddressManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.TimeZone

@ExperimentalCoroutinesApi
@FlowPreview
@ExperimentalSerializationApi
internal class CalendarRepositoryTest {

    private val calendarsApiMock: CalendarsApi = mockk()
    private val testsApiMock: TestsApi = mockk()

    private lateinit var appDatabaseMock: AppDatabase

    private val transformEventUseCaseMock: TransformEventUseCase = mockk()
    private val fetchEventsUseCaseMock: FetchEventsUseCase = mockk()
    private val getEventWithCommentsUseCaseMock: GetEventWithCommentsUseCase = mockk()
    private val updateAlarmsUseCaseMock: UpdateAlarmsUseCase = mockk()
    private val calendarWidgetRefresherMock: CalendarWidgetRefresher = mockk()
    private val eventDecryptorMock: EventDecryptor = mockk()
    private val searchDatabaseMock: SearchDatabase = mockk()
    private val indexEventForSearchUseCaseMock: IndexEventForSearchUseCase = mockk()
    private val userAddressManagerMock: UserAddressManager = mockk()
    private val accountManagerMock: AccountManager = mockk()
    private val networkManagerMock: NetworkManager = mockk()
    private val updateEventOccurrencesUseCaseMock: UpdateEventOccurrencesUseCase = mockk()
    private val updateFetchedEventsMetadataUseCaseMock: UpdateFetchedEventsMetadataUseCase = mockk()
    private val getFetchedEventWindowsValidity: GetFetchedEventWindowsValidity = mockk()

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
        coEvery { appDatabaseMock.eventsDao().skeletonEventCountFlow() } returns flowOf(0)
        coEvery { appDatabaseMock.membersDao().flowMembers() } returns flowOf(listOf())
        coEvery { appDatabaseMock.calendarSettingsDao().flowCalendarSettings() } returns flowOf(listOf())
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
                TimeZone.getDefault().id
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
                TimeZone.getDefault().id
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
                TimeZone.getDefault().id
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
                TimeZone.getDefault().id
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
                TimeZone.getDefault().id
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

    @Test
    fun `should not fetch events from metadata if they are up-to-date`() {
        runBlocking {
            val metadata = createEventMetadata("id modified at 1", modifyTime = 1)
            coEvery { appDatabaseMock.eventsDao().selectById(metadata.id) } returns createEventEntity(metadata.id, modifyTime = metadata.modifyTime)

            assertThat(getCalendarRepository().shouldFetchEvent(UserId("userid-1"), metadata)).isFalse()
        }
    }

    @Test
    fun `should fetch non-recurring events from metadata if they are inside of previously requested windows`() {
        runBlocking {
            val metadata = createEventMetadata("id",
                startTime = 1577890800, // 1. January 2020 15:00:00 UTC
                endTime = 1577894400 // 1. January 2020 16:00:00 UTC
            )
            coEvery { appDatabaseMock.eventsDao().selectById(metadata.id) } returns null
            coEvery { updateFetchedEventsMetadataUseCaseMock.isWindowFullyFetched(any(), any(), any(), any()) } returns false

            val calendarsRepository = getCalendarRepository() as CalendarsRepositoryImpl

            calendarsRepository.minRequestedWindowToFetch = CalendarsRepositoryImpl.FetchWindow(
                UserId("user ID"),
                listOf("calendar 1 ID", "calendar 2 ID"),
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2020, 1, 31),
                "UTC"
            )

            calendarsRepository.maxRequestedWindowToFetch = CalendarsRepositoryImpl.FetchWindow(
                UserId("user ID"),
                listOf("calendar 1 ID", "calendar 2 ID"),
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2020, 1, 31),
                "UTC"
            )

            assertThat(calendarsRepository.shouldFetchEvent(UserId("userid-1"), metadata)).isTrue()
        }
    }

    @Test
    fun `should not fetch non-recurring events from metadata if they are outside of previously requested windows`() {
        runBlocking {
            val metadata = createEventMetadata("id",
                startTime = 1577890800, // 1. January 2020 15:00:00 UTC
                endTime = 1577894400 // 1. January 2020 16:00:00 UTC
            )
            coEvery { appDatabaseMock.eventsDao().selectById(metadata.id) } returns null
            coEvery { updateFetchedEventsMetadataUseCaseMock.isWindowFullyFetched(any(), any(), any(), any()) } returns false

            val calendarsRepository = getCalendarRepository() as CalendarsRepositoryImpl

            calendarsRepository.minRequestedWindowToFetch = CalendarsRepositoryImpl.FetchWindow(
                UserId("user ID"),
                listOf("calendar 1 ID", "calendar 2 ID"),
                LocalDate.of(2020, 3, 1),
                LocalDate.of(2020, 3, 31),
                "UTC"
            )

            calendarsRepository.maxRequestedWindowToFetch = CalendarsRepositoryImpl.FetchWindow(
                UserId("user ID"),
                listOf("calendar 1 ID", "calendar 2 ID"),
                LocalDate.of(2020, 3, 1),
                LocalDate.of(2020, 3, 31),
                "UTC"
            )

            assertThat(calendarsRepository.shouldFetchEvent(UserId("userid-1"), metadata)).isFalse()
        }
    }

    @Test
    fun `should not fetch non-recurring events from metadata if they are inside of previously requested metadata windows`() {
        runBlocking {
            val metadata = createEventMetadata("id",
                startTime = 1577890800, // 1. January 2020 15:00:00 UTC
                endTime = 1577894400 // 1. January 2020 16:00:00 UTC
            )
            coEvery { appDatabaseMock.eventsDao().selectById(metadata.id) } returns null
            coEvery { updateFetchedEventsMetadataUseCaseMock.isWindowFullyFetched(any(), any(), any(), any()) } returns true

            val calendarsRepository = getCalendarRepository() as CalendarsRepositoryImpl

            calendarsRepository.minRequestedWindowToFetch = CalendarsRepositoryImpl.FetchWindow(
                UserId("user ID"),
                listOf("calendar 1 ID", "calendar 2 ID"),
                LocalDate.of(2020, 3, 1),
                LocalDate.of(2020, 3, 31),
                "UTC"
            )

            calendarsRepository.maxRequestedWindowToFetch = CalendarsRepositoryImpl.FetchWindow(
                UserId("user ID"),
                listOf("calendar 1 ID", "calendar 2 ID"),
                LocalDate.of(2020, 3, 1),
                LocalDate.of(2020, 3, 31),
                "UTC"
            )

            assertThat(calendarsRepository.shouldFetchEvent(UserId("userid-1"), metadata)).isTrue()
        }
    }

    @Test
    fun `should fetch recurring events from metadata regardless of previously requested windows`() {
        runBlocking {
            // here the FetchWindows are empty
            val metadata = createEventMetadata("id modified at 1", modifyTime = 1, rRule = "FREQ=WEEKLY;BYDAY=TU,WE,TH,FR")
            coEvery { appDatabaseMock.eventsDao().selectById(metadata.id) } returns null

            assertThat(getCalendarRepository().shouldFetchEvent(UserId("userid-1"), metadata)).isTrue()
        }
    }

    @Test
    fun `expandOccurrencesWithSingleEditsAndExDatesToUiEvents keeps series occurrences, drops exdates, and adds single edits`() = runBlocking {
        // Given DAILY x3 series (Sep 23–25) with EXDATE on Sep 24 and a single edit on Sep 24
        val uid = "series-uid-1"
        val tz = "UTC"
        val fromDate = LocalDate.of(2025, 9, 22)
        val toDate = LocalDate.of(2025, 9, 26)

        val original = buildRecurringAllDayEvent(
            id = "root-id",
            uid = uid,
            startDate = LocalDate.of(2025, 9, 23),
            count = 3,
            exDates = listOf(LocalDate.of(2025, 9, 24))
        )
        val singleEdit = buildSingleEditAllDayEvent(
            id = "se-24",
            uid = uid,
            recurrenceDate = LocalDate.of(2025, 9, 24)
        )

        // When
        val repo = getCalendarRepository()
        val uiEvents = repo.expandOccurrencesWithSingleEditsAndExDatesToUiEvents(
            original,
            eventsSharingUid = listOf(singleEdit),
            fromDate = fromDate,
            toDate = toDate,
            timeZoneId = tz,
            userEmails = emptyList(),
            isFreeUser = false
        )!!

        // Then
        assertThat(uiEvents.size).isEqualTo(3)

        val dates = uiEvents.map { it.dateStart.toLocalDate() }.sorted()
        assertThat(dates).isEqualTo(listOf(
            LocalDate.of(2025, 9, 23),
            LocalDate.of(2025, 9, 24),
            LocalDate.of(2025, 9, 25)
        ))

        // two series occurrences (23 & 25) must be recurring (occurrenceNumber > 0)
        val recurringDates = uiEvents.filter { it.isRecurring }.map { it.dateStart.toLocalDate() }.sorted()
        assertThat(recurringDates).isEqualTo(listOf(
            LocalDate.of(2025, 9, 23),
            LocalDate.of(2025, 9, 25)
        ))

        // single edit (24) must have occurrenceNumber == 0
        val singleEditDates = uiEvents.filter { !it.isRecurring }.map { it.dateStart.toLocalDate() }
        assertThat(singleEditDates).isEqualTo(listOf(LocalDate.of(2025, 9, 24)))
    }

    @Test
    fun `expandOccurrencesWithSingleEditsAndExDatesToUiEvents filters by window`() = runBlocking {
        // Given DAILY x3 series (Sep 23–25) + single edit on Sep 24
        val uid = "series-uid-2"
        val tz = "UTC"

        val original = buildRecurringAllDayEvent(
            id = "root-id-2",
            uid = uid,
            startDate = LocalDate.of(2025, 9, 23),
            count = 3
        )
        val singleEdit = buildSingleEditAllDayEvent(
            id = "se-24-b",
            uid = uid,
            recurrenceDate = LocalDate.of(2025, 9, 24)
        )

        // When window to only Sep 25
        val repo = getCalendarRepository()
        val uiEvents = repo.expandOccurrencesWithSingleEditsAndExDatesToUiEvents(
            original,
            eventsSharingUid = listOf(singleEdit),
            fromDate = LocalDate.of(2025, 9, 25),
            toDate = LocalDate.of(2025, 9, 25),
            timeZoneId = tz,
            userEmails = emptyList(),
            isFreeUser = false
        )!!

        // Then only Sep 25 should remain
        assertThat(uiEvents.size).isEqualTo(1)
        assertThat(uiEvents.first().dateStart.toLocalDate()).isEqualTo(LocalDate.of(2025, 9, 25))
        // regular series occurrence should be marked recurring
        assertThat(uiEvents.first().isRecurring).isTrue()
    }

    @Test
    fun `expandOccurrences returns only series occurrences when no exdates or single edits`() = runBlocking {
        // Given a clean DAILY x3 series (Sep 23–25), no exdates, no single edits
        val uid = "series-uid-3"
        val tz = "UTC"

        val original = buildRecurringAllDayEvent(
            id = "root-id-3",
            uid = uid,
            startDate = LocalDate.of(2025, 9, 23),
            count = 3
        )

        val repo = getCalendarRepository()
        val uiEvents = repo.expandOccurrencesWithSingleEditsAndExDatesToUiEvents(
            original,
            eventsSharingUid = emptyList(),
            fromDate = LocalDate.of(2025, 9, 22),
            toDate = LocalDate.of(2025, 9, 26),
            timeZoneId = tz,
            userEmails = emptyList(),
            isFreeUser = false
        )!!

        assertThat(uiEvents.size).isEqualTo(3)
        assertThat(uiEvents.all { it.isRecurring }).isTrue()

        val dates = uiEvents.map { it.dateStart.toLocalDate() }.sorted()
        assertThat(dates).isEqualTo(listOf(
            LocalDate.of(2025, 9, 23),
            LocalDate.of(2025, 9, 24),
            LocalDate.of(2025, 9, 25)
        ))
    }

    private fun buildRecurringAllDayEvent(
        id: String,
        uid: String,
        startDate: LocalDate,
        count: Int,
        exDates: List<LocalDate> = emptyList(),
    ): Event {
        val ics = buildString {
            appendLine("BEGIN:VCALENDAR")
            appendLine("VERSION:2.0")
            appendLine("BEGIN:VEVENT")
            appendLine("UID:$uid")
            appendLine("DTSTAMP:20210101T000000Z")
            appendLine("DTSTART;VALUE=DATE:${toIcsDate(startDate)}")
            appendLine("DTEND;VALUE=DATE:${toIcsDate(startDate.plusDays(1))}")
            appendLine("RRULE:FREQ=DAILY;COUNT=$count")
            exDates.forEach { ex ->
                appendLine("EXDATE;VALUE=DATE:${toIcsDate(ex)}")
            }
            appendLine("SEQUENCE:0")
            appendLine("STATUS:CONFIRMED")
            appendLine("END:VEVENT")
            appendLine("END:VCALENDAR")
        }
        val iCal = me.proton.android.calendar.common.utils.ICalUtilsImpl.parseICalString(ics)!!
        val base = Event.dummyFrom(iCal)!!
        return Event.from(base, id = id)
    }

    private fun buildSingleEditAllDayEvent(
        id: String,
        uid: String,
        recurrenceDate: LocalDate
    ): Event {
        val ics = buildString {
            appendLine("BEGIN:VCALENDAR")
            appendLine("VERSION:2.0")
            appendLine("BEGIN:VEVENT")
            appendLine("UID:$uid")
            appendLine("DTSTAMP:20210101T000000Z")
            appendLine("DTSTART;VALUE=DATE:${toIcsDate(recurrenceDate)}")
            appendLine("DTEND;VALUE=DATE:${toIcsDate(recurrenceDate.plusDays(1))}")
            appendLine("RECURRENCE-ID;VALUE=DATE:${toIcsDate(recurrenceDate)}")
            appendLine("SEQUENCE:0")
            appendLine("STATUS:CONFIRMED")
            appendLine("END:VEVENT")
            appendLine("END:VCALENDAR")
        }
        val iCal = me.proton.android.calendar.common.utils.ICalUtilsImpl.parseICalString(ics)!!
        val base = Event.dummyFrom(iCal)!!
        return Event.from(base, id = id)
    }

    private fun toIcsDate(d: LocalDate) = String.format("%04d%02d%02d", d.year, d.monthValue, d.dayOfMonth)

    private fun getCalendarRepository(): CalendarsRepository {
        return CalendarsRepositoryImpl(
            appDatabaseMock,
            transformEventUseCaseMock,
            testsLogger,
            fetchEventsUseCaseMock,
            getEventWithCommentsUseCaseMock,
            updateAlarmsUseCaseMock,
            calendarsApiMock,
            testsApiMock,
            json,
            calendarWidgetRefresherMock,
            eventDecryptorMock,
            searchDatabaseMock,
            indexEventForSearchUseCaseMock,
            userAddressManagerMock,
            accountManagerMock,
            networkManagerMock,
            updateEventOccurrencesUseCaseMock,
            updateFetchedEventsMetadataUseCaseMock,
            getFetchedEventWindowsValidity
        )
    }

    private fun mockEventsByUidOrphanSingleEdit(): EventsByUidApiResponse {
        // Orphan single edit
        return EventsByUidApiResponse(
            events = listOf(
                EventResponse( // Parent
                    id = "eventIdSingleEdit",
                    calendarId = "calendarId",
                    sharedEventId = "sharedEventId",
                    calendarKeyPacket = null,
                    createTime = 0L,
                    modifyTime = 0L,
                    permissions = 1,
                    addressKeyPacket = null,
                    addressId = null,
                    sharedKeyPacket = "sharedKeyPacket",
                    sharedEvents = listOf(
                        Json.decodeFromString<JsonElement>(
                            "{\"Type\":2,\"Data\":\"BEGIN:VCALENDAR\\r\\nVERSION:2.0\\r\\nBEGIN:VEVENT\\r\\nUID:4trl5eflr8jo9vtotb0gmtsf4c@google.com\\r\\nDTSTAMP:20210923T074702Z\\r\\nDTSTART;VALUE=DATE:20210924\\r\\nRECURRENCE-ID;VALUE=DATE:20210924\\r\\nORGANIZER;CN=garciabenjamin30@gmail.com:mailto:garciabenjamin30@gmail.com\\r\\nSEQUENCE:0\\r\\nEND:VEVENT\\r\\nEND:VCALENDAR\",\"Signature\":null,\"Author\":\"benjaminlovesdebugging@pm.me\"}"
                        )
                    ),
                    calendarEvents = emptyList(),
                    attendeesEvents = emptyList(),
                    attendees = emptyList(),
                    attendeesInfo = AttendeesInfoResponse(
                        attendees = emptyList(),
                        moreAttendees = 0,
                    ),
                    isProtonProtonInvite = 0,
                    startTime = 1632441600L,
                    startTimeZone = "GMT",
                    endTime = 1632528000L,
                    endTimeZone = "GMT",
                    fullDay = 0,
                    uid = "4trl5eflr8jo9vtotb0gmtsf4c@google.com",
                    recurrenceID = 1632441600L,
                    exDates = emptyList(),
                    rRule = null,
                    isOrganizer = 0,
                    isPersonalSingleEdit = false
                )
            )
        )
    }

    private fun mockEventsByUidRecurringWithSingleEdit(): EventsByUidApiResponse {
        // Recurring with two occurrences and one single edit
        return EventsByUidApiResponse(
            events = listOf(
                EventResponse( // Parent
                    id = "eventIdParent",
                    calendarId = "calendarId",
                    sharedEventId = "sharedEventId",
                    calendarKeyPacket = null,
                    createTime = 0L,
                    modifyTime = 0L,
                    permissions = 1,
                    addressKeyPacket = null,
                    addressId = null,
                    sharedKeyPacket = "sharedKeyPacket",
                    sharedEvents = listOf(
                        Json.decodeFromString<JsonElement>(
                            "{\"Type\":2,\"Data\":\"BEGIN:VCALENDAR\\r\\nVERSION:2.0\\r\\nBEGIN:VEVENT\\r\\nUID:4rus20gb7bkamq3l8q1gmm8n8g@google.com\\r\\nDTSTAMP:20210923T074445Z\\r\\nDTSTART;VALUE=DATE:20210923\\r\\nRRULE:FREQ=DAILY;COUNT=3\\r\\nORGANIZER;CN=garciabenjamin30@gmail.com:mailto:garciabenjamin30@gmail.com\\r\\nSEQUENCE:0\\r\\nEND:VEVENT\\r\\nEND:VCALENDAR\",\"Signature\":null,\"Author\":\"benjaminlovesdebugging@pm.me\"}"
                        )
                    ),
                    calendarEvents = emptyList(),
                    attendeesEvents = emptyList(),
                    attendees = emptyList(),
                    attendeesInfo = AttendeesInfoResponse(
                        attendees = emptyList(),
                        moreAttendees = 0,
                    ),
                    isProtonProtonInvite = 0,
                    startTime = 1632355200L,
                    startTimeZone = "GMT",
                    endTime = 1632441600L,
                    endTimeZone = "GMT",
                    fullDay = 1,
                    uid = "4rus20gb7bkamq3l8q1gmm8n8g@google.com",
                    recurrenceID = null,
                    exDates = emptyList(),
                    rRule = "FREQ=DAILY;COUNT=3",
                    isOrganizer = 0,
                    isPersonalSingleEdit = false
                ),
                EventResponse( // Single edit
                    id = "eventIdSingleEdit",
                    calendarId = "calendarId",
                    sharedEventId = "sharedEventId",
                    calendarKeyPacket = null,
                    createTime = 0L,
                    modifyTime = 0L,
                    permissions = 3,
                    addressKeyPacket = null,
                    addressId = null,
                    sharedKeyPacket = "sharedKeyPacket",
                    sharedEvents = listOf(
                        Json.decodeFromString<JsonElement>(
                            "{\"Type\":0,\"Data\":\"BEGIN:VCALENDAR\\r\\nPRODID:-//Google Inc//Google Calendar 70.9054//EN\\r\\nVERSION:2.0\\r\\nBEGIN:VEVENT\\r\\nDTSTART;VALUE=DATE:20210924\\r\\nDTEND;VALUE=DATE:20210925\\r\\nDTSTAMP:20210923T080645Z\\r\\nORGANIZER;CN=garciabenjamin30@gmail.com:mailto:garciabenjamin30@gmail.com\\r\\nUID:4rus20gb7bkamq3l8q1gmm8n8g@google.com\\r\\nRECURRENCE-ID;VALUE=DATE:20210924\\r\\nSEQUENCE:0\\r\\nEND:VEVENT\\r\\nEND:VCALENDAR\",\"Signature\":null,\"Author\":\"garciabenjamin30@gmail.com\"}"
                        )
                    ),
                    calendarEvents = emptyList(),
                    attendeesEvents = emptyList(),
                    attendees = emptyList(),
                    attendeesInfo = AttendeesInfoResponse(
                        attendees = emptyList(),
                        moreAttendees = 0,
                    ),
                    isProtonProtonInvite = 0,
                    startTime = 1632441600L,
                    startTimeZone = "GMT",
                    endTime = 1632528000L,
                    endTimeZone = "GMT",
                    fullDay = 1,
                    uid = "4rus20gb7bkamq3l8q1gmm8n8g@google.com",
                    recurrenceID = 1632441600L,
                    exDates = emptyList(),
                    rRule = null,
                    isOrganizer = 0,
                    isPersonalSingleEdit = false
                )
            )
        )
    }

    private fun mockEventsByUidOnlySingleEdits(): EventsByUidApiResponse {
        // Recurring with only single edit (3)
        return EventsByUidApiResponse(
            events = listOf(
                EventResponse( // Parent
                    id = "eventIdParent",
                    calendarId = "calendarId",
                    sharedEventId = "sharedEventId",
                    calendarKeyPacket = null,
                    createTime = 0L,
                    modifyTime = 0L,
                    permissions = 1,
                    addressKeyPacket = null,
                    addressId = null,
                    sharedKeyPacket = "sharedKeyPacket",
                    sharedEvents = listOf(
                        Json.decodeFromString<JsonElement>(
                            "{\"Type\":2,\"Data\":\"BEGIN:VCALENDAR\\r\\nVERSION:2.0\\r\\nBEGIN:VEVENT\\r\\nUID:4rus20gb7bkamq3l8q1gmm8n8g@google.com\\r\\nDTSTAMP:20210923T074445Z\\r\\nDTSTART;VALUE=DATE:20210923\\r\\nRRULE:FREQ=DAILY;COUNT=3\\r\\nORGANIZER;CN=garciabenjamin30@gmail.com:mailto:garciabenjamin30@gmail.com\\r\\nSEQUENCE:0\\r\\nEND:VEVENT\\r\\nEND:VCALENDAR\",\"Signature\":null,\"Author\":\"benjaminlovesdebugging@pm.me\"}"
                        )
                    ),
                    calendarEvents = emptyList(),
                    attendeesEvents = emptyList(),
                    attendees = emptyList(),
                    attendeesInfo = AttendeesInfoResponse(
                        attendees = emptyList(),
                        moreAttendees = 0,
                    ),
                    isProtonProtonInvite = 0,
                    startTime = 1632355200L,
                    startTimeZone = "GMT",
                    endTime = 1632441600L,
                    endTimeZone = "GMT",
                    fullDay = 1,
                    uid = "4rus20gb7bkamq3l8q1gmm8n8g@google.com",
                    recurrenceID = null,
                    exDates = emptyList(),
                    rRule = "FREQ=DAILY;COUNT=3",
                    isOrganizer = 0,
                    isPersonalSingleEdit = false
                ),
                EventResponse( // Single edit 1
                    id = "eventIdSingleEdit1",
                    calendarId = "calendarId",
                    sharedEventId = "sharedEventId",
                    calendarKeyPacket = null,
                    createTime = 0L,
                    modifyTime = 0L,
                    permissions = 3,
                    addressKeyPacket = null,
                    addressId = null,
                    sharedKeyPacket = "sharedKeyPacket",
                    sharedEvents = listOf(
                        Json.decodeFromString<JsonElement>(
                            "{\"Type\":0,\"Data\":\"BEGIN:VCALENDAR\\r\\nPRODID:-//Google Inc//Google Calendar 70.9054//EN\\r\\nVERSION:2.0\\r\\nBEGIN:VEVENT\\r\\nDTSTART;VALUE=DATE:20210923\\r\\nDTEND;VALUE=DATE:20210924\\r\\nDTSTAMP:20210923T090014Z\\r\\nORGANIZER;CN=garciabenjamin30@gmail.com:mailto:garciabenjamin30@gmail.com\\r\\nUID:4rus20gb7bkamq3l8q1gmm8n8g@google.com\\r\\nRECURRENCE-ID;VALUE=DATE:20210923\\r\\nSEQUENCE:0\\r\\nEND:VEVENT\\r\\nEND:VCALENDAR\",\"Signature\":null,\"Author\":\"garciabenjamin30@gmail.com\"}"
                        )
                    ),
                    calendarEvents = emptyList(),
                    attendeesEvents = emptyList(),
                    attendees = emptyList(),
                    attendeesInfo = AttendeesInfoResponse(
                        attendees = emptyList(),
                        moreAttendees = 0,
                    ),
                    isProtonProtonInvite = 0,
                    startTime = 1632355200L,
                    startTimeZone = "GMT",
                    endTime = 1632441600L,
                    endTimeZone = "GMT",
                    fullDay = 1,
                    uid = "4rus20gb7bkamq3l8q1gmm8n8g@google.com",
                    recurrenceID = null,
                    exDates = emptyList(),
                    rRule = "FREQ=DAILY;COUNT=3",
                    isOrganizer = 0,
                    isPersonalSingleEdit = false
                ),
                EventResponse( // Single edit 2
                    id = "eventIdSingleEdit2",
                    calendarId = "calendarId",
                    sharedEventId = "sharedEventId",
                    calendarKeyPacket = null,
                    createTime = 0L,
                    modifyTime = 0L,
                    permissions = 3,
                    addressKeyPacket = null,
                    addressId = null,
                    sharedKeyPacket = "sharedKeyPacket",
                    sharedEvents = listOf(
                        Json.decodeFromString<JsonElement>(
                            "{\"Type\":0,\"Data\":\"BEGIN:VCALENDAR\\r\\nPRODID:-//Google Inc//Google Calendar 70.9054//EN\\r\\nVERSION:2.0\\r\\nBEGIN:VEVENT\\r\\nDTSTART;VALUE=DATE:20210924\\r\\nDTEND;VALUE=DATE:20210925\\r\\nDTSTAMP:20210923T080645Z\\r\\nORGANIZER;CN=garciabenjamin30@gmail.com:mailto:garciabenjamin30@gmail.com\\r\\nUID:4rus20gb7bkamq3l8q1gmm8n8g@google.com\\r\\nRECURRENCE-ID;VALUE=DATE:20210924\\r\\nSEQUENCE:0\\r\\nEND:VEVENT\\r\\nEND:VCALENDAR\",\"Signature\":null,\"Author\":\"garciabenjamin30@gmail.com\"}"
                        )
                    ),
                    calendarEvents = emptyList(),
                    attendeesEvents = emptyList(),
                    attendees = emptyList(),
                    attendeesInfo = AttendeesInfoResponse(
                        attendees = emptyList(),
                        moreAttendees = 0,
                    ),
                    isProtonProtonInvite = 0,
                    startTime = 1632355200L,
                    startTimeZone = "GMT",
                    endTime = 1632441600L,
                    endTimeZone = "GMT",
                    fullDay = 1,
                    uid = "4rus20gb7bkamq3l8q1gmm8n8g@google.com",
                    recurrenceID = null,
                    exDates = emptyList(),
                    rRule = "FREQ=DAILY;COUNT=3",
                    isOrganizer = 0,
                    isPersonalSingleEdit = false
                ),
                EventResponse( // Single edit 3
                    id = "eventIdSingleEdit3",
                    calendarId = "calendarId",
                    sharedEventId = "sharedEventId",
                    calendarKeyPacket = null,
                    createTime = 0L,
                    modifyTime = 0L,
                    permissions = 3,
                    addressKeyPacket = null,
                    addressId = null,
                    sharedKeyPacket = "sharedKeyPacket",
                    sharedEvents = listOf(
                        Json.decodeFromString<JsonElement>(
                            "{\"Type\":0,\"Data\":\"BEGIN:VCALENDAR\\r\\nPRODID:-//Google Inc//Google Calendar 70.9054//EN\\r\\nVERSION:2.0\\r\\nBEGIN:VEVENT\\r\\nDTSTART;VALUE=DATE:20210925\\r\\nDTEND;VALUE=DATE:20210926\\r\\nDTSTAMP:20210923T090022Z\\r\\nORGANIZER;CN=garciabenjamin30@gmail.com:mailto:garciabenjamin30@gmail.com\\r\\nUID:4rus20gb7bkamq3l8q1gmm8n8g@google.com\\r\\nRECURRENCE-ID;VALUE=DATE:20210925\\r\\nSEQUENCE:0\\r\\nEND:VEVENT\\r\\nEND:VCALENDAR\",\"Signature\":null,\"Author\":\"garciabenjamin30@gmail.com\"}"
                        )
                    ),
                    calendarEvents = emptyList(),
                    attendeesEvents = emptyList(),
                    attendees = emptyList(),
                    attendeesInfo = AttendeesInfoResponse(
                        attendees = emptyList(),
                        moreAttendees = 0,
                    ),
                    isProtonProtonInvite = 0,
                    startTime = 1632355200L,
                    startTimeZone = "GMT",
                    endTime = 1632441600L,
                    endTimeZone = "GMT",
                    fullDay = 1,
                    uid = "4rus20gb7bkamq3l8q1gmm8n8g@google.com",
                    recurrenceID = null,
                    exDates = emptyList(),
                    rRule = "FREQ=DAILY;COUNT=3",
                    isOrganizer = 0,
                    isPersonalSingleEdit = false
                )
            )
        )
    }

    private fun mockEventsByUidRecurringWithSingleEditsAndExDate(): EventsByUidApiResponse {
        // Recurring with two single edits and one ex date (no normal occurrences)
        return EventsByUidApiResponse(
            events = listOf(
                EventResponse( // Parent
                    id = "eventIdParent",
                    calendarId = "calendarId",
                    sharedEventId = "sharedEventId",
                    calendarKeyPacket = null,
                    createTime = 0L,
                    modifyTime = 0L,
                    permissions = 1,
                    addressKeyPacket = null,
                    addressId = null,
                    sharedKeyPacket = "sharedKeyPacket",
                    sharedEvents = listOf(
                        Json.decodeFromString<JsonElement>(
                            "{\"Type\":2,\"Data\":\"BEGIN:VCALENDAR\\r\\nVERSION:2.0\\r\\nPRODID:-//Proton Technologies//AndroidCalendar 0.25.3//EN\\r\\nBEGIN:VEVENT\\r\\nUID:4rus20gb7bkamq3l8q1gmm8n8g@google.com\\r\\nDTSTAMP:20210923T074445Z\\r\\nDTSTART;VALUE=DATE:20210923\\r\\nDTEND;VALUE=DATE:20210924\\r\\nRRULE:FREQ=DAILY;COUNT=3\\r\\nSEQUENCE:0\\r\\nEXDATE;VALUE=DATE:20210923\\r\\nORGANIZER;CN=garciabenjamin30@gmail.com:mailto:garciabenjamin30@gmail.com\\r\\nEND:VEVENT\\r\\nEND:VCALENDAR\\r\\n\",\"Signature\":\"-----BEGIN PGP SIGNATURE-----\\nVersion: GopenPGP 2.2.1\\nComment: https://gopenpgp.org\\n\\nwsBzBAABCgAnBQJhTElJCRCx+3wR1+oHYhYhBAh54WjgdlZnDZztcLH7fBHX6gdi\\nAAChRQf/TNDmfTnShgqV2J5fQU8KTISYGboQPx2cK2CaStH1aqKZEud2ld6zXxEQ\\n9dF1dDNGPRn7FEb6IWg+1dFhSSNMPAsqoWfaBv+j4g7o0SXB57qWfkPHjOo70g33\\nIkfmJuqyS825S/6S7nzzYv6SQd/GqtJ/P9Igt9itgWbXOfQgMDFnKgjjJeop7Wkp\\no0gw3va6rmv+eEbjCiTjx1m1R+CEHx2btrk51U/dyiIlwBdpYbWeMmfsm3YMTjmi\\nFFg2jHIL9vo5lICn6Giu41z51scMtwoNsqscLOsxtjxorXhOl85S/EG4PwPgVSKd\\nyo+mygzeo2xioIByVT0gap9C6GfF3w==\\n=Z6Dw\\n-----END PGP SIGNATURE-----\",\"Author\":\"benjaminlovesdebugging@pm.me\"}"
                        )
                    ),
                    calendarEvents = emptyList(),
                    attendeesEvents = emptyList(),
                    attendees = emptyList(),
                    attendeesInfo = AttendeesInfoResponse(
                        attendees = emptyList(),
                        moreAttendees = 0,
                    ),
                    isProtonProtonInvite = 0,
                    startTime = 1632355200L,
                    startTimeZone = "GMT",
                    endTime = 1632441600L,
                    endTimeZone = "GMT",
                    fullDay = 1,
                    uid = "4rus20gb7bkamq3l8q1gmm8n8g@google.com",
                    recurrenceID = null,
                    exDates = emptyList(),
                    rRule = "FREQ=DAILY;COUNT=3",
                    isOrganizer = 0,
                    isPersonalSingleEdit = false
                ),
                EventResponse( // Single edit 1
                    id = "eventIdSingleEdit1",
                    calendarId = "calendarId",
                    sharedEventId = "sharedEventId",
                    calendarKeyPacket = null,
                    createTime = 0L,
                    modifyTime = 0L,
                    permissions = 3,
                    addressKeyPacket = null,
                    addressId = null,
                    sharedKeyPacket = "sharedKeyPacket",
                    sharedEvents = listOf(
                        Json.decodeFromString<JsonElement>(
                            "{\"Type\":0,\"Data\":\"BEGIN:VCALENDAR\\r\\nPRODID:-//Google Inc//Google Calendar 70.9054//EN\\r\\nVERSION:2.0\\r\\nBEGIN:VEVENT\\r\\nDTSTART;VALUE=DATE:20210924\\r\\nDTEND;VALUE=DATE:20210925\\r\\nDTSTAMP:20210923T080645Z\\r\\nORGANIZER;CN=garciabenjamin30@gmail.com:mailto:garciabenjamin30@gmail.com\\r\\nUID:4rus20gb7bkamq3l8q1gmm8n8g@google.com\\r\\nRECURRENCE-ID;VALUE=DATE:20210924\\r\\nSEQUENCE:0\\r\\nEND:VEVENT\\r\\nEND:VCALENDAR\",\"Signature\":null,\"Author\":\"garciabenjamin30@gmail.com\"}"
                        )
                    ),
                    calendarEvents = emptyList(),
                    attendeesEvents = emptyList(),
                    attendees = emptyList(),
                    attendeesInfo = AttendeesInfoResponse(
                        attendees = emptyList(),
                        moreAttendees = 0,
                    ),
                    isProtonProtonInvite = 0,
                    startTime = 1632355200L,
                    startTimeZone = "GMT",
                    endTime = 1632441600L,
                    endTimeZone = "GMT",
                    fullDay = 1,
                    uid = "4rus20gb7bkamq3l8q1gmm8n8g@google.com",
                    recurrenceID = null,
                    exDates = emptyList(),
                    rRule = "FREQ=DAILY;COUNT=3",
                    isOrganizer = 0,
                    isPersonalSingleEdit = false
                ),
                EventResponse( // Single edit 2
                    id = "eventIdSingleEdit2",
                    calendarId = "calendarId",
                    sharedEventId = "sharedEventId",
                    calendarKeyPacket = null,
                    createTime = 0L,
                    modifyTime = 0L,
                    permissions = 3,
                    addressKeyPacket = null,
                    addressId = null,
                    sharedKeyPacket = "sharedKeyPacket",
                    sharedEvents = listOf(
                        Json.decodeFromString<JsonElement>(
                            "{\"Type\":0,\"Data\":\"BEGIN:VCALENDAR\\r\\nPRODID:-//Google Inc//Google Calendar 70.9054//EN\\r\\nVERSION:2.0\\r\\nBEGIN:VEVENT\\r\\nDTSTART;VALUE=DATE:20210925\\r\\nDTEND;VALUE=DATE:20210926\\r\\nDTSTAMP:20210923T090022Z\\r\\nORGANIZER;CN=garciabenjamin30@gmail.com:mailto:garciabenjamin30@gmail.com\\r\\nUID:4rus20gb7bkamq3l8q1gmm8n8g@google.com\\r\\nRECURRENCE-ID;VALUE=DATE:20210925\\r\\nSEQUENCE:0\\r\\nEND:VEVENT\\r\\nEND:VCALENDAR\",\"Signature\":null,\"Author\":\"garciabenjamin30@gmail.com\"}"
                        )
                    ),
                    calendarEvents = emptyList(),
                    attendeesEvents = emptyList(),
                    attendees = emptyList(),
                    attendeesInfo = AttendeesInfoResponse(
                        attendees = emptyList(),
                        moreAttendees = 0,
                    ),
                    isProtonProtonInvite = 0,
                    startTime = 1632355200L,
                    startTimeZone = "GMT",
                    endTime = 1632441600L,
                    endTimeZone = "GMT",
                    fullDay = 1,
                    uid = "4rus20gb7bkamq3l8q1gmm8n8g@google.com",
                    recurrenceID = null,
                    exDates = emptyList(),
                    rRule = "FREQ=DAILY;COUNT=3",
                    isOrganizer = 0,
                    isPersonalSingleEdit = false
                )
            )
        )
    }

    private fun mockEventsByUidStandaloneSingleEdit(): EventsByUidApiResponse {
        return EventsByUidApiResponse(
            events = listOf(
                EventResponse( // Parent
                    id = "eventIdParent",
                    calendarId = "calendarId",
                    sharedEventId = "sharedEventId",
                    calendarKeyPacket = null,
                    createTime = 0L,
                    modifyTime = 0L,
                    permissions = 1,
                    addressKeyPacket = null,
                    addressId = null,
                    sharedKeyPacket = "sharedKeyPacket",
                    sharedEvents = listOf(
                        Json.decodeFromString<JsonElement>(
                            "{\"Type\":2,\"Data\":\"BEGIN:VCALENDAR\\r\\nVERSION:2.0\\r\\nPRODID:-//Proton Technologies//AndroidCalendar 0.25.3//EN\\r\\nBEGIN:VEVENT\\r\\nUID:4rus20gb7bkamq3l8q1gmm8n8g@google.com\\r\\nDTSTAMP:20210923T074445Z\\r\\nDTSTART;VALUE=DATE:20210923\\r\\nDTEND;VALUE=DATE:20210924\\r\\nRRULE:FREQ=DAILY;COUNT=3\\r\\nSEQUENCE:0\\r\\nEXDATE;VALUE=DATE:20210923\\r\\nEXDATE;VALUE=DATE:20210925\\r\\nORGANIZER;CN=garciabenjamin30@gmail.com:mailto:garciabenjamin30@gmail.com\\r\\nEND:VEVENT\\r\\nEND:VCALENDAR\\r\\n\",\"Signature\":\"-----BEGIN PGP SIGNATURE-----\\nVersion: GopenPGP 2.2.1\\nComment: https://gopenpgp.org\\n\\nwsBzBAABCgAnBQJhTEuhCRCx+3wR1+oHYhYhBAh54WjgdlZnDZztcLH7fBHX6gdi\\nAACSdAf+I3/PraHuFtmCwHtC9du8Q8ei8srVjKgKmhNcfB9fSPs0olYwp0Y/4gPa\\nTy0QefqYbPtRPjGvjwAmDUNncmQG4CH3NyfFMqzRqPa74xBOM24XcH4c4fnwpenu\\nRe3q9Md+zoSD7fIydr58euJhlNuarejUSBhlYDdZhd53RYigPuSM3DMPPp8Gy5Nt\\njUI+hbhD36g6IW43jxgwt4Zfow8IlLlFMYbdsX0OP3Sw14USdbOYWNtLNucXXiRx\\nMdStbTUML7Ofg+mUP4aQnwsqcrPbWPSsqvjBB7dpE3TnoKgRE2UeYL5hIdBMMVRg\\nJXUjW+b9CZBQLLXPpQ1Ia9ep1TN04A==\\n=a2n8\\n-----END PGP SIGNATURE-----\",\"Author\":\"benjaminlovesdebugging@pm.me\"}"
                        )
                    ),
                    calendarEvents = emptyList(),
                    attendeesEvents = emptyList(),
                    attendees = emptyList(),
                    attendeesInfo = AttendeesInfoResponse(
                        attendees = emptyList(),
                        moreAttendees = 0,
                    ),
                    isProtonProtonInvite = 0,
                    startTime = 1632355200L,
                    startTimeZone = "GMT",
                    endTime = 1632441600L,
                    endTimeZone = "GMT",
                    fullDay = 1,
                    uid = "4rus20gb7bkamq3l8q1gmm8n8g@google.com",
                    recurrenceID = null,
                    exDates = emptyList(),
                    rRule = "FREQ=DAILY;COUNT=3",
                    isOrganizer = 0,
                    isPersonalSingleEdit = false
                ),
                EventResponse( // Single edit 1
                    id = "eventIdSingleEdit1",
                    calendarId = "calendarId",
                    sharedEventId = "sharedEventId",
                    calendarKeyPacket = null,
                    createTime = 0L,
                    modifyTime = 0L,
                    permissions = 3,
                    addressKeyPacket = null,
                    addressId = null,
                    sharedKeyPacket = "sharedKeyPacket",
                    sharedEvents = listOf(
                        Json.decodeFromString<JsonElement>(
                            "{\"Type\":0,\"Data\":\"BEGIN:VCALENDAR\\r\\nPRODID:-//Google Inc//Google Calendar 70.9054//EN\\r\\nVERSION:2.0\\r\\nBEGIN:VEVENT\\r\\nDTSTART;VALUE=DATE:20210924\\r\\nDTEND;VALUE=DATE:20210925\\r\\nDTSTAMP:20210923T080645Z\\r\\nORGANIZER;CN=garciabenjamin30@gmail.com:mailto:garciabenjamin30@gmail.com\\r\\nUID:4rus20gb7bkamq3l8q1gmm8n8g@google.com\\r\\nRECURRENCE-ID;VALUE=DATE:20210924\\r\\nSEQUENCE:0\\r\\nEND:VEVENT\\r\\nEND:VCALENDAR\",\"Signature\":null,\"Author\":\"garciabenjamin30@gmail.com\"}"
                        )
                    ),
                    calendarEvents = emptyList(),
                    attendeesEvents = emptyList(),
                    attendeesInfo = AttendeesInfoResponse(
                        attendees = emptyList(),
                        moreAttendees = 0,
                    ),
                    attendees = emptyList(),
                    isProtonProtonInvite = 0,
                    startTime = 1632355200L,
                    startTimeZone = "GMT",
                    endTime = 1632441600L,
                    endTimeZone = "GMT",
                    fullDay = 1,
                    uid = "4rus20gb7bkamq3l8q1gmm8n8g@google.com",
                    recurrenceID = null,
                    exDates = emptyList(),
                    rRule = "FREQ=DAILY;COUNT=3",
                    isOrganizer = 0,
                    isPersonalSingleEdit = false
                )
            )
        )
    }
}
