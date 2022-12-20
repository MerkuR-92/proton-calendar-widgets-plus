package me.proton.android.calendar.eventmanager

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.WidgetRefresher
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.EventApiResponse
import me.proton.android.calendar.data.api.ServerEvent
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.FetchPublicKeysUseCase
import me.proton.android.calendar.domain.usecase.GetMinimalCalendarEventsUseCase
import me.proton.android.calendar.domain.usecase.HandleEventsMetadataUseCase
import me.proton.android.calendar.domain.usecase.UpdateAlarmsUseCase
import me.proton.android.calendar.eventmanager.listeners.calendar.CalendarEventListener
import me.proton.android.calendar.eventmanager.listeners.calendar.CalendarEventListenerDelegate
import me.proton.android.calendar.mocks.*
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.eventmanager.domain.entity.Action
import me.proton.core.eventmanager.domain.entity.Event
import me.proton.core.eventmanager.domain.entity.EventsResponse
import org.junit.Ignore
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class CalendarEventListenerTest {

    private val db: AppDatabase = mockk()
    private val calendarsRepository: CalendarsRepository = mockk()
    private val delegate: CalendarEventListenerDelegate = mockk(relaxed = true)
    private val getMinimalCalendarEventsUseCase: GetMinimalCalendarEventsUseCase = mockk(relaxed = true)
    private val updateAlarmsUseCase: UpdateAlarmsUseCase = mockk(relaxed = true)
    private val logger: Logger = mockk(relaxed = true)

    private lateinit var listener: CalendarEventListener
    private val config = EventManagerConfig.Calendar(userId, calendarId)

    @BeforeEach
    fun setup() {
        clearAllMocks()
        listener = CalendarEventListener(
            db,
            calendarsRepository,
            delegate,
            getMinimalCalendarEventsUseCase,
            updateAlarmsUseCase,
            logger,
        )
        coEvery { calendarsRepository.hasCalendar(any()) } returns true
    }

    @Test
    fun `deserializeEvents can deserialize a response`() {
        runBlocking {
            val result = listener.deserializeEvents(config, EventsResponse(eventsResponse))

            assertThat(result.isNullOrEmpty()).isFalse()
        }
    }

    @Test
    fun `onPrepare calls delegate's onPrepare`() {
        runBlocking {
            val metadata = listOf(createEventMetadata("id_1"))

            listener.onPrepare(config, metadata)

            coVerify(exactly = 1) { delegate.onPrepare(any(), any()) }
        }
    }

    @Test
    fun `onCreate calls delegate's onCreate if calendar is present`() {
        runBlocking {
            val capturedIds = slot<List<String>>()
            coEvery { delegate.onCreate(capture(capturedIds)) } returns Unit
            val metadata = listOf(createEventMetadata("id_1"))

            listener.onCreate(config, metadata)

            coVerify(exactly = 1) { delegate.onCreate(any()) }
            assertThat(capturedIds.captured).isEqualTo(metadata.map { it.id })
        }
    }

    @Test
    fun `onCreate doesn't call delegate's onCreate if calendar is not present`() {
        runBlocking {
            coEvery { calendarsRepository.hasCalendar(any()) } returns false
            val metadata = listOf(createEventMetadata("id_1"))

            listener.onCreate(config, metadata)

            coVerify(exactly = 0) { delegate.onCreate(any()) }
            coVerify(exactly = 1) { logger.i(any()) }
        }
    }

    @Test
    fun `onUpdate calls delegate's onUpdate`() {
        runBlocking {
            val capturedIds = slot<List<String>>()
            coEvery { delegate.onUpdate(capture(capturedIds)) } returns Unit
            val metadata = listOf(createEventMetadata("id_1"))

            listener.onUpdate(config, metadata)

            coVerify(exactly = 1) { delegate.onUpdate(any()) }
            assertThat(capturedIds.captured).isEqualTo(metadata.map { it.id })
        }
    }

    @Test
    fun `onUpdate doesn't call delegate's onUpdate if calendar is not present`() {
        runBlocking {
            coEvery { calendarsRepository.hasCalendar(any()) } returns false
            val metadata = listOf(createEventMetadata("id_1"))

            listener.onUpdate(config, metadata)

            coVerify(exactly = 0) { delegate.onUpdate(any()) }
            coVerify(exactly = 1) { logger.i(any()) }
        }
    }

    @Test
    fun `onDelete calls delegate's onDelete`() {
        runBlocking {
            val capturedIds = slot<List<String>>()
            coEvery { delegate.onDelete(capture(capturedIds)) } returns Unit
            val ids = listOf("id_1")

            listener.onDelete(config, ids)

            coVerify(exactly = 1) { delegate.onDelete(any()) }
            assertThat(capturedIds.captured).isEqualTo(ids)
        }
    }

    @Ignore("You cannot mock listener here. You should use listener.notifySuccess(...)")
    fun `onSuccess calls delegate's onSuccess with events that were either created or updated`() {
        runBlocking {
            val capturedIds = slot<List<String>>()
            coEvery { delegate.onSuccess(any(), capture(capturedIds)) } returns Unit

            coEvery { listener.getActionMap(any()) } returns mapOf(
                Action.Create to listOf(Event(Action.Create, "id_1", createEventMetadata("id_1"))),
                Action.Update to listOf(Event(Action.Update, "id_2", createEventMetadata("id_2"))),
                Action.Delete to listOf(Event(Action.Delete, "id_3", null)),
            )

            listener.onSuccess(config)

            coVerify(exactly = 1) { delegate.onSuccess(any(), any()) }
            assertThat(capturedIds.captured).isEqualTo(listOf("id_1", "id_2"))
        }
    }

    @Test
    fun `onResetAll deletes all events and fetches recent events`() {
        runBlocking {
            coEvery { calendarsRepository.deleteAllEvents(any()) } returns Unit
            coEvery { getMinimalCalendarEventsUseCase.execute(any(), any()) } returns flowOf(emptyList())

            listener.onResetAll(config)

            coVerify(exactly = 1) { calendarsRepository.deleteAllEvents(any()) }
            coVerify(exactly = 1) { getMinimalCalendarEventsUseCase.execute(any(), true) }
        }
    }
}

class CalendarEventListenerDelegateTest {

    private val calendarsRepository: CalendarsRepository = mockk()
    private val logger: Logger = mockk(relaxed = true)
    private val fetchPublicKeysUseCase: FetchPublicKeysUseCase = mockk(relaxed = true)
    private val widgetRefresher: WidgetRefresher = mockk(relaxed = true)
    private val handleEventsMetadataUseCase: HandleEventsMetadataUseCase = mockk(relaxed = true)
    private val updateAlarmsUseCase: UpdateAlarmsUseCase = mockk(relaxed = true)

    private lateinit var delegate: CalendarEventListenerDelegate
    private val config = EventManagerConfig.Calendar(userId, calendarId)

    @BeforeEach
    fun setup() {
        delegate = CalendarEventListenerDelegate(
            calendarsRepository,
            fetchPublicKeysUseCase,
            widgetRefresher,
            handleEventsMetadataUseCase,
            updateAlarmsUseCase,
        )

        coEvery { calendarsRepository.shouldFetchEvent(any()) } returns true
        coEvery { calendarsRepository.fetchEventById(any(), any(), any()) } answers {
            val id = args[2] as String
            ApiResponse.Success(
                EventApiResponse(createEventEntity(id))
            )
        }

        every { handleEventsMetadataUseCase.now } returns Instant.now()
        every { handleEventsMetadataUseCase.shouldFetchEvent(any()) } answers { callOriginal() }
    }

    @Test
    fun `onPrepare fetches recent events to match the metadata`() {
        runBlocking {
            val metadata = listOf(
                createEventMetadata("id_1"),
                createEventMetadata("id_2"),
                createEventMetadata("id_3"),
            )

            delegate.onPrepare(config, metadata)

            coVerify(exactly = metadata.count()) { calendarsRepository.fetchEventById(any(), any(), any()) }
        }
    }

    @Test
    fun `onPrepare won't fetch very distant events`() {

        runBlocking {
            val metadata = listOf(
                // Events that are very distant in time
                createEventMetadata("id_1", startTime = 0L, endTime = 0L),
                createEventMetadata("id_2", startTime = Instant.MAX.epochSecond, endTime = Instant.MAX.epochSecond),
                // This is a valid event
                createEventMetadata("id_3"),
            )

            coEvery { calendarsRepository.shouldFetchEvent(metadata[0]) } returns false
            coEvery { calendarsRepository.shouldFetchEvent(metadata[1]) } returns false
            coEvery { calendarsRepository.shouldFetchEvent(metadata[2]) } returns true

            delegate.onPrepare(config, metadata)

            coVerify(exactly = 1) { calendarsRepository.fetchEventById(any(), any(), any()) }
        }
    }

    @Test
    fun `onCreate persists events`() {
        runBlocking {
            coEvery { calendarsRepository.persistEvents(*anyVararg()) } returns Unit
            val metadata = listOf(createEventMetadata("id_1"))
            // Needed to populate the entity cache
            delegate.onPrepare(config, metadata)

            delegate.onCreate(metadata.map { it.id })

            coVerify(exactly = 1) { calendarsRepository.persistEvents(*anyVararg()) }
        }
    }

    @Test
    fun `onUpdate persists events`() {
        runBlocking {
            coEvery { calendarsRepository.persistEvents(*anyVararg()) } returns Unit
            val metadata = listOf(createEventMetadata("id_1"))
            // Needed to populate the entity cache
            delegate.onPrepare(config, metadata)

            delegate.onUpdate(metadata.map { it.id })

            coVerify(exactly = 1) { calendarsRepository.persistEvents(*anyVararg()) }
        }
    }

    @Test
    fun `onDelete deletes events`() {
        runBlocking {
            coEvery { calendarsRepository.deleteEventsById(any()) } returns Unit

            delegate.onDelete(listOf(eventId))

            coVerify(exactly = 1) { calendarsRepository.deleteEventsById(any()) }
        }
    }

    @Test
    fun `onCompletion does event post-processing when ids are provided`() {
        runBlocking {
            val metadata = listOf(createEventMetadata("id_1"))
            // Needed to populate the entity cache
            delegate.onPrepare(config, metadata)

            delegate.onSuccess(config, metadata.map { it.id })

            //coVerify(exactly = 1) { fetchPublicKeysUseCase.execute(any(), any()) }
            coVerify(exactly = 1) { updateAlarmsUseCase.execute(any(), any()) }
            coVerify(exactly = 1) { widgetRefresher.refreshEventList() }
        }
    }

    @Test
    fun `onCompletion does not do event post-processing when no ids are provided`() {
        runBlocking {
            val metadata = listOf(createEventMetadata("id_1"))
            // Needed to populate the entity cache
            delegate.onPrepare(config, metadata)

            delegate.onSuccess(config, emptyList())

            //coVerify(exactly = 0) { fetchPublicKeysUseCase.execute(any(), any()) }
            coVerify(exactly = 0) { widgetRefresher.refreshEventList() }
        }
    }
}

fun createEventEntity(id: String, modifyTime: Long? = null, paramCalendarId: String? = null, paramSharedKeyPacket: String? = null) = EventEntity(
    id,
    paramCalendarId ?: calendarId,
    sharedEventId,
    calendarKeyPacket,
    0L,
    modifyTime ?: 0L,
    0,
    addressKeyPacket = null,
    addressId = null,
    paramSharedKeyPacket ?: sharedKeyPacket,
    emptyList(),
    emptyList(),
    emptyList(),
    emptyList(),
    emptyList(),
    null
)

fun createEventMetadata(
    id: String,
    startTime: Long? = null,
    endTime: Long? = null,
    modifyTime: Long? = null,
    rRule: String? = null
) = ServerEvent.EventEntityMetadata(
    id,
    calendarId,
    startTime ?: Instant.now().plusSeconds(3600).epochSecond,
    "GMT",
    endTime ?: Instant.now().plusSeconds(7200).epochSecond,
    "GMT",
    0,
    eventUid,
    null,
    emptyList(),
    rRule,
    0L,
    modifyTime ?: 0L,
    0
)

private const val eventsResponse = """
{
    "Code": 1000,
    "CalendarModelEventID": "q-aXxL2ncTtY-zGNxoc-oEfJC5Q577195AE3hx5hYdUtTzgZNjwgIeqY6fE9iiqrraPIFrzNfjJAhH3XFHb-Pg==",
    "Refresh": 0,
    "More": 0,
    "CalendarEvents": [
        {
            "ID": "q6fRrEIn0nyJBE_-YSIiVf80M2VZhOuUHW5In4heCyOdV_nGibV38tK76fPKm7lTHQLcDiZtEblk0t55wbuw4w==",
            "Action": 2,
            "Event": {
                "ID": "q6fRrEIn0nyJBE_-YSIiVf80M2VZhOuUHW5In4heCyOdV_nGibV38tK76fPKm7lTHQLcDiZtEblk0t55wbuw4w==",
                "CalendarID": "q6PiQMq2FU0RnxzC2ly5cRQ7AjB1A-3IvKOmKUT4vwmPxGqJuXUvshsnor2q59pgXUISYBjycLu7WcNj_gt7-A==",
                "SharedEventID": "9WO6jlhdJQbw46gBKjMR6yXpTC-H8LtLsCad65bcKmsg7NCeDSi7O-QlnrBce0T15VkjiVlcNnScJ61RN44BWLq9VbLYBgI0Xg-jripj1lE=",
                "StartTime": 1637933400,
                "StartTimezone": "Europe/Madrid",
                "EndTime": 1637935200,
                "EndTimezone": "Europe/Madrid",
                "FullDay": 0,
                "UID": "nEa_hXjoeJiOgoyBKWzqxPSU-Zda@proton.me",
                "RecurrenceID": null,
                "Exdates": [],
                "RRule": null,
                "CreateTime": 1637683433,
                "ModifyTime": 1637927851,
                "IsOrganizer": 1
            }
        }
    ],
    "CalendarAlarms": [
        {
            "ID": "FV6FNtAc6lgP6R7dM7YB5N3LzHuoBMyivBSqjPcDfIbuMf5MdvXlCmMMcBY57-wdG-hQk4qhFb5lDEJyxjySpQ==",
            "Action": 1,
            "Alarm": {
                "ID": "FV6FNtAc6lgP6R7dM7YB5N3LzHuoBMyivBSqjPcDfIbuMf5MdvXlCmMMcBY57-wdG-hQk4qhFb5lDEJyxjySpQ==",
                "EventID": "q6fRrEIn0nyJBE_-YSIiVf80M2VZhOuUHW5In4heCyOdV_nGibV38tK76fPKm7lTHQLcDiZtEblk0t55wbuw4w==",
                "CalendarID": "q6PiQMq2FU0RnxzC2ly5cRQ7AjB1A-3IvKOmKUT4vwmPxGqJuXUvshsnor2q59pgXUISYBjycLu7WcNj_gt7-A==",
                "MemberID": "q6PiQMq2FU0RnxzC2ly5cRQ7AjB1A-3IvKOmKUT4vwmPxGqJuXUvshsnor2q59pgXUISYBjycLu7WcNj_gt7-A==",
                "Occurrence": 1637932500,
                "Trigger": "-PT15M",
                "Action": 2
            }
        }
    ]
}
"""
