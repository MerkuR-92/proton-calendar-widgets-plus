package me.proton.android.calendar.eventmanager

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.PassphraseEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.CacheCalendarPassphraseUseCase
import me.proton.android.calendar.eventmanager.listeners.calendar.CalendarPassphraseEventListener
import me.proton.android.calendar.mocks.calendarId
import me.proton.core.domain.entity.UserId
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.eventmanager.domain.entity.Action
import me.proton.core.eventmanager.domain.entity.Event
import me.proton.core.eventmanager.domain.extension.groupByAction
import org.junit.Ignore
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CalendarPassphraseEventListenerTest {

    private val db: AppDatabase = mockk()
    private val calendarsRepository: CalendarsRepository = mockk(relaxed = true)
    private val cacheCalendarPassphraseUseCase: CacheCalendarPassphraseUseCase = mockk(relaxed = true)
    private val logger: Logger = mockk(relaxed = true)
    private lateinit var listener: CalendarPassphraseEventListener
    private val config = EventManagerConfig.Calendar(UserId("user_id"), calendarId)

    @BeforeEach
    fun setup() {
        clearAllMocks()
        listener = spyk(CalendarPassphraseEventListener(db, calendarsRepository, cacheCalendarPassphraseUseCase, logger))
        coEvery { calendarsRepository.hasCalendar(any()) } returns true
    }

    @Test
    fun `onCreateOrUpdate persists the passphrases if calendar exists`() {
        runBlocking {
            val entities = listOf(
                PassphraseEntity("passphrase_id", 0, emptyList(), calendarId),
                PassphraseEntity("passphrase_id_2", 0, emptyList(), calendarId)
            )

            listener.onCreateOrUpdate(config, entities)

            coVerify(exactly = entities.count()) { calendarsRepository.persistPassphrase(any()) }
        }
    }

    @Test
    fun `onCreateOrUpdate doesn't persist the passphrases if calendar doesn't exist`() {
        runBlocking {
            coEvery { calendarsRepository.hasCalendar(any()) } returns false
            val entities = listOf(
                PassphraseEntity("passphrase_id", 0, emptyList(), calendarId),
                PassphraseEntity("passphrase_id_2", 0, emptyList(), calendarId)
            )

            listener.onCreateOrUpdate(config, entities)

            coVerify(exactly = 0) { calendarsRepository.persistPassphrase(any()) }
            coVerify { logger.i(any()) }
        }
    }

    @Test
    fun `onUpdate persists the passphrases`() {
        runBlocking {
            val entities = listOf(
                PassphraseEntity("passphrase_id", 0, emptyList(), calendarId),
                PassphraseEntity("passphrase_id_2", 0, emptyList(), calendarId)
            )

            listener.onUpdate(config, entities)

            coVerify(exactly = entities.count()) { calendarsRepository.persistPassphrase(any()) }
        }
    }

    @Test
    fun `onDelete removes the passphrases`() {
        runBlocking {
            val entities = listOf("passphrase_id", "passphrase_id_2")

            listener.onDelete(config, entities)

            coVerify(exactly = entities.count()) { calendarsRepository.deletePassphraseById(any()) }
        }
    }

    @Ignore("You cannot mock listener here. You should use listener.notifyComplete(...)")
    fun `onComplete caches the created or update passphrases if present`() {
        runBlocking {
            val entities = listOf(
                PassphraseEntity("passphrase_id", 0, emptyList(), calendarId),
                PassphraseEntity("passphrase_id_2", 0, emptyList(), calendarId)
            )
            coEvery { listener.getActionMap(any()) } returns entities.map { Event(Action.Create, it.id, it) }.groupByAction()

            listener.onComplete(config)

            coVerify(exactly = 1) { cacheCalendarPassphraseUseCase.execute(any(), any()) }
        }
    }

}
