package me.proton.android.calendar.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.common.API_VERSION_CALENDAR
import me.proton.android.calendar.data.entity.*
import me.proton.android.calendar.domain.api.*
import me.proton.android.calendar.domain.model.Event
import me.proton.core.domain.entity.UserId
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.data.protonApi.BaseRetrofitApi
import retrofit2.http.*
import java.time.Instant

interface CalendarsApiService : BaseRetrofitApi {

    @GET("calendar/$API_VERSION_CALENDAR")
    suspend fun getCalendars(@Query("Page") page: Int = 0, @Query("PageSize") pageSize: Int = 100): CalendarsApiResponse

    @GET("calendar/$API_VERSION_CALENDAR/{calendarId}")
    suspend fun getCalendar(@Path("calendarId") calendarId: String): CalendarApiResponse

    @GET("calendar/$API_VERSION_CALENDAR/{calendarId}/events")
    suspend fun getEvents(
        @Path("calendarId") calendarId: String,
        @Query("Start") startTimestamp: Long,
        @Query("End") endTimestamp: Long,
        @Query("Timezone") timezone: String,
        @Query("Type") type: Int,
        @Query("Page") page: Int,
        @Query("PageSize") pageSize: Int
    ): EventsApiResponse

    @GET("calendar/$API_VERSION_CALENDAR/{calendarId}/events/{eventId}")
    suspend fun getEvent(@Path("calendarId") calendarId: String, @Path("eventId") eventId: String) : EventApiResponse

    @GET("calendar/$API_VERSION_CALENDAR/{calendarId}/bootstrap")
    suspend fun getBootstrap(@Path("calendarId") calendarId: String): BootstrapApiResponse

    @GET("calendar/$API_VERSION_CALENDAR/{calendarId}/alarms")
    suspend fun getAlarms(@Path("calendarId") calendarId: String, @Query("Start") startTimestamp: Long, @Query("End") endTimestamp: Long, @Query("PageSize") pageSize: Int) : AlarmsApiResponse

    @GET("calendar/$API_VERSION_CALENDAR/{calendarId}/events/{eventId}/alarms")
    suspend fun getEventAlarms(@Path("calendarId") calendarId: String, @Path("eventId") eventId: String) : AlarmsApiResponse

    @DELETE("calendar/$API_VERSION_CALENDAR/{calendarId}/events/{eventId}")
    suspend fun deleteEvent(@Path("calendarId") calendarId: String, @Path("eventId") eventId: String) : DeleteEventApiResponse

    @PUT("calendar/$API_VERSION_CALENDAR/{calendarId}/events/sync")
    suspend fun syncEvents(@Path("calendarId") calendarId: String, @Body body: SyncEventsUpdateApiRequest) : SyncEventsApiResponse

    @GET("calendar/$API_VERSION_CALENDAR/events")
    suspend fun getEventsByUid(@Query("UID") eventUid: String, @Query("Page") page: Int, @Query("PageSize") pageSize: Int) : EventsByUidApiResponse

    @PUT("calendar/$API_VERSION_CALENDAR/{calendarId}")
    suspend fun updateCalendar(@Path("calendarId") calendarId: String, @Body body: UpdateCalendarApiRequest) : CalendarApiResponse

    @PUT("calendar/$API_VERSION_CALENDAR/{calendarId}")
    suspend fun updateCalendarDisplay(@Path("calendarId") calendarId: String, @Body body: UpdateCalendarDisplayApiRequest) : CalendarApiResponse

    @POST("calendar/$API_VERSION_CALENDAR")
    suspend fun createCalendar(@Body body: CreateCalendarApiRequest) : CalendarApiResponse

    @GET("calendar/$API_VERSION_CALENDAR/{calendarId}/members")
    suspend fun getMemberList(@Path("calendarId") calendarId: String) : MemberListApiResponse

    @POST("calendar/$API_VERSION_CALENDAR/{calendarId}/keys")
    suspend fun setupKey(@Path("calendarId") calendarId: String, @Body body: SetupKeyApiRequest) : SetupKeyApiResponse

    @GET("calendar/$API_VERSION_CALENDAR/{calendarId}/keys/all")
    suspend fun getKeys(@Path("calendarId") calendarId: String) : KeysApiResponse

    @GET("calendar/$API_VERSION_CALENDAR/keys/reset")
    suspend fun getResetInfo() : ResetInfoApiResponse

    @POST("calendar/$API_VERSION_CALENDAR/keys/reset")
    suspend fun resetCalendar(@Body body: ResetCalendarApiRequest) : ResetCalendarApiResponse

    @GET("calendar/$API_VERSION_CALENDAR/{calendarId}/passphrases")
    suspend fun getPassphrases(@Path("calendarId") calendarId: String) : PassphrasesApiResponse

    @PUT("calendar/$API_VERSION_CALENDAR/{calendarId}/keys/{keyId}")
    suspend fun reenableKey(@Path("calendarId") calendarId: String, @Path("keyId") keyId: String, @Body body: ReenableKeyApiRequest) : ReenableKeyApiResponse

    @PUT("calendar/$API_VERSION_CALENDAR/{calendarId}/events/{eventId}/attendees/{attendeeId}")
    suspend fun updateParticipationStatus(@Path("calendarId") calendarId: String,
                                          @Path("eventId") eventId: String,
                                          @Path("attendeeId") attendeeId: String,
                                          @Body body: UpdateParticipationStatusApiRequest) : AttendeeApiResponse

    @PUT("calendar/$API_VERSION_CALENDAR/{calendarId}/events/{eventId}/personal")
    suspend fun updateEventPersonalPart(@Path("calendarId") calendarId: String,
                                        @Path("eventId") eventId: String,
                                        @Body body: UpdateEventPersonalPartApiRequest) : EventApiResponse
}

class CalendarsApiImpl(private val apiProvider: ApiProvider) : CalendarsApi {

    override suspend fun getCalendars(userId: UserId): ApiResponse<CalendarsApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            getCalendars()
        }.toApiResponse()

    override suspend fun getCalendar(userId: UserId, calendarId: String): ApiResponse<CalendarApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            getCalendar(calendarId)
        }.toApiResponse()

    override suspend fun getEvents(
        userId: UserId,
        calendarId: String,
        startTimestamp: Long,
        endTimestamp: Long,
        timezone: String,
        type: Int,
        page: Int,
        pageSize: Int
    ): ApiResponse<EventsApiResponse> = apiProvider.get<CalendarsApiService>(userId).invoke {
        getEvents(
            calendarId,
            startTimestamp,
            endTimestamp,
            timezone,
            type,
            page,
            pageSize
        )
    }.toApiResponse()

    override suspend fun getEvent(
        userId: UserId,
        calendarId: String,
        eventId: String
    ): ApiResponse<EventApiResponse> = apiProvider.get<CalendarsApiService>(userId).invoke {
        getEvent(calendarId, eventId)
    }.toApiResponse()

    override suspend fun getBootstrap(userId: UserId, calendarId: String): ApiResponse<BootstrapApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            getBootstrap(calendarId)
        }.toApiResponse()

    override suspend fun getAlarms(userId: UserId, calendarId: String, startTimestamp: Long, endTimestamp: Long, pageSize: Int): ApiResponse<AlarmsApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            getAlarms(calendarId, startTimestamp, endTimestamp, pageSize)
        }.toApiResponse()

    override suspend fun getEventAlarms(
        userId: UserId,
        calendarId: String,
        eventId: String
    ): ApiResponse<AlarmsApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            getEventAlarms(calendarId, eventId)
        }.toApiResponse()

    override suspend fun deleteEvent(userId: UserId, calendarId: String, eventId: String): ApiResponse<DeleteEventApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            deleteEvent(calendarId, eventId)
        }.toApiResponse()

    override suspend fun syncEvents(userId: UserId, calendarId: String, body: SyncEventsUpdateApiRequest): ApiResponse<SyncEventsApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            syncEvents(calendarId, body)
        }.toApiResponse()

    override suspend fun getEventsByUid(userId: UserId, eventUid: String, page: Int, pageSize: Int): ApiResponse<EventsByUidApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            getEventsByUid(eventUid, page, pageSize)
        }.toApiResponse()

    override suspend fun updateCalendar(userId: UserId, calendarId: String, body: UpdateCalendarApiRequest): ApiResponse<CalendarApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            updateCalendar(calendarId, body)
        }.toApiResponse()

    override suspend fun updateCalendarDisplay(userId: UserId, calendarId: String, body: UpdateCalendarDisplayApiRequest): ApiResponse<CalendarApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            updateCalendarDisplay(calendarId, body)
        }.toApiResponse()

    override suspend fun createCalendar(userId: UserId, body: CreateCalendarApiRequest): ApiResponse<CalendarApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            createCalendar(body)
        }.toApiResponse()

    override suspend fun getMemberList(userId: UserId, calendarId: String): ApiResponse<MemberListApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            getMemberList(calendarId)
        }.toApiResponse()

    override suspend fun setupKey(userId: UserId, calendarId: String, body: SetupKeyApiRequest): ApiResponse<SetupKeyApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            setupKey(calendarId, body)
        }.toApiResponse()

    override suspend fun getKeys(userId: UserId, calendarId: String): ApiResponse<KeysApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            getKeys(calendarId)
        }.toApiResponse()

    override suspend fun reenableKey(userId: UserId, calendarId: String, keyId: String, body: ReenableKeyApiRequest): ApiResponse<ReenableKeyApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            reenableKey(calendarId, keyId, body)
        }.toApiResponse()

    override suspend fun getResetInfo(userId: UserId): ApiResponse<ResetInfoApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            getResetInfo()
        }.toApiResponse()

    override suspend fun resetCalendar(userId: UserId, body: ResetCalendarApiRequest): ApiResponse<ResetCalendarApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            resetCalendar(body)
        }.toApiResponse()

    override suspend fun getPassphrases(userId: UserId, calendarId: String): ApiResponse<PassphrasesApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            getPassphrases(calendarId)
        }.toApiResponse()

    override suspend fun updateParticipationStatus(
        userId: UserId,
        calendarId: String,
        eventId: String,
        attendeeId: String,
        status: Int,
        updateTime: Int?
    ): ApiResponse<AttendeeApiResponse> = apiProvider.get<CalendarsApiService>(userId).invoke {
        updateParticipationStatus(calendarId, eventId, attendeeId, UpdateParticipationStatusApiRequest(
            status, updateTime ?: Instant.now().epochSecond.toInt())
        )
    }.toApiResponse()

    override suspend fun updateEventPersonalPart(
        userId: UserId,
        calendarId: String,
        eventId: String,
        body: UpdateEventPersonalPartApiRequest
    ): ApiResponse<EventApiResponse> = apiProvider.get<CalendarsApiService>(userId).invoke {
        updateEventPersonalPart(calendarId, eventId, body)
    }.toApiResponse()
}

@Serializable
data class CalendarsApiResponse(
    @SerialName("Calendars")
    val calendars: List<CalendarEntity>
)

@Serializable
data class EventsApiResponse(
    @SerialName("Events")
    val events: List<EventEntity>,
    @SerialName("More")
    val more: Int
)

@Serializable
data class EventApiResponse(
    @SerialName("Event")
    val event: EventEntity
)

@Serializable
data class SyncEventsUpdateApiRequest(
    @SerialName("MemberID")
    val memberId: String,
    @SerialName("Events")
    val events: List<SyncEventContainer>
)

@Serializable
data class UpdateCalendarApiRequest(
    @SerialName("Name")
    val name: String,
    @SerialName("Description")
    val description: String,
    @SerialName("Color")
    val color: String,
    @SerialName("Display")
    val display: Int
)

@Serializable
data class UpdateCalendarDisplayApiRequest(
    @SerialName("Display")
    val display: Int
)

@Serializable
data class CreateCalendarApiRequest(
    @SerialName("Name")
    val name: String,
    @SerialName("Description")
    val description: String,
    @SerialName("AddressID")
    val addressId: String,
    @SerialName("Color")
    val color: String,
    @SerialName("Display")
    val display: Int
)

@Serializable
data class CalendarApiResponse(
    @SerialName("Calendar")
    val calendar: CalendarEntity
)

@Serializable
data class MemberListApiResponse(
    @SerialName("Members")
    val members: List<MemberEntity>
)

@Serializable
data class SetupKeyApiRequest(
    @SerialName("PrivateKey")
    val privateKey: String,
    @SerialName("Signature")
    val signature: String,
    @SerialName("AddressID")
    val addressId: String,
    @SerialName("Passphrase")
    val passphrase: PassphraseApiRequest,
)

@Serializable
data class PassphraseApiRequest(
    @SerialName("DataPacket")
    val dataPacket: String,
    @SerialName("KeyPackets")
    val keyPackets: Map<String, String>
)

@Serializable
data class SetupKeyApiResponse(
    @SerialName("Key")
    val calendarKey: CalendarKeyEntity
)

@Serializable
data class ResetInfoApiResponse(
    @SerialName("Calendars")
    val calendars: List<ResetInfoCalendar>
)

@Serializable
data class ResetInfoCalendar(
    @SerialName("ID")
    val id: String,
    @SerialName("Name")
    val name: String,
    @SerialName("Description")
    val description: String,
    @SerialName("Members")
    val members: Map<String, String>
)

@Serializable
data class ResetCalendarApiRequest(
    @SerialName("CalendarKeys")
    val calendarKeys: Map<String, SetupKeyApiRequest>
)


// TODO container for CREATE LINKED by adding SharedEventID and UID

@Serializable
sealed class SyncEventContainer

@Serializable
data class SyncEventCreateContainer(
    @SerialName("Event")
    val event: SyncEvent
) : SyncEventContainer()

@Serializable
data class SyncEventUpdateContainer(
    @SerialName("ID")
    val id: String,
    @SerialName("Event")
    val event: SyncEvent
) : SyncEventContainer()

@Serializable
data class SyncEventDeleteContainer(
    @SerialName("ID")
    val id: String
) : SyncEventContainer()

@Serializable
data class SyncEvent(
    @SerialName("Permissions")
    val permissions: Int? = null,
    @SerialName("IsOrganizer")
    val isOrganizer: Int, // Default value is 1
    @SerialName("CalendarKeyPacket")
    val calendarKeyPacket: String? = null,
    @SerialName("CalendarEventContent")
    val calendarEventContent: List<Event.EventPart.Calendar>? = null,
    @SerialName("SharedKeyPacket")
    val sharedKeyPacket: String? = null,
    @SerialName("SharedEventContent")
    val sharedEventContent: List<Event.EventPart.Shared>? = null,
    @SerialName("PersonalEventContent")
    val personalEventContent: Event.EventPart.Personal? = null,
    @SerialName("AttendeesEventContent")
    val attendeesEventContent: List<Event.EventPart.Attendee>? = null,
    @SerialName("Attendees")
    val attendees: List<Event.AttendeeStatusEvent>? = null,
    @SerialName("SharedEventID")
    val sharedEventId: String? = null,
    @SerialName("UID")
    val uid: String? = null
)

@Serializable
data class BootstrapApiResponse(
    @SerialName("Keys")
    val keys: List<CalendarKeyEntity>,
    @SerialName("Passphrase")
    val passphrase: PassphraseEntity,
    @SerialName("Members")
    val members: List<MemberEntity>,
    @SerialName("CalendarSettings")
    val calendarSettings: CalendarSettingsEntity, // settings specific to calendar, not user
    @SerialName("CalendarSubscription")
    val calendarSubscriptionEntity: CalendarSubscriptionEntity? = null // contains extra properties for subscribed calendars
)

@Serializable
data class SyncEventsApiResponse(
    @SerialName("Responses")
    val responses: List<SyncResponseWrapper>
)

@Serializable
data class SyncResponseWrapper(
    @SerialName("Index")
    val index: Int,
    @SerialName("Response")
    val response: SyncResponse
    // TODO errors and other types of payload
)

@Serializable
data class SyncResponse(
    @SerialName("Code")
    override val code: Int,
    @SerialName("Event")
    val event: EventEntity? = null
    // TODO errors and other types of payload
) : BaseApiResponse()

@Serializable
data class AlarmsApiResponse(
    @SerialName("Alarms")
    val alarms: List<EventAlarmEntity>
)

@Serializable
data class EventsByUidApiResponse(
    @SerialName("Events")
    val events: List<EventEntity>
)

@Serializable
data class ResetCalendarApiResponse(
    @SerialName("Code")
    override val code: Int
) : BaseApiResponse()

@Serializable
data class DeleteEventApiResponse(
    @SerialName("Code")
    override val code: Int
) : BaseApiResponse()

@Serializable
data class KeysApiResponse(
    @SerialName("Keys")
    val keys: List<CalendarKeyEntity>,
)

@Serializable
data class PassphrasesApiResponse(
    @SerialName("Passphrases")
    val passphrases: List<PassphraseEntity>,
)

@Serializable
data class ReenableKeyApiResponse(
    @SerialName("Key")
    val calendarKey: CalendarKeyEntity
)

@Serializable
data class ReenableKeyApiRequest(
    @SerialName("PrivateKey")
    val privateKey: String
)

@Serializable
data class AttendeeApiResponse(
    @SerialName("Event")
    val event: AttendeeStatusApiResponse
)

@Serializable
data class AttendeeStatusApiResponse(
    @SerialName("ID")
    val id: String,
    @SerialName("ModifyTime")
    val modifyTime: Int
)

@Serializable
data class UpdateParticipationStatusApiRequest(
    @SerialName("Status")
    val status: Int, // 0: Unanswered, 1: Maybe, 2: No, 3: Yes
    @SerialName("UpdateTime")
    val updateTime: Int? = null
)

@Serializable
data class UpdateEventPersonalPartApiRequest(
    @SerialName("MemberID")
    val memberID: String,
    @SerialName("PersonalEventContent")
    val personalEventContent: PersonalEventContentApiRequest? = null
)

@Serializable
data class PersonalEventContentApiRequest(
    @SerialName("Type")
    val type: Int,
    @SerialName("Data")
    val data: String,
    @SerialName("Signature")
    val signature: String
)
