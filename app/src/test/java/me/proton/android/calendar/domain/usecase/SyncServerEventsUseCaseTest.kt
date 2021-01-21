package me.proton.android.calendar.domain.usecase

import assertk.assertThat
import assertk.assertions.isEqualTo
import me.proton.android.calendar.common.TestsLogger
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.ServerEventsApiResponse
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.api.ServerEventsApi
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import me.proton.android.calendar.data.api.EventApiResponse
import me.proton.android.calendar.data.entity.EventAlarmEntity
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.core.domain.entity.UserId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.File

// TODO add test for verification when there are different authors for different Event Parts

internal class SyncServerEventsUseCaseTest {

    private val serverEventsApiMock: ServerEventsApi = mockk()
    private val calendarsRepositoryMock: CalendarsRepository = mockk()
    private val usersRepositoryMock: UsersRepository = mockk()
    private val valueStoreMock: ValueStore = mockk()
    private val valueStoreProviderMock: ValueStoreProvider = mockk()
    private val cacheCalendarPassphraseUseCaseMock: CacheCalendarPassphraseUseCase = mockk()
    private val calendarUserSettingsChangedUseCaseMock: CalendarUserSettingsChangedUseCase = mockk()
    private val keySetupUseCaseMock: KeySetupUseCase = mockk()
    private val handleEventsMetadataUseCase: HandleEventsMetadataUseCase = mockk()
    private val calendarsApi: CalendarsApi = mockk()
    private val handleAlarmsUseCaseMock: HandleAlarmsUseCase = mockk()
    private val updateAlarmsUseCaseMock: UpdateAlarmsUseCase = mockk()

    private val userId = UserId("IXFh2TE4LI11sd0GYf94r7fddHNMdZvicfoWMACCjPTS-oNjpBjeclhKlIs6N48-GB5w-zM6uqX_9HFgEnzhYQ==")

    private val testsLogger = TestsLogger
    private val json = Json { this.ignoreUnknownKeys = true }

    @BeforeEach
    fun `before each`() {
        clearAllMocks()
    }

    @Test
    fun `handle chain of proton events`() {
        runBlocking {
            every { valueStoreProviderMock.provideValueStore(userId.id) } returns valueStoreMock

            every { valueStoreMock.getString(ValueKey.LAST_SERVER_EVENT_ID) } returns "l_o7TdJpH3UCfYn-0xBWaJVlZ633baHNvjyZFvuX7TD6lFPaOzy-3YkSorWafW5nKivpxfZ1YaU66_d25R58-Q=="
            every { valueStoreMock.putString(any(), any()) } just Runs

            // TODO "CalendarEvents" list looks differently now, it contains no shared/calendar/personal-Events and we need to fetch them
            //  separately, by calling CalendarsApi

            coEvery {
                serverEventsApiMock.getServerEvents(userId, "l_o7TdJpH3UCfYn-0xBWaJVlZ633baHNvjyZFvuX7TD6lFPaOzy-3YkSorWafW5nKivpxfZ1YaU66_d25R58-Q==")
            } returns ApiResponse.Success(
                json.decodeFromString<ServerEventsApiResponse>(File("src/test/resources/get_server_events_1.json").readText())
            )

            coEvery {
                serverEventsApiMock.getServerEvents(userId, "KiQ9JV0gXPgyz5wA0T8dFOzHIzD-fFVLEnog3En_ySlO3JjwlsPOn7zJHbbqkUh81kYofB14MuhrbCtMXNSEDQ==")
            } returns ApiResponse.Success(
                json.decodeFromString<ServerEventsApiResponse>(File("src/test/resources/get_server_events_2.json").readText())
            )

            coEvery {
                serverEventsApiMock.getServerEvents(userId, "deZCqTHUuob80ZGgsyTwXxhJka4LD0soPisUvyeTcXKej5UnbTqI_0Qp3bUZfNjcI1gVZs2tZqqcnHCj5zXVYQ==")
            } returns ApiResponse.Success(
                json.decodeFromString<ServerEventsApiResponse>(File("src/test/resources/get_server_events_3_after_creating_calendar.json").readText())
            )

            coEvery {
                serverEventsApiMock.getServerEvents(userId, "OYludcH6yhRe9X4Hgsxa9yeEOD-yMAsQVzehHyOxOgEf2owoLdVmWD1v_yzq_WuPWf3CX0fqpmIpqg-7PYxhIg==")
            } returns ApiResponse.Success(
                json.decodeFromString<ServerEventsApiResponse>(File("src/test/resources/get_server_events_4.json").readText())
            )

            coEvery { cacheCalendarPassphraseUseCaseMock.execute(userId, any()) } returns UseCase.Result.Success

            coEvery { calendarsRepositoryMock.persistCalendar(userId.id, any()) } just Runs
            coEvery { calendarsRepositoryMock.updateCalendar(userId.id, any()) } just Runs
            coEvery { calendarsRepositoryMock.persistEvents(any()) } just Runs
            coEvery { calendarsRepositoryMock.selectEventAlarm(any()) } returns EventAlarmEntity("", 0L, "", 2, "", "", "")
            coEvery { calendarsRepositoryMock.deleteEventAlarmsByEventIdAndOccurrence(any(), any()) } just Runs
            coEvery { calendarsRepositoryMock.hasEvent(any(), any()) } returns true
            coEvery { calendarsRepositoryMock.refreshCalendarsFlagsForAddress(any(), any(), any()) } just Runs
            coEvery { calendarsRepositoryMock.refreshCalendars(any()) } returns true
            coEvery { usersRepositoryMock.hasReactivatedAddressKeys(any()) } returns true
            coEvery { usersRepositoryMock.persistAddress(userId.id, any()) } just Runs
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
            coEvery { calendarUserSettingsChangedUseCaseMock.execute(any(), any()) } returns UseCase.Result.Success
            coEvery { handleEventsMetadataUseCase.execute(any(), any()) } returns UseCase.Result.Success
            coEvery { handleAlarmsUseCaseMock.execute(any()) } just Runs
            coEvery { keySetupUseCaseMock.execute(any(), any()) } returns UseCase.Result.Success
            coEvery { updateAlarmsUseCaseMock.execute(any(), any()) } just Runs
            coEvery { calendarsApi.getEvent(any(), any(), any()) } returns ApiResponse.Success(
                EventApiResponse(EventEntity(
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

            val handleProtonEventsUseCase = HandleServerEventsUseCase(testsLogger, calendarsRepositoryMock, usersRepositoryMock, cacheCalendarPassphraseUseCaseMock, handleAlarmsUseCaseMock, updateAlarmsUseCaseMock, handleEventsMetadataUseCase, calendarUserSettingsChangedUseCaseMock, keySetupUseCaseMock, calendarsApi)

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
                calendarsRepositoryMock.persistCalendar(userId.id, any())
            }
            coVerify(exactly = 2) {
                calendarsRepositoryMock.updateCalendar(userId.id, any())
            }
            coVerify(exactly = 2) {
                handleEventsMetadataUseCase.execute(any(), any())
            }
            coVerify(exactly = 5) {
                updateAlarmsUseCaseMock.execute(userId.id, any())
            }
            coVerify(exactly = 2) {
                usersRepositoryMock.updateAddress(userId.id, any())
            }
            coVerify(exactly = 1) {
                usersRepositoryMock.persistUserSettings(userId.id, any())
            }
            coVerify(exactly = 1) {
                usersRepositoryMock.updateUser(any())
            }
            coVerify(exactly = 5) {
                calendarsRepositoryMock.persistEventAlarm(any())
            }
            coVerify(exactly = 3) {
                calendarsRepositoryMock.deleteEventAlarmsByEventIdAndOccurrence(any(), any())
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
        }
    }

    @Disabled
    @Test
    fun `handle chain of proton events with network error`() {

        // TODO
        TODO()

    }


}
