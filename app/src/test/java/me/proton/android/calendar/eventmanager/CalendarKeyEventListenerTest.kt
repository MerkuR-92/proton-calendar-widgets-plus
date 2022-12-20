package me.proton.android.calendar.eventmanager

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.CalendarKeyEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.eventmanager.listeners.calendar.CalendarKeyEventListener
import me.proton.android.calendar.mocks.calendarId
import me.proton.android.calendar.mocks.eventId
import me.proton.core.domain.entity.UserId
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.eventmanager.domain.entity.Action
import me.proton.core.eventmanager.domain.entity.Event
import me.proton.core.eventmanager.domain.extension.groupByAction
import org.junit.Ignore
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CalendarKeyEventListenerTest {

    private val db: AppDatabase = mockk()
    private val calendarsRepository: CalendarsRepository = mockk(relaxed = true)
    private val logger: Logger = mockk(relaxed = true)
    private lateinit var listener: CalendarKeyEventListener
    private val config = EventManagerConfig.Calendar(UserId("user_id"), calendarId)

    @BeforeEach
    fun setup() {
        clearAllMocks()
        listener = CalendarKeyEventListener(db, calendarsRepository, logger)
        coEvery { calendarsRepository.hasCalendar(any()) } returns true
    }

    @Test
    fun `onCreateOrUpdate persists the keys if calendar is present`() {
        runBlocking {
            val entities = listOf(
                CalendarKeyEntity("key_id", 0, "", "", calendarId),
                CalendarKeyEntity("key_id_2", 0, "", "", calendarId),
            )

            listener.onCreateOrUpdate(config, entities)

            coVerify(exactly = entities.count()) { calendarsRepository.persistCalendarKey(any()) }
        }
    }

    @Test
    fun `onCreateOrUpdate doesn't persist the keys if calendar is not present`() {
        coEvery { calendarsRepository.hasCalendar(any()) } returns false
        runBlocking {
            val entities = listOf(
                CalendarKeyEntity("key_id", 0, "", "", calendarId),
                CalendarKeyEntity("key_id_2", 0, "", "", calendarId),
            )

            listener.onCreateOrUpdate(config, entities)

            coVerify(exactly = 0) { calendarsRepository.persistCalendarKey(any()) }
            coVerify { logger.i(any()) }
        }
    }

    @Test
    fun `onDelete removes the keys`() {
        runBlocking {
            val ids = listOf("key_id", "key_id_2")

            listener.onDelete(config, ids)

            coVerify(exactly = ids.count()) { calendarsRepository.deleteCalendarKeyById(any()) }
        }
    }

    @Ignore("You cannot mock listener here. You should use listener.notifySuccess(...)")
    fun `onSuccess calls refreshCalendarsFlags`() {
        runBlocking {
            val entity = CalendarKeyEntity("key_id", 0, "", "", calendarId)

            coEvery { listener.getActionMap(any()) } returns listOf(
                Event(Action.Create, "id", entity)
            ).groupByAction()

            listener.onSuccess(config)

            coVerify { calendarsRepository.refreshCalendarsFlags(any()) }
        }
    }

}
