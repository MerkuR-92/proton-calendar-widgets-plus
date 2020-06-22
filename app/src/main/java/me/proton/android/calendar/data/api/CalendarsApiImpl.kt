package me.proton.android.calendar.data.api

import com.google.gson.Gson
import me.proton.android.calendar.common.API_VERSION_CALENDAR
import me.proton.android.calendar.data.entity.*
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.*
import me.proton.android.calendar.domain.model.Event
import retrofit2.Response
import retrofit2.http.*

interface CalendarsApiService {

//    @GET("calendars/")
    @GET("calendar/$API_VERSION_CALENDAR/")
    suspend fun getCalendars(@Query("Page") page: Int = 0, @Query("PageSize") pageSize: Int = 100): Response<CalendarsApiResponse>

//        calendars/{{Calendar.CalendarID}}/events?Start=1580515200&End=1583020800&Timezone=Europe/Paris
    @GET("calendar/$API_VERSION_CALENDAR/{calendarId}/events")
    suspend fun getEvents(
//        @Tag retrofitTag: RetrofitTag,
        @Path("calendarId") calendarId: String,
        @Query("Start") startTimestamp: Long,
        @Query("End") endTimestamp: Long,
        @Query("Timezone") timezone: String,
        @Query("Page") page: Int = 0,
        @Query("PageSize") pageSize: Int = 100
    ): Response<EventsApiResponse>


    @GET("calendar/$API_VERSION_CALENDAR/{calendarId}/bootstrap")
    suspend fun getBootstrap(@Path("calendarId") calendarId: String): Response<BootstrapApiResponse>

    @GET("calendar/$API_VERSION_CALENDAR/{calendarId}/alarms")
    suspend fun getAlarms(@Path("calendarId") calendarId: String) : Response<AlarmsApiResponse>

    @DELETE("calendar/$API_VERSION_CALENDAR/{calendarId}/events/{eventId}")
    suspend fun deleteEvent(@Path("calendarId") calendarId: String, @Path("eventId") eventId: String) : Response<StatusCodeApiResponse>

    @PUT("calendar/$API_VERSION_CALENDAR/{calendarId}/events/sync")
    suspend fun syncEvents(@Path("calendarId") calendarId: String, @Body body: SyncEventsUpdateApiRequest) : Response<SyncEventsApiResponse>

}

class CalendarsApiImpl(private val service: CalendarsApiService, gson: Gson, logger: Logger) : BaseApi(gson, logger), CalendarsApi {

    override suspend fun getCalendars(): ApiResponse<CalendarsApiResponse> = safeApiCall { service.getCalendars() }

    override suspend fun getEvents(
        //userId: String,
        calendarId: String,
        startTimestamp: Long,
        endTimestamp: Long,
        timezone: String
    ): ApiResponse<EventsApiResponse> = safeApiCall { service.getEvents(/*RetrofitTag(userId), */calendarId, startTimestamp, endTimestamp, timezone) }

    override suspend fun getBootstrap(calendarId: String): ApiResponse<BootstrapApiResponse> = safeApiCall { service.getBootstrap(calendarId) }

    override suspend fun getAlarms(calendarId: String): ApiResponse<AlarmsApiResponse> = safeApiCall { service.getAlarms(calendarId) }

    override suspend fun deleteEvent(calendarId: String, eventId: String): ApiResponse<StatusCodeApiResponse>  = safeApiCall { service.deleteEvent(calendarId, eventId) }

    override suspend fun syncEvents(calendarId: String, body: SyncEventsUpdateApiRequest): ApiResponse<SyncEventsApiResponse> = safeApiCall { service.syncEvents(calendarId, body) }

}


data class CalendarsApiResponse(
    override val code: Int,
    val calendars: List<CalendarEntity>
) : BaseApiResponse()

data class EventsApiResponse(
    override val code: Int,
    val events: List<EventEntity>
) : BaseApiResponse()

//"MemberID": "{{Calendar.MemberID}}",
//  "Permissions": 1,
//  "CalendarKeyPacket": null,
//  "CalendarEventContent": [],
//  "SharedKeyPacket": "wV4DcC\/2yqTc1AkSAQdA\/z7Qyp8XfBvSPgiQ4ndlbu\/viJ2nfB+XXPvj\/zmIQVUwgmXPs\/ELe0cGAiOIunrTwVXQ7+KAzf6WC8sAXkDOu290HQrjbhjy7WZocQ+ZKEvm",
//  "SharedEventContent": [
//    {
//      "Type": 2,
//      "Data": "BEGIN:VCALENDAR\nPRODID:-\/\/CALENDARSERVER.ORG\/\/NONSGML Version 1\/\/EN\nVERSION:2.0\nBEGIN:VEVENT\nDTSTAMP:20190905T120251Z\nTZID:Europe\/Zurich\nUID:7E018059-2165-4170-B32F-6936E88E61E5295\nDTSTART;TZID=Europe\/Zurich:20190910T120000\nDTEND;TZID=Europe\/Zurich:20190910T130000\nEND:VEVENT\nEND:VCALENDAR",
//      "Signature": "-----BEGIN PGP SIGNATURE-----\r\nVersion: OpenPGP.js v4.5.5\r\nComment: https:\/\/openpgpjs.org\r\n\r\nwsBcBAEBCAAGBQJdcN1MAAoJELdKNH+ugJvYpeYH\/iMFhM7MIBokI+m8fv5z\r\nHykTkXfCkK6nZGfS6SaIs7hp8ktSLoh2z3e5iKfkjCukzxlEKkhiooZFY6DF\r\nCUqPI5pUwPE45k95PWK76vVsonovj9kDw2aLrFeQCJaEhGOSMmn2GlyJtOP0\r\nWC\/yFuPR\/pEobHIzffCMv2Mn62iYRf7+d+sFHqMqCwLomUI9VFBK9VU8ll9z\r\nUtQbLq0h4\/k2d7BQlOnx\/KIN5oLVLSg2k9GhOFmdm4panAs6srePPEfw8lv3\r\nt5xTQ7+Dbo15E5Y+6MNVyoRZoHx5\/ciyljexyTf+Q3rYc3KsSiUQ7BmHj69W\r\nSyLWOpubQW9gE\/znJCnl9Ck=\r\n=Ve+v\r\n-----END PGP SIGNATURE-----\r\n"
//    },
//    {
//      "Type": 3,
//      "Data": "0sA4ATgechqLZ42IQt48nZL9h+ToZWrbShNNlnH1FnT\/QeMRmEeaDJWiugzNZKshhON+9izXngD49NtmFZmdi0c5mS3NvSfcSERLV3A\/oSs6sRZRYt9ro2Q1pCh4EOZqhtajlIoqyaUWAOZrmtlUgejNRGhoThWH\/LXaFE7BWpTJuRqYNGGNIigChMipDTjXHqAD1sVJPBHABW5O6sg75jV\/Lloqfv5xNDRbZK5dnyKbAZk3y+14q\/pVoDJCXQTH87Npz7rzDMTMCMMDhV+MR9nShnv1UmMSfgsH8403A4ukoIbiBRf9oCtX2i2yut6sFGTYQyJ0Zy1qY2c=",
//      "Signature": "-----BEGIN PGP SIGNATURE-----\r\nVersion: OpenPGP.js v4.5.5\r\nComment: https:\/\/openpgpjs.org\r\n\r\nwsBcBAEBCAAGBQJdcN1MAAoJELdKNH+ugJvYujQH\/1I9DvCLqeYVocupHch5\r\n6zUxebE\/OSIz0FgTTaR00tQKhCHmT\/lLEUinQumAsUGrKh5HU+RPM4M1rhKL\r\n\/sXu3069vi2eFw2jx6yZzZBVAGXOG8zoyDxQoIQysnfxJM8iIomh9lhE7M44\r\nTjNSwmIKLeOWiINpr\/zfaFMDncWEmGpE+wu62hzdMEl+33Wt1x8jzzZHlDCE\r\ngiypcXKFAosaC3ewz1O1q3WwyqggDuw4+tDhI6JSF5pJmUbOy1IBfLX4e3f4\r\nWIwD1N88Z5Q37xuXOi1e5fer4\/Xy\/Kvcy14ldIHDhT2XMJB3iOfH0NdOn6R3\r\n5MqQzOylGmwBNhvl+ZaqicM=\r\n=tEbj\r\n-----END PGP SIGNATURE-----\r\n"
//    }
data class CreateEventApiRequest(
    val memberId: String,
    val permissions: Int,
    val calendarKeyPacket: String?, // TODO for now, for simple example, but it can/must (sometimes) be empty
    val calendarEventContent: List<Event.CalendarEvent>?, // TODO empty for now
    val sharedKeyPacket: String, // TODO I think it always has to be there
    val sharedEventContent: List<Event.SharedEvent>,
    val personalEventContent: Event.PersonalEvent?
    // TODO AttendeesEventContent, Attendees
    )


data class SyncEventsUpdateApiRequest(
    val memberId: String,
    val events: List<SyncEventContainer>
)

// TODO container for CREATE LINKED by adding SharedEventID and UID

interface SyncEventContainer

data class SyncEventCreateContainer(
    val event: SyncEvent
) : SyncEventContainer

data class SyncEventUpdateContainer(
    val id: String,
    val event: SyncEvent
) : SyncEventContainer

data class SyncEvent(
    val permissions: Int,
    val calendarKeyPacket: String?,
    val calendarEventContent: List<Event.CalendarEvent>?,
    val sharedKeyPacket: String?,
    val sharedEventContent: List<Event.SharedEvent>,
    val personalEventContent: Event.PersonalEvent?
)

data class SyncEventDeleteContainer(
    val id: String
)

data class BootstrapApiResponse(
    override val code: Int,
    val keys: List<CalendarKeyEntity>,
    val passphrase: PassphraseEntity,
    val members: List<MemberEntity>,
    val calendarSettings: SettingsEntity // settings specific to calendar, not user
) : BaseApiResponse()

data class CreateEventApiResponse(
    override val code: Int // TODO other fields
) : BaseApiResponse()

data class SyncEventsApiResponse(
    override val code: Int // TODO other fields
) : BaseApiResponse()

//@Serializable

//enum class CalendarDisplay(val display: Int) {
//    @SerializedName("0")
//    HIDE(0),
//    @SerializedName("1")
//    SHOW(1)
//}

data class AlarmsApiResponse(
    override val code: Int,
    val eventAlarms: List<EventAlarmEntity>
) : BaseApiResponse()
