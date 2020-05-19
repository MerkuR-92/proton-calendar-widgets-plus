package me.proton.android.calendar.data.api

import com.google.gson.*
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import me.proton.android.calendar.data.entity.*
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.ServerEventsApi
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

// ServerEvent is an Event happening in Event Loop

interface ServerEventsApiService {

    @GET("events/latest")
    suspend fun getLatestProtonEvent(): Response<LatestServerEventApiResponse>

    @GET("events/{eventId}")
    suspend fun getProtonEvents(@Path("eventId") sinceProtonEventId: String): Response<ServerEventsApiResponse>
}

class ServerEventsApiImpl(private val service: ServerEventsApiService, gson: Gson, logger: Logger) : BaseApi(gson, logger), ServerEventsApi {

    override suspend fun getLatestServerEvent(): ApiResponse<LatestServerEventApiResponse> = safeApiCall { service.getLatestProtonEvent() }

    override suspend fun getServerEvents(sinceServerEventId: String): ApiResponse<ServerEventsApiResponse> = safeApiCall { service.getProtonEvents(sinceServerEventId) }

}

data class LatestServerEventApiResponse(
    override val code: Int,
    val eventId: String
) : BaseApiResponse()

data class ServerEventsApiResponse(
    override val code: Int, // new eventId to send with next request
    val eventId: String, // next event Id which should be persisted in client
    val refresh: Int, // bitmap, 255 means throw out client cache and reload everything from server, 1 is mail, 2 is contacts
    val more: Int, // 0 or 1 if more events exist and should be fetched
// contacts
//    @SerializedName("blabla")
    val user: UserEntity?, // doesn't contain "Action", I think it's always "update"
// userSettings
    val addresses: List<ServerEvent.AddressesApiResponse>?,
    val calendars: List<ServerEvent.CalendarsApiResponse>?,
    val calendarKeys: List<ServerEvent.CalendarKeysApiResponse>?, // TODO can't test it for now
    val calendarPassphrases: List<ServerEvent.PassphrasesApiResponse>?,  // TODO can't test it for now
    val calendarMembers: List<ServerEvent.MembersApiResponse>?,  // TODO can't test it for now
    val calendarEvents: List<ServerEvent.EventsApiResponse>?,
//    TODO: CalendarAttendees
//    TODO:    val calendarUserSettings
    val calendarSettings: List<ServerEvent.SettingsApiResponse>?,
    val calendarAlarms: List<ServerEvent.AlarmsApiResponse>?
) : BaseApiResponse()

// TODO HANDLE ACTIONS AND CREATE TESTS FOR THAT!!!!!!!!!!!!!!!!!!

class ServerEvent {

    // TODO BaseApiEntity? BaseEventApiEntity?
    abstract class BaseServerEventApiResponse {
        abstract val id: String
        abstract val action: Int // when action is 0 = delete, we get no payload, that's why all payloads are nullable
    }

    enum class Action(val value: Int) {
//        @SerializedName("0")
        DELETE(0),
        CREATE(1),
        UPDATE(2);
        //UPDATE_FLAGS(3) we don't use this for now, but will need to at one point, to only update metadata and not get entire blob from API

        companion object {
            fun valueOf(value: Int) = values().find { it.value == value }
        }

        // TODO extract this to generic function?
        class GsonSerializer : TypeAdapter<Action>() {
            override fun write(out: JsonWriter?, value: Action) {
                TODO("serializing enums is not implemented")

                val test : ApiEnum<Action> = ApiEnum(Action.DELETE)

            }

            override fun read(jsonReader: JsonReader?): Action {
                if (jsonReader == null) throw JsonParseException("JsonReader is null in GsonSerializer")
                return valueOf(jsonReader.nextInt())!! // if we get unsupported value, then we fail
            }
        }
    }



    class ApiEnum<E : Enum<E>>(val value: Enum<E>) {

//        interface Const<E : Enum<E>> {
//            val action: Action2<E>
//        }
    }


    data class AddressesApiResponse(
        override val id: String,
        override val action: Int,
        val address: AddressEntity? // all the payloads here are nullable, because action = 0 (delete) sends no payload
    ) : BaseServerEventApiResponse()

    data class CalendarsApiResponse(
        override val id: String,
        override val action: Int,
        val calendar: CalendarEntity? // TODO in /events it looks like there's no "flags" field
    ) : BaseServerEventApiResponse()

    data class CalendarKeysApiResponse(
        override val id: String,
        override val action: Int,
        val key: CalendarKeyEntity?
    ) : BaseServerEventApiResponse()

    data class PassphrasesApiResponse(
        override val id: String,
        override val action: Int,
        val passphrase: PassphraseEntity?
    ) : BaseServerEventApiResponse()

    data class MembersApiResponse(
            override val id: String,
            override val action: Int,
            val member: MemberEntity?
    ) : BaseServerEventApiResponse()

    data class EventsApiResponse(
            override val id: String,
            override val action: Int,
            val event: EventEntity?
    ) : BaseServerEventApiResponse()

    class SettingsApiResponse(
        override val id: String,
        override val action: Int,
        val calendarSettings: SettingsEntity?
    ) : BaseServerEventApiResponse()

    class AlarmsApiResponse(
            override val id: String,
            override val action: Int,
            val alarm: EventAlarmEntity?
    ) : BaseServerEventApiResponse()

}

