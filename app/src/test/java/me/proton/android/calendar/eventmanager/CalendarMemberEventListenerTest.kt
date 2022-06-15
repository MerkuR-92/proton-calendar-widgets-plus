package me.proton.android.calendar.eventmanager

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.MemberEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.eventmanager.listeners.core.CalendarMemberEventListener
import me.proton.android.calendar.mocks.calendarColor
import me.proton.android.calendar.mocks.calendarDisplay
import me.proton.android.calendar.mocks.calendarFlags
import me.proton.android.calendar.mocks.calendarId
import me.proton.android.calendar.mocks.memberId
import me.proton.android.calendar.mocks.userEmail
import me.proton.core.domain.entity.UserId
import me.proton.core.eventmanager.domain.EventManagerConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CalendarMemberEventListenerTest {

    private val db: AppDatabase = mockk()
    private val calendarsRepository: CalendarsRepository = mockk(relaxed = true)
    private val logger: Logger = mockk(relaxed = true)
    lateinit var listener: CalendarMemberEventListener
    private val config = EventManagerConfig.Core(UserId("user_id"))

    @BeforeEach
    fun setup() {
        clearAllMocks()
        listener = CalendarMemberEventListener(db, calendarsRepository, logger)
        coEvery { calendarsRepository.hasCalendar(any()) } returns true
    }

    @Test
    fun `onCreateOrUpdate persists the members if calendar is present`() {
        runBlocking {
            val entities = listOf(MemberEntity(memberId, 0, userEmail, calendarId, calendarColor, calendarDisplay, calendarFlags))

            listener.onCreateOrUpdate(config, entities)

            coVerify(exactly = entities.count()) { calendarsRepository.persistMember(any()) }
        }
    }

    @Test
    fun `onCreateOrUpdate doesn't persist the members if calendar is not present`() {
        runBlocking {
            coEvery { calendarsRepository.hasCalendar(any()) } returns false
            val entities = listOf(MemberEntity(memberId, 0, userEmail, calendarId, calendarColor, calendarDisplay, calendarFlags))

            listener.onCreateOrUpdate(config, entities)

            coVerify(exactly = 0) { calendarsRepository.persistMember(any()) }
            coVerify(exactly = 1) { logger.i(any()) }
        }
    }

    @Test
    fun `onDelete removes the members`() {
        runBlocking {
            val ids = listOf(memberId)

            listener.onDelete(config, ids)

            coVerify(exactly = ids.count()) { calendarsRepository.deleteMemberById(any()) }
        }
    }

}
