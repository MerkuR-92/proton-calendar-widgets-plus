package me.proton.android.calendar.eventmanager

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.MemberEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.KeySetupUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.eventmanager.listeners.core.CalendarMemberEventListener
import me.proton.android.calendar.test.shared.mocks.CalendarMocks.provideMemberEntity
import me.proton.android.calendar.test.shared.mocks.calendarColor
import me.proton.android.calendar.test.shared.mocks.calendarDescription
import me.proton.android.calendar.test.shared.mocks.calendarDisplay
import me.proton.android.calendar.test.shared.mocks.calendarFlags
import me.proton.android.calendar.test.shared.mocks.calendarId
import me.proton.android.calendar.test.shared.mocks.calendarName
import me.proton.android.calendar.test.shared.mocks.memberId
import me.proton.android.calendar.test.shared.mocks.userEmail
import me.proton.android.calendar.test.shared.mocks.addressId
import me.proton.core.domain.entity.UserId
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.eventmanager.domain.entity.Action
import me.proton.core.eventmanager.domain.entity.Event
import me.proton.core.user.domain.UserManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CalendarMemberEventListenerTest {

    private val db: AppDatabase = mockk()
    private val calendarsRepository: CalendarsRepository = mockk(relaxed = true)
    private val logger: Logger = mockk(relaxed = true)
    private val keySetupUseCase: KeySetupUseCase = mockk(relaxed = true)
    private val userManager: UserManager = mockk()
    lateinit var listener: CalendarMemberEventListener
    private val config = EventManagerConfig.Core(UserId("user_id"))

    @BeforeEach
    fun setup() {
        clearAllMocks()
        listener = CalendarMemberEventListener(db, calendarsRepository, logger, keySetupUseCase, userManager)
        coEvery { calendarsRepository.hasCalendar(any()) } returns true
    }

    @Test
    fun `onCreateOrUpdate persists the members if calendar is present`() {
        runBlocking {
            val entities = listOf(MemberEntity(memberId, 0, addressId.id, userEmail, calendarId, calendarColor, calendarDisplay, calendarFlags, calendarName, calendarDescription))

            listener.onCreateOrUpdate(config, entities)

            coVerify(exactly = entities.count()) { calendarsRepository.persistMember(any()) }
        }
    }

    @Test
    fun `onCreateOrUpdate doesn't persist the members if calendar is not present`() {
        runBlocking {
            coEvery { calendarsRepository.hasCalendar(any()) } returns false
            val entities = listOf(MemberEntity(memberId, 0, addressId.id, userEmail, calendarId, calendarColor, calendarDisplay, calendarFlags, calendarName, calendarDescription))

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

    @Test
    fun `handleIncompleteKeys executes key setup calendars needing it`() {
        runBlocking {
            val entities = listOf(
                provideMemberEntity(flags = MemberEntity.CalendarFlags.INCOMPLETE_SETUP.value),
                provideMemberEntity(flags = MemberEntity.CalendarFlags.ACTIVE.value),
                provideMemberEntity(flags = MemberEntity.CalendarFlags.ACTIVE.value),
            )
            val events = entities.map { Event(Action.Create, "id", it) }
            coEvery { keySetupUseCase.execute(any(), any()) } returns UseCase.Result.Success(Unit)
            coEvery { calendarsRepository.fetchMembers(any(), any()) } returns listOf(entities.first())

            listener.handleIncompleteKeys(config, events)

            coVerify(exactly = 1) { keySetupUseCase.execute(any(), any()) }
        }
    }

    @Test
    fun `handleIncompleteKeys will remove the incomplete flag from a calendar and makes it active if it can't be fetched`() {
        runBlocking {
            val entities = listOf(
                provideMemberEntity(flags = MemberEntity.CalendarFlags.INCOMPLETE_SETUP.value),
                provideMemberEntity(flags = MemberEntity.CalendarFlags.ACTIVE.value),
                provideMemberEntity(flags = MemberEntity.CalendarFlags.ACTIVE.value),
            )
            val events = entities.map { Event(Action.Create, "id", it) }
            coEvery { keySetupUseCase.execute(any(), any()) } returns UseCase.Result.Success(Unit)
            coEvery { calendarsRepository.fetchMembers(any(), any()) } returns null

            listener.handleIncompleteKeys(config, events)

            coVerify(exactly = 1) { keySetupUseCase.execute(any(), any()) }
            val createEvents = listener.getActionMap(config)[Action.Create].orEmpty()
            assertThat(createEvents.filter { it.entity?.hasIncompleteKeySetup == true }.count()).isEqualTo(0)
        }
    }

}
