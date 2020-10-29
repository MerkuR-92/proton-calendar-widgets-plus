package me.proton.android.calendar.data.api

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.common.API_VERSION_CALENDAR
import me.proton.android.calendar.data.entity.*
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.*
import me.proton.android.calendar.domain.model.Event
import retrofit2.Response
import retrofit2.http.*

interface CalendarsApiService {

    @GET("calendar/$API_VERSION_CALENDAR")
    suspend fun getCalendars(@Query("Page") page: Int = 0, @Query("PageSize") pageSize: Int = 100): Response<CalendarsApiResponse>

    @GET("calendar/$API_VERSION_CALENDAR/{calendarId}/events")
    suspend fun getEvents(
        @Path("calendarId") calendarId: String,
        @Query("Start") startTimestamp: Long,
        @Query("End") endTimestamp: Long,
        @Query("Timezone") timezone: String,
        @Query("Type") type: Int,
        @Query("Page") page: Int,
        @Query("PageSize") pageSize: Int
    ): Response<EventsApiResponse>

    @GET("calendar/$API_VERSION_CALENDAR/{calendarId}/events/{eventId}")
    suspend fun getEvent(@Path("calendarId") calendarId: String, @Path("eventId") eventId: String) : Response<EventApiResponse>

    @GET("calendar/$API_VERSION_CALENDAR/{calendarId}/bootstrap")
    suspend fun getBootstrap(@Path("calendarId") calendarId: String): Response<BootstrapApiResponse>

    @GET("calendar/$API_VERSION_CALENDAR/{calendarId}/alarms")
    suspend fun getAlarms(@Path("calendarId") calendarId: String, @Query("Start") startTimestamp: Long, @Query("End") endTimestamp: Long, @Query("PageSize") pageSize: Int) : Response<AlarmsApiResponse>

    @DELETE("calendar/$API_VERSION_CALENDAR/{calendarId}/events/{eventId}")
    suspend fun deleteEvent(@Path("calendarId") calendarId: String, @Path("eventId") eventId: String) : Response<StatusCodeApiResponse>

    @PUT("calendar/$API_VERSION_CALENDAR/{calendarId}/events/sync")
    suspend fun syncEvents(@Path("calendarId") calendarId: String, @Body body: SyncEventsUpdateApiRequest) : Response<SyncEventsApiResponse>

    @GET("calendar/$API_VERSION_CALENDAR/events")
    suspend fun getEventsByUid(@Query("UID") eventUid: String, @Query("Page") page: Int, @Query("PageSize") pageSize: Int) : Response<EventsByUidApiResponse>

    @PUT("calendar/$API_VERSION_CALENDAR/{calendarId}")
    suspend fun updateCalendar(@Path("calendarId") calendarId: String, @Body body: UpdateCalendarApiRequest) : Response<UpdateCalendarApiResponse>

}

class CalendarsApiImpl(private val service: CalendarsApiService, gson: Gson, logger: Logger) : BaseApi(gson, logger), CalendarsApi {

    override suspend fun getCalendars(): ApiResponse<CalendarsApiResponse> = safeApiCall { service.getCalendars() }

    override suspend fun getEvents(
        //userId: String,
        calendarId: String,
        startTimestamp: Long,
        endTimestamp: Long,
        timezone: String,
        type: Int,
        page: Int,
        pageSize: Int
    ): ApiResponse<EventsApiResponse> = safeApiCall { service.getEvents(/*RetrofitTag(userId), */
        calendarId,
        startTimestamp,
        endTimestamp,
        timezone,
        type,
        page,
        pageSize
    ) }

    override suspend fun getEvent(
        calendarId: String,
        eventId: String
    ): ApiResponse<EventApiResponse> = safeApiCall { service.getEvent(calendarId, eventId) }

    override suspend fun getBootstrap(calendarId: String): ApiResponse<BootstrapApiResponse> = safeApiCall { service.getBootstrap(calendarId) }

    override suspend fun getAlarms(calendarId: String, startTimestamp: Long, endTimestamp: Long, pageSize: Int): ApiResponse<AlarmsApiResponse> = safeApiCall { service.getAlarms(calendarId, startTimestamp, endTimestamp, pageSize) }

    override suspend fun deleteEvent(calendarId: String, eventId: String): ApiResponse<StatusCodeApiResponse>  = safeApiCall { service.deleteEvent(calendarId, eventId) }

    override suspend fun syncEvents(calendarId: String, body: SyncEventsUpdateApiRequest): ApiResponse<SyncEventsApiResponse> = safeApiCall { service.syncEvents(calendarId, body) }

    override suspend fun getEventsByUid(eventUid: String, page: Int, pageSize: Int): ApiResponse<EventsByUidApiResponse> = safeApiCall { service.getEventsByUid(eventUid, page, pageSize) }

    override suspend fun updateCalendar(calendarId: String, body: UpdateCalendarApiRequest): ApiResponse<UpdateCalendarApiResponse> = safeApiCall{
        service.updateCalendar(calendarId, body)
    }

}

@Serializable
data class CalendarsApiResponse(
    @SerialName("Code")
    override val code: Int,
    @SerialName("Calendars")
    val calendars: List<CalendarEntity>
) : BaseApiResponse()

@Serializable
data class EventsApiResponse(
    @SerialName("Code")
    override val code: Int,
    @SerialName("Events")
    val events: List<EventEntity>,
    @SerialName("More")
    val more: Int
) : BaseApiResponse()

@Serializable
data class EventApiResponse(
    @SerialName("Code")
    override val code: Int,
    @SerialName("Event")
    val event: EventEntity
) : BaseApiResponse()

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
    val calendarKeyPacket: String?,
    @SerialName("CalendarEventContent")
    val calendarEventContent: List<Event.CalendarEvent>?,
    @SerialName("SharedKeyPacket")
    val sharedKeyPacket: String? = null,
    @SerialName("SharedEventContent")
    val sharedEventContent: List<Event.SharedEvent>,
    @SerialName("PersonalEventContent")
    val personalEventContent: Event.PersonalEvent?
)

@Serializable
data class BootstrapApiResponse(
    @SerialName("Code")
    override val code: Int,
    @SerialName("Keys")
    val keys: List<CalendarKeyEntity>,
    @SerialName("Passphrase")
    val passphrase: PassphraseEntity,
    @SerialName("Members")
    val members: List<MemberEntity>,
    @SerialName("CalendarSettings")
    val calendarSettings: CalendarSettingsEntity // settings specific to calendar, not user
) : BaseApiResponse()

@Serializable
data class CreateEventApiResponse(
    @SerialName("code")
    override val code: Int
) : BaseApiResponse()

@Serializable
data class SyncEventsApiResponse(
    @SerialName("Code")
    override val code: Int,
    @SerialName("Responses")
    val responses: List<SyncResponseWrapper>
) : BaseApiResponse()

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
data class UpdateCalendarApiResponse(
    @SerialName("Code")
    override val code: Int
) : BaseApiResponse()

@Serializable
data class AlarmsApiResponse(
    @SerialName("Code")
    override val code: Int,
    @SerialName("Alarms")
    val alarms: List<EventAlarmEntity>
) : BaseApiResponse()

@Serializable
data class EventsByUidApiResponse(
    @SerialName("Code")
    override val code: Int,
    @SerialName("Events")
    val events: List<EventEntity>
) : BaseApiResponse()
