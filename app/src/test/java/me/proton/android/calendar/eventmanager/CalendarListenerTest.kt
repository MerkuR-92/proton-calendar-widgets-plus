package me.proton.android.calendar.eventmanager

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.data.entity.CalendarFlags
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.BootstrapCalendarsUseCase
import me.proton.android.calendar.domain.usecase.KeySetupUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.eventmanager.listeners.core.CalendarListener
import me.proton.core.domain.entity.UserId
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.eventmanager.domain.entity.Action
import me.proton.core.eventmanager.domain.entity.Event
import me.proton.core.eventmanager.domain.entity.EventsResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CalendarListenerTest {

    private val db: AppDatabase = mockk()
    private val calendarsRepository: CalendarsRepository = mockk()
    private val logger: Logger = mockk(relaxed = true)
    private val bootstrapCalendarsUseCase: BootstrapCalendarsUseCase = mockk()
    private val keySetupUseCase: KeySetupUseCase = mockk()

    private lateinit var listener: CalendarListener
    private val config = EventManagerConfig.Core(UserId("user_id"))

    @BeforeEach
    fun setup() {
        clearAllMocks()

        listener = CalendarListener(db, calendarsRepository, bootstrapCalendarsUseCase, keySetupUseCase, logger)

        coEvery { calendarsRepository.selectCalendarUserSettings(any()) } returns null
        coEvery { calendarsRepository.persistCalendar(any(), any()) } returns Unit
        coEvery { calendarsRepository.deleteCalendarById(any()) } returns Unit
    }

    @Test
    fun `Can deserialize valid response`() {
        runBlocking {
            val response = EventsResponse(validResponse)

            val events = listener.deserializeEvents(config, response)

            assertThat(events.isNullOrEmpty()).isFalse()
        }
    }

    @Test
    fun `Cannot deserialize invalid response`() {
        runBlocking {
            val response = EventsResponse(brokenResponse)

            val events = listener.deserializeEvents(config, response)

            assertThat(events).isNull()
        }
    }

    @Test
    fun `Cannot deserialize empty response`() {
        runBlocking {
            val response = EventsResponse(emptyResponse)

            val events = listener.deserializeEvents(config, response)

            assertThat(events).isNull()
        }
    }

    @Test
    fun `handleIncompleteKeys setup keys for incomplete calendars`() {
        runBlocking {
            val entities = listOf(
                CalendarEntity("calendar_id", "Name", "Description", "#fff", display = 1, flags = CalendarFlags.INCOMPLETE_SETUP.value),
                CalendarEntity("calendar_id2", "Name2", "Description2", "#fff", display = 1, flags = 0),
                CalendarEntity("calendar_id3", "Name3", "Description3", "#fff", display = 1, flags = 0),
            )
            val events = entities.map { Event(Action.Create, "id", it) }
            coEvery { keySetupUseCase.execute(any(), any()) } returns UseCase.Result.Success(Unit)
            coEvery { calendarsRepository.fetchCalendar(any(), any()) } returns entities.first()

            listener.handleIncompleteKeys(config, events)

            coVerify(exactly = 1) { keySetupUseCase.execute(any(), any()) }
        }
    }

    @Test
    fun `handleIncompleteKeys will remove the incomplete flag from a calendar and makes it active if it can't be fetched`() {
        runBlocking {
            val entities = listOf(
                CalendarEntity("calendar_id", "Name", "Description", "#fff", display = 1, flags = CalendarFlags.INCOMPLETE_SETUP.value),
                CalendarEntity("calendar_id2", "Name2", "Description2", "#fff", display = 1, flags = 0),
                CalendarEntity("calendar_id3", "Name3", "Description3", "#fff", display = 1, flags = 0),
            )
            val events = entities.map { Event(Action.Create, "id", it) }
            coEvery { keySetupUseCase.execute(any(), any()) } returns UseCase.Result.Success(Unit)
            coEvery { calendarsRepository.fetchCalendar(any(), any()) } returns null

            listener.handleIncompleteKeys(config, events)

            coVerify(exactly = 1) { keySetupUseCase.execute(any(), any()) }
            val createEvents = listener.getActionMap(config)[Action.Create].orEmpty()
            assertThat(createEvents.filter { it.entity?.hasIncompleteKeySetup == true }.count()).isEqualTo(0)
        }
    }

    @Test
    fun `onCreate starts calendar bootstraping`() {
        runBlocking {
            val entities = listOf(
                CalendarEntity("calendar_id", "Name", "Description", "#fff", display = 1, flags = 0),
            )
            coEvery { bootstrapCalendarsUseCase.executeBootstrap(any(), any(), any()) } returns UseCase.Result.Success(Unit)

            listener.onCreate(config, entities)

            coVerify(exactly = 1) { bootstrapCalendarsUseCase.executeBootstrap(any(), any(), any()) }
            coVerify(exactly = 0) { calendarsRepository.persistCalendar(any(), any()) }
        }
    }

    @Test
    fun `If bootstraping in onCreate fails, the calendar is just persisted`() {
        runBlocking {
            val entities = listOf(
                CalendarEntity("calendar_id", "Name", "Description", "#fff", display = 1, flags = 0),
            )
            coEvery { bootstrapCalendarsUseCase.executeBootstrap(any(), any(), any()) } returns UseCase.Result.Error("error")

            listener.onCreate(config, entities)

            coVerify(exactly = 1) { bootstrapCalendarsUseCase.executeBootstrap(any(), any(), any()) }
            coVerify(exactly = 1) { calendarsRepository.persistCalendar(any(), any()) }
        }
    }

    @Test
    fun `onUpdate just persists the calendar`() {
        runBlocking {
            val entities = listOf(
                CalendarEntity("calendar_id", "Name", "Description", "#fff", display = 1, flags = 0),
            )

            listener.onUpdate(config, entities)

            coVerify(exactly = 1) { calendarsRepository.persistCalendar(any(), any()) }
        }
    }

    @Test
    fun `onDelete just removes the calendar`() {
        runBlocking {
            val ids = listOf("calendar_id")

            listener.onDelete(config, ids)

            coVerify(exactly = 1) { calendarsRepository.deleteCalendarById(any()) }
        }
    }

}

private const val validResponse = """
{
    "Code": 1000,
    "EventID": "scQb2qVo6ZNziFBmsRggXrCzjrvAZEz8XL6nCSiSj9DtfGJI8f6LIUKcvw5kIOjhsKBowzOMTDNQfiIjTE24XQ==",
    "Refresh": 0,
    "More": 0,
    "Calendars": [
        {
            "ID": "xZLizr66ZlJwAfcVTwiH5ewAQ3a5h6IptTBHdtP-mpuv4Sqqy5B3S8KfD-7_W8i0jxBd976glUl8q5eMAo4JCw==",
            "Action": 1,
            "Calendar": {
                "ID": "xZLizr66ZlJwAfcVTwiH5ewAQ3a5h6IptTBHdtP-mpuv4Sqqy5B3S8KfD-7_W8i0jxBd976glUl8q5eMAo4JCw==",
                "Name": "ASDADADSASD",
                "Description": "",
                "Type": 0,
                "Flags": 1,
                "Color": "#9DB99F",
                "Display": 1,
                "CalendarID": "xZLizr66ZlJwAfcVTwiH5ewAQ3a5h6IptTBHdtP-mpuv4Sqqy5B3S8KfD-7_W8i0jxBd976glUl8q5eMAo4JCw=="
            }
        }
    ],
    "CalendarMembers": [
        {
            "ID": "xZLizr66ZlJwAfcVTwiH5ewAQ3a5h6IptTBHdtP-mpuv4Sqqy5B3S8KfD-7_W8i0jxBd976glUl8q5eMAo4JCw==",
            "Action": 1,
            "Member": {
                "ID": "xZLizr66ZlJwAfcVTwiH5ewAQ3a5h6IptTBHdtP-mpuv4Sqqy5B3S8KfD-7_W8i0jxBd976glUl8q5eMAo4JCw==",
                "Permissions": 127,
                "Email": "pro@burbank.proton.black",
                "AddressID": "p5DPgsgSOQhwxfZmy4A-vVIxHd40lH8xRVg_4ulz69pz7Ox7ibSa2QXEbMg151clLRB-CQQTCRNteaIBHL_iUg==",
                "CalendarID": "xZLizr66ZlJwAfcVTwiH5ewAQ3a5h6IptTBHdtP-mpuv4Sqqy5B3S8KfD-7_W8i0jxBd976glUl8q5eMAo4JCw==",
                "Color": "#9DB99F",
                "Display": 1
            }
        }
    ],
    "Notices": []
}
"""

private const val brokenResponse = """
        {
    "Code": 1000,
    "EventID": "scQb2qVo6ZNziFBmsRggXrCzjrvAZEz8XL6nCSiSj9DtfGJI8f6LIUKcvw5kIOjhsKBowzOMTDNQfiIjTE24XQ==",
    "Refresh": 0,
    "More": 0,
    "Calendars": [
        {
            "ID": "xZLizr66ZlJwAfcVTwiH5ewAQ3a5h6IptTBHdtP-mpuv4Sqqy5B3S8KfD-7_W8i0jxBd976glUl8q5eMAo4JCw==",
            "Action": 1,
            "Calendar": {
                "ID": "xZLizr66ZlJwAfcVTwiH5ewAQ3a5h6IptTBHdtP-mpuv4Sqqy5B3S8KfD-7_W8i0jxBd976glUl8q5eMAo4JCw==",
                "Naaaaaaame": "ASDADADSASD",
                "Description": "",
                "Type": 0,
                "Flags": 1,
                "Color": "#9DB99F",
                "Display": 1,
                "CalendarID": "xZLizr66ZlJwAfcVTwiH5ewAQ3a5h6IptTBHdtP-mpuv4Sqqy5B3S8KfD-7_W8i0jxBd976glUl8q5eMAo4JCw=="
            }
        }
    ],
    "Notices": []
}
"""

private const val emptyResponse = """
{
    "Code": 1000,
    "EventID": "scQb2qVo6ZNziFBmsRggXrCzjrvAZEz8XL6nCSiSj9DtfGJI8f6LIUKcvw5kIOjhsKBowzOMTDNQfiIjTE24XQ==",
    "Refresh": 0,
    "More": 0,
    "Notices": []
}
"""
