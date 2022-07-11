package me.proton.android.calendar.eventmanager

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.CalendarSettingsEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.CalendarSettingsChangedUseCase
import me.proton.android.calendar.eventmanager.listeners.calendar.CalendarSettingsEventListener
import me.proton.android.calendar.mocks.calendarId
import me.proton.android.calendar.mocks.calendarSettingsId
import me.proton.android.calendar.mocks.defaultEventDuration
import me.proton.core.domain.entity.UserId
import me.proton.core.eventmanager.domain.EventManagerConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CalendarSettingsEventListenerTest {

    private val db: AppDatabase = mockk()
    private val calendarsRepository: CalendarsRepository = mockk(relaxed = true)
    private val calendarSettingsChangedUseCase: CalendarSettingsChangedUseCase = mockk(relaxed = true)
    private val logger: Logger = mockk(relaxed = true)
    private lateinit var listener: CalendarSettingsEventListener
    private val config = EventManagerConfig.Calendar(UserId("user_id"), calendarId)

    @BeforeEach
    fun setup() {
        clearAllMocks()
        listener = CalendarSettingsEventListener(db, calendarsRepository, logger, calendarSettingsChangedUseCase)
        coEvery { calendarsRepository.hasCalendar(any()) } returns true
    }

    @Test
    fun `onCreateOrUpdate persists the calendar settings if calendar is present`() {
        runBlocking {
            val entities = listOf(
                CalendarSettingsEntity(calendarSettingsId, calendarId, defaultEventDuration, emptyList(), emptyList()),
                CalendarSettingsEntity(calendarSettingsId, calendarId, defaultEventDuration, emptyList(), emptyList()),
            )

            listener.onCreateOrUpdate(config, entities)

            coVerify(exactly = entities.count()) { calendarsRepository.persistCalendarSettings(any()) }
        }
    }

    @Test
    fun `onCreateOrUpdate doesn't persist the calendar settings if calendar is not present`() {
        runBlocking {
            coEvery { calendarsRepository.hasCalendar(any()) } returns false
            val entities = listOf(
                CalendarSettingsEntity(calendarSettingsId, calendarId, defaultEventDuration, emptyList(), emptyList()),
                CalendarSettingsEntity(calendarSettingsId, calendarId, defaultEventDuration, emptyList(), emptyList()),
            )

            listener.onCreateOrUpdate(config, entities)

            coVerify(exactly = 0) { calendarsRepository.persistCalendarSettings(any()) }
            coVerify { logger.i(any()) }
        }
    }

}
