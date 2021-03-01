package me.proton.android.calendar.domain.usecase

import android.util.Log
import assertk.assertThat
import assertk.assertions.isEqualTo
import me.proton.android.calendar.common.TestsLogger
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.api.ServerEventsApi
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import me.proton.android.calendar.data.api.*
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.data.entity.CalendarUserSettingsEntity
import me.proton.android.calendar.data.entity.EventAlarmEntity
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.core.domain.entity.UserId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

// TODO add test for verification when there are different authors for different Event Parts

internal class SyncServerEventsUseCaseTest {

    private val serverEventsApiMock: ServerEventsApi = mockk()
    private val calendarsRepositoryMock: CalendarsRepository = mockk()
    private val usersRepositoryMock: UsersRepository = mockk()
    private val valueStoreMock: ValueStore = mockk()
    private lateinit var appDatabaseMock: AppDatabase
    private val valueStoreProviderMock: ValueStoreProvider = mockk()
    private val cacheCalendarPassphraseUseCaseMock: CacheCalendarPassphraseUseCase = mockk()
    private val calendarUserSettingsChangedUseCaseMock: CalendarUserSettingsChangedUseCase = mockk()
    private val keySetupUseCaseMock: KeySetupUseCase = mockk()
    private val handleEventsMetadataUseCaseMock: HandleEventsMetadataUseCase = mockk()
    private val calendarsApiMock: CalendarsApi = mockk()
    private val handleAlarmsUseCaseMock: HandleAlarmsUseCase = mockk()
    private val updateAlarmsUseCaseMock: UpdateAlarmsUseCase = mockk()
    private val fetchPublicKeysUseCaseMock: FetchPublicKeysUseCase = mockk()
    private val bootstrapCalendarsUseCaseMock: BootstrapCalendarsUseCase = mockk()

    private val userId = UserId("IXFh2TE4LI11sd0GYf94r7fddHNMdZvicfoWMACCjPTS-oNjpBjeclhKlIs6N48-GB5w-zM6uqX_9HFgEnzhYQ==")

    private val testsLogger = TestsLogger
    private val json = Json { this.ignoreUnknownKeys = true }

    @BeforeEach
    fun `before each`() {
        clearAllMocks()
        mockkStatic(Log::class)
        coEvery { Log.isLoggable(any(), any()) } returns true
        appDatabaseMock = mockk()

        coEvery { cacheCalendarPassphraseUseCaseMock.execute(userId, any()) } returns UseCase.Result.Success<Unit>()

        coEvery { calendarsRepositoryMock.persistCalendar(userId.id, any()) } just Runs
        coEvery { calendarsRepositoryMock.updateCalendar(userId.id, any()) } just Runs
        coEvery { calendarsRepositoryMock.persistEvents(any()) } just Runs
        coEvery { calendarsRepositoryMock.persistEvents(any(), captureCoroutine()) } just Runs
        coEvery { calendarsRepositoryMock.deleteEventsById(any()) } just Runs
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
        coEvery { calendarsRepositoryMock.selectCalendarUserSettings(any()) } returns CalendarUserSettingsEntity(1, 1, 1, "Europe/Zurich", 1, null, 1, null)
        coEvery { calendarUserSettingsChangedUseCaseMock.execute(any(), any()) } returns UseCase.Result.Success<Unit>()
        coEvery { handleAlarmsUseCaseMock.execute(any()) } just Runs
        coEvery { keySetupUseCaseMock.execute(any(), any()) } returns UseCase.Result.Success<Unit>()
        coEvery { updateAlarmsUseCaseMock.execute(any(), any()) } just Runs
        coEvery { fetchPublicKeysUseCaseMock.execute(any(), any()) } returns UseCase.Result.Success<Unit>()
        coEvery { bootstrapCalendarsUseCaseMock.executeBootstrap(any(), any(), any()) } returns UseCase.Result.Success<Unit>()

        every { valueStoreProviderMock.provideValueStore(userId.id) } returns valueStoreMock
        every { valueStoreMock.putString(any(), any()) } just Runs
        every { valueStoreMock.putStringInSet(any(), any(), any()) } just Runs
    }

    @Test
    fun `handle chain of core server events`() {
        runBlocking {

            val newCalendarEntity = CalendarEntity(
                "iy-qX4FUDKVvivBrwQI_AEfVEkCu5maUqxoFNzmyak9YkG5Ijsvtv25l3V2BNCDMtdYaMFQuAbQb956KoPDIDA==",
                "Calendar for test",
                "",
                "#C793CA",
                1,
                1,
            )

            every { valueStoreMock.getString(ValueKey.LAST_SERVER_EVENT_ID) } returns "a74ab-bdMtoz8yqIFalPabc6TdsL6pIgdig2CRK9PVcBjM3my8FnZ_JICEqaTRwKnAFdi71swYmFOKaZMECv4Q=="

            // ignore calendar events in this test
            coEvery {
                serverEventsApiMock.getLatestServerCalendarEvent(userId, any())
            } returns ApiResponse.Success(LatestServerCalendarEventApiResponse("latest calendar event ID"))
            coEvery { appDatabaseMock.calendarsDao().selectCalendars(userId.id) } returns emptyList()

            coEvery {
                serverEventsApiMock.getServerCoreEventsSince(userId, "a74ab-bdMtoz8yqIFalPabc6TdsL6pIgdig2CRK9PVcBjM3my8FnZ_JICEqaTRwKnAFdi71swYmFOKaZMECv4Q==")
            } returns ApiResponse.Success(
                json.decodeFromString<ServerCoreEventsApiResponse>(File("src/test/resources/get_server_core_events_1.json").readText()).toServerEventsApiResponse()
            )

            coEvery {
                serverEventsApiMock.getServerCoreEventsSince(userId, "tuSVNffjJ-X7oNoI3o56uToDlTKy6AIvF0XVh9AIz0CN9j60PoE9mZJMSJzqCPpaPBphecjDWUbOrx4-HpuzdQ==")
            } returns ApiResponse.Success(
                json.decodeFromString<ServerCoreEventsApiResponse>(File("src/test/resources/get_server_core_events_2.json").readText()).toServerEventsApiResponse()
            )

            val handleServerEventsUseCase = HandleServerEventsUseCase(testsLogger, calendarsRepositoryMock, usersRepositoryMock, cacheCalendarPassphraseUseCaseMock, handleAlarmsUseCaseMock, updateAlarmsUseCaseMock, handleEventsMetadataUseCaseMock, calendarUserSettingsChangedUseCaseMock, keySetupUseCaseMock, bootstrapCalendarsUseCaseMock, calendarsApiMock, valueStoreProviderMock, serverEventsApiMock)

            val useCase = SyncServerEventsUseCase(
                testsLogger,
                valueStoreProviderMock,
                serverEventsApiMock,
                appDatabaseMock,
                handleServerEventsUseCase
            )

            // TODO handle incomplete key setup case: KeySetupUseCase.kt

            assert(useCase.execute(userId) is UseCase.Result.Success<*>)

            coVerify(exactly = 1) {
                bootstrapCalendarsUseCaseMock.executeBootstrap(newCalendarEntity, userId, "Europe/Zurich")
            }
            coVerify(exactly = 1) {
                calendarsRepositoryMock.persistMember(any())
            }
            coVerify(exactly = 1) {
                calendarsRepositoryMock.updateCalendar(userId.id, any())
            }
            coVerify(exactly = 1) {
                usersRepositoryMock.updateAddress(userId.id, any())
            }
            coVerify(exactly = 1) {
                usersRepositoryMock.persistUserSettings(userId.id, any())
            }
            coVerify(exactly = 1) {
                usersRepositoryMock.updateUser(any())
            }
            coVerify(exactly = 1) {
                calendarUserSettingsChangedUseCaseMock.execute(userId.id, any())
            }
        }
    }

    @Test
    fun `handle chain of calendar server events`() {
        runBlocking {

            val calendarId = "ZusVacxuoQZqP-z0j3eiNZsilZrVLZ7HNOBSJCFEQi6lONDqiCvi0QdF6eOk5HOWVE2esdadMUe1M4569ZNLzA=="
            val lastEventId = "5qoyO4yM2Ytj-vsj6mMjJ7BBAqP53GMa_LHoYfCr7fSutsj6CtJDFFqva-EclKgerkhZ2lZ-Fs3vfRYFKwDQTQ=="

            // ignore core events in this test
            every { valueStoreMock.getString(ValueKey.LAST_SERVER_EVENT_ID) } returns "latest core event ID"
            coEvery {
                serverEventsApiMock.getServerCoreEventsSince(userId, "latest core event ID")
            } returns ApiResponse.Success(
                json.decodeFromString<ServerCoreEventsApiResponse>(File("src/test/resources/get_server_events_empty_result.json").readText()).toServerEventsApiResponse()
            )

            every { valueStoreMock.getStringFromSet(ValueSet.LAST_SERVER_CALENDAR_EVENT_ID, calendarId) } returns lastEventId

            coEvery {
                serverEventsApiMock.getServerCalendarEventsSince(userId, lastEventId, calendarId)
            } returns ApiResponse.Success(
                json.decodeFromString<ServerCalendarEventsApiResponse>(File("src/test/resources/get_server_calendar_events_1.json").readText()).toServerEventsApiResponse()
            )

            val secondEventsId = "yZvpJj3vgcog2h4hMy73NPlMTJJq_wdiiAqxa31sF3UxBUSpM4qMtVDZU6WDFskEVUh2eUlMNJLO6Oqb1wVMSg=="

            coEvery {
                serverEventsApiMock.getServerCalendarEventsSince(userId, secondEventsId, calendarId)
            } returns ApiResponse.Success(
                json.decodeFromString<ServerCalendarEventsApiResponse>(File("src/test/resources/get_server_calendar_events_2.json").readText()).toServerEventsApiResponse()
            )

            coEvery { appDatabaseMock.calendarsDao().selectCalendars(userId.id) } returns listOf(CalendarEntity(calendarId, "", "", "", 0))
            coEvery { calendarsRepositoryMock.selectCalendars(userId.id) } returns listOf(CalendarEntity(calendarId, "", "", "", 0))

            coEvery { calendarsApiMock.getEvent(userId, calendarId, "zhcfa9p3xUcoTmTt2nqYF5nW7BtS93OgtDKkBk9erBfrZXrOmfn-AGcS4p8iu1xcJVrh7_6AVssvpWtFe8VcQw==") } returns ApiResponse.Success(
                EventApiResponse(EventEntity(
                    "event id 1",
                    calendarId,
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

            coEvery { calendarsApiMock.getEvent(userId, calendarId, "KEZDgiuT4MIsCMCH9aPPXJ9NpDNnKRr3-fY1dh8shzeAri-GLWVjb0sexZAc6hpRJUDTV2ljNfapsxEy-47WBQ==") } returns ApiResponse.Success(
                EventApiResponse(EventEntity(
                    "event id 2",
                    calendarId,
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

            val handleEventsMetadataUseCase = HandleEventsMetadataUseCase(
                testsLogger,
                calendarsApiMock,
                updateAlarmsUseCaseMock,
                calendarsRepositoryMock,
                fetchPublicKeysUseCaseMock
            )

            val handleServerEventsUseCase = HandleServerEventsUseCase(testsLogger, calendarsRepositoryMock, usersRepositoryMock, cacheCalendarPassphraseUseCaseMock, handleAlarmsUseCaseMock, updateAlarmsUseCaseMock, handleEventsMetadataUseCase, calendarUserSettingsChangedUseCaseMock, keySetupUseCaseMock, bootstrapCalendarsUseCaseMock, calendarsApiMock, valueStoreProviderMock, serverEventsApiMock)

            val useCase = SyncServerEventsUseCase(
                testsLogger,
                valueStoreProviderMock,
                serverEventsApiMock,
                appDatabaseMock,
                handleServerEventsUseCase
            )

            assert(useCase.execute(userId) is UseCase.Result.Success<*>)

            coVerify(exactly = 1) {
                calendarsRepositoryMock.persistEvents(any(), captureCoroutine())
            }

            coVerify(exactly = 1) {
                calendarsRepositoryMock.deleteEventsById(any())
            }

            coVerify(exactly = 1) {
                fetchPublicKeysUseCaseMock.execute(any(), any())
            }

            coVerify(exactly = 1) {
                calendarsRepositoryMock.persistCalendarSettings(any())
            }

            // 1 for metadata usecase, 1 for created alarm
            coVerify(exactly = 2) {
                updateAlarmsUseCaseMock.execute(userId.id, any())
            }

            coVerify(exactly = 1) {
                calendarsRepositoryMock.persistCalendarKey(any())
            }

            coVerify(exactly = 1) {
                calendarsRepositoryMock.persistPassphrase(any())
            }

        }
    }

}
