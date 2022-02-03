package me.proton.android.calendar.eventmanager

import io.mockk.clearAllMocks
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.CalendarUserSettingsEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.usecase.CalendarUserSettingsChangedUseCase
import me.proton.android.calendar.eventmanager.listeners.core.CalendarUserSettingsEventListener
import me.proton.android.calendar.mocks.calendarId
import me.proton.android.calendar.mocks.defaultTimezone
import me.proton.android.calendar.mocks.userId
import me.proton.core.domain.entity.UserId
import me.proton.core.eventmanager.domain.EventManagerConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CalendarUserSettingsEventListenerTest {

    private val db: AppDatabase = mockk()
    private val calendarsUserSettingsChangedUseCase: CalendarUserSettingsChangedUseCase = mockk(relaxed = true)
    lateinit var listener: CalendarUserSettingsEventListener
    private val config = EventManagerConfig.Calendar(UserId("user_id"), calendarId)

    @BeforeEach
    fun setup() {
        clearAllMocks()
        listener = CalendarUserSettingsEventListener(db, calendarsUserSettingsChangedUseCase)
    }

    @Test
    fun `onUpdate persists the user settings`() {
        runBlocking {
            val entities = listOf(
                CalendarUserSettingsEntity(
                    userId.id,
                    0,
                    0,
                    0,
                    defaultTimezone,
                    0,
                    null,
                    0,
                    null
                )
            )

            listener.onUpdate(config, entities)

            coVerify { calendarsUserSettingsChangedUseCase.execute(config.userId.id, any()) }
        }
    }

}
