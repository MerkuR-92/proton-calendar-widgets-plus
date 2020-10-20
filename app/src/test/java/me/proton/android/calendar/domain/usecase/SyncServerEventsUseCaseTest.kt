package me.proton.android.calendar.domain.usecase

import assertk.assertThat
import assertk.assertions.isEqualTo
import me.proton.android.calendar.common.GsonCommon
import me.proton.android.calendar.common.TestsLogger
import me.proton.android.calendar.common.readLocalResourceTestFile
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.ServerEventsApiResponse
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.api.ServerEventsApi
import io.mockk.*
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.data.api.EventApiResponse
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.domain.api.CalendarsApi
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

// TODO add test for verification when there are different authors for different Event Parts

internal class SyncServerEventsUseCaseTest {

    private val serverEventsApiMock: ServerEventsApi = mockk()
    private val calendarsRepositoryMock: CalendarsRepository = mockk()
    private val usersRepositoryMock: UsersRepository = mockk()
    private val valueStoreMock: ValueStore = mockk()
    private val valueStoreProviderMock: ValueStoreProvider = mockk()
    private val cacheCalendarPassphraseUseCaseMock: CacheCalendarPassphraseUseCase = mockk()
    private val fetchPublicKeysUseCaseMock: FetchPublicKeysUseCase = mockk()
    private val calendarsApi: CalendarsApi = mockk()
    private val handleAlarmsUseCaseMock: HandleAlarmsUseCase = mockk()

    private val userId = "IXFh2TE4LI11sd0GYf94r7fddHNMdZvicfoWMACCjPTS-oNjpBjeclhKlIs6N48-GB5w-zM6uqX_9HFgEnzhYQ=="

    private val gson = GsonCommon.gson
    private val testsLogger = TestsLogger

    @BeforeEach
    fun `before each`() {
        clearAllMocks()
    }

    @Test
    fun `handle chain of proton events`() {
        runBlocking {
            every { valueStoreProviderMock.provideValueStore(userId) } returns valueStoreMock

            every { valueStoreMock.getString(ValueKey.LAST_SERVER_EVENT_ID) } returns "l_o7TdJpH3UCfYn-0xBWaJVlZ633baHNvjyZFvuX7TD6lFPaOzy-3YkSorWafW5nKivpxfZ1YaU66_d25R58-Q=="
            every { valueStoreMock.putString(any(), any()) } just Runs

            // TODO "CalendarEvents" list looks differently now, it contains no shared/calendar/personal-Events and we need to fetch them
            //  separately, by calling CalendarsApi

            coEvery {
                serverEventsApiMock.getServerEvents("l_o7TdJpH3UCfYn-0xBWaJVlZ633baHNvjyZFvuX7TD6lFPaOzy-3YkSorWafW5nKivpxfZ1YaU66_d25R58-Q==")
            } returns ApiResponse.Success(
                gson.readLocalResourceTestFile(
                    "get_server_events_1.json",
                    ServerEventsApiResponse::class.java
                )
            )

            coEvery {
                serverEventsApiMock.getServerEvents("KiQ9JV0gXPgyz5wA0T8dFOzHIzD-fFVLEnog3En_ySlO3JjwlsPOn7zJHbbqkUh81kYofB14MuhrbCtMXNSEDQ==")
            } returns ApiResponse.Success(
                gson.readLocalResourceTestFile(
                    "get_server_events_2.json",
                    ServerEventsApiResponse::class.java
                )
            )

            coEvery {
                serverEventsApiMock.getServerEvents("deZCqTHUuob80ZGgsyTwXxhJka4LD0soPisUvyeTcXKej5UnbTqI_0Qp3bUZfNjcI1gVZs2tZqqcnHCj5zXVYQ==")
            } returns ApiResponse.Success(
                gson.readLocalResourceTestFile(
                    "get_server_events_3_after_creating_calendar.json",
                    ServerEventsApiResponse::class.java
                )
            )

            coEvery {
                serverEventsApiMock.getServerEvents("OYludcH6yhRe9X4Hgsxa9yeEOD-yMAsQVzehHyOxOgEf2owoLdVmWD1v_yzq_WuPWf3CX0fqpmIpqg-7PYxhIg==")
            } returns ApiResponse.Success(
                gson.readLocalResourceTestFile(
                    "get_server_events_4.json",
                    ServerEventsApiResponse::class.java
                )
            )

            coEvery { cacheCalendarPassphraseUseCaseMock.execute(userId, any()) } returns UseCase.Result.Success

            coEvery { calendarsRepositoryMock.persistCalendar(userId, any()) } just Runs
            coEvery { calendarsRepositoryMock.updateCalendar(userId, any()) } just Runs
            coEvery { calendarsRepositoryMock.persistEvents(any()) } just Runs
            coEvery { calendarsRepositoryMock.refreshCalendarsFlagsForAddress(any(), any(), any()) } just Runs
            coEvery { usersRepositoryMock.persistAddress(userId, any()) } just Runs
            coEvery { usersRepositoryMock.persistUser(any()) } just Runs
            coEvery { usersRepositoryMock.updateUser(any()) } just Runs
            coEvery { usersRepositoryMock.persistUserSettings(any(), any()) } just Runs
            coEvery { usersRepositoryMock.updateAddress(any(), any()) } just Runs
            coEvery { calendarsRepositoryMock.persistEventAlarm(any()) } just Runs
            coEvery { calendarsRepositoryMock.deleteEventAlarmById(any()) } just Runs
            coEvery { calendarsRepositoryMock.persistCalendarKey(any()) } just Runs
            coEvery { calendarsRepositoryMock.persistMember(any()) } just Runs
            coEvery { calendarsRepositoryMock.persistPassphrase(any()) } just Runs
            coEvery { calendarsRepositoryMock.persistCalendarSettings(any()) } just Runs
            coEvery { calendarsRepositoryMock.isCalendarDisplayUpToDate(any(), any()) } returns true
            coEvery { fetchPublicKeysUseCaseMock.execute(any()) } returns UseCase.Result.Success
            coEvery { handleAlarmsUseCaseMock.execute(any()) } just Runs
            coEvery { calendarsApi.getEvent(any(), any()) } returns ApiResponse.Success(
                EventApiResponse(1000, EventEntity(
                    "id",
                    "calendarId",
                    "calendarKeyPacket",
                    0L,
                    0L,
                    0,
                    "sharedKeyPacket",
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    emptyList()))
            )

            val handleProtonEventsUseCase = HandleServerEventsUseCase(testsLogger, calendarsRepositoryMock, usersRepositoryMock, cacheCalendarPassphraseUseCaseMock, handleAlarmsUseCaseMock, fetchPublicKeysUseCaseMock, calendarsApi)

            val useCase = SyncServerEventsUseCase(
                testsLogger,
                valueStoreProviderMock,
                serverEventsApiMock,
                handleProtonEventsUseCase
            )

            assertThat(useCase.execute(userId)).isEqualTo(UseCase.Result.Success)

            coVerify(exactly = 1) {
                cacheCalendarPassphraseUseCaseMock.execute(userId, any())
            }

            verify {
                valueStoreMock.getString(ValueKey.LAST_SERVER_EVENT_ID)
            }
            coVerify(exactly = 1) {
                calendarsRepositoryMock.persistCalendar(userId, any())
            }
            coVerify(exactly = 2) {
                calendarsRepositoryMock.updateCalendar(userId, any())
            }
            coVerify(exactly = 6) {
                calendarsRepositoryMock.persistEvents(any())
            }
            coVerify(exactly = 2) {
                usersRepositoryMock.updateAddress(userId, any())
            }
            coVerify(exactly = 1) {
                usersRepositoryMock.persistUserSettings(userId, any())
            }
            coVerify(exactly = 1) {
                usersRepositoryMock.updateUser(any())
            }
            coVerify(exactly = 5) {
                calendarsRepositoryMock.persistEventAlarm(any())
            }
            coVerify(exactly = 3) {
                calendarsRepositoryMock.deleteEventAlarmById(any())
            }
            coVerify(exactly = 1) {
                calendarsRepositoryMock.persistCalendarKey(any())
            }
            coVerify(exactly = 1) {
                calendarsRepositoryMock.persistMember(any())
            }
            coVerify(exactly = 1) {
                calendarsRepositoryMock.persistPassphrase(any())
            }
            coVerify(exactly = 2) {
                calendarsRepositoryMock.persistCalendarSettings(any())
            }
            coVerify(atLeast = 1) {
                fetchPublicKeysUseCaseMock.execute("adamtst@protonmail.blue")
            }
        }
    }

    @Disabled
    @Test
    fun `handle chain of proton events with network error`() {

        // TODO
        TODO()

    }


}
