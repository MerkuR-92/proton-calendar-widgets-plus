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

interface CalendarsApiService : BaseRetrofitApi {

    @GET("calendar/$API_VERSION_CALENDAR")
    suspend fun getCalendars(@Query("Page") page: Int = 0, @Query("PageSize") pageSize: Int = 100): CalendarsApiResponse

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

    @DELETE("calendar/$API_VERSION_CALENDAR/{calendarId}/events/{eventId}")
    suspend fun deleteEvent(@Path("calendarId") calendarId: String, @Path("eventId") eventId: String) : StatusCodeApiResponse

    @PUT("calendar/$API_VERSION_CALENDAR/{calendarId}/events/sync")
    suspend fun syncEvents(@Path("calendarId") calendarId: String, @Body body: SyncEventsUpdateApiRequest) : SyncEventsApiResponse

    @GET("calendar/$API_VERSION_CALENDAR/events")
    suspend fun getEventsByUid(@Query("UID") eventUid: String, @Query("Page") page: Int, @Query("PageSize") pageSize: Int) : EventsByUidApiResponse

    @PUT("calendar/$API_VERSION_CALENDAR/{calendarId}")
    suspend fun updateCalendar(@Path("calendarId") calendarId: String, @Body body: UpdateCalendarApiRequest) : UpdateCalendarApiResponse

}

class CalendarsApiImpl(private val apiProvider: ApiProvider) : CalendarsApi {

    override suspend fun getCalendars(userId: UserId): ApiResponse<CalendarsApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            getCalendars()
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

    override suspend fun deleteEvent(userId: UserId, calendarId: String, eventId: String): ApiResponse<StatusCodeApiResponse> =
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

    override suspend fun updateCalendar(userId: UserId, calendarId: String, body: UpdateCalendarApiRequest): ApiResponse<UpdateCalendarApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            updateCalendar(calendarId, body)
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
    val name: String? = null,
    @SerialName("Description")
    val description: String? = null,
    @SerialName("Color")
    val color: String? = null,
    @SerialName("Display")
    val display: Int? = null
)

@Serializable
data class UpdateCalendarApiResponse(
    @SerialName("ID")
    val id: String? = null,
    @SerialName("Name")
    val name: String? = null,
    @SerialName("Description")
    val description: String? = null,
    @SerialName("Color")
    val color: String? = null,
    @SerialName("Display")
    val display: Int? = null
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
    val permissions: Int,
    @SerialName("CalendarKeyPacket")
    val calendarKeyPacket: String? = null,
    @SerialName("CalendarEventContent")
    val calendarEventContent: List<Event.EventPart.Calendar>? = null,
    @SerialName("SharedKeyPacket")
    val sharedKeyPacket: String? = null,
    @SerialName("SharedEventContent")
    val sharedEventContent: List<Event.EventPart.Shared>,
    @SerialName("PersonalEventContent")
    val personalEventContent: Event.EventPart.Personal? = null
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
    val calendarSettings: CalendarSettingsEntity // settings specific to calendar, not user
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
