package me.proton.android.calendar.data.api

import com.google.gson.*
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.data.entity.*
import me.proton.android.calendar.domain.api.ServerEventsApi
import me.proton.core.domain.entity.UserId
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.data.protonApi.BaseRetrofitApi
import retrofit2.http.GET
import retrofit2.http.Path

// ServerEvent is an Event happening in Event Loop

interface ServerEventsApiService : BaseRetrofitApi {

    @GET("events/latest")
    suspend fun getLatestProtonEvent(): LatestServerEventApiResponse

    @GET("events/{eventId}")
    suspend fun getProtonEvents(@Path("eventId") sinceProtonEventId: String): ServerEventsApiResponse
}

class ServerEventsApiImpl(private val apiProvider: ApiProvider) : ServerEventsApi {

    override suspend fun getLatestServerEvent(userId: UserId): ApiResponse<LatestServerEventApiResponse> =
        apiProvider.get<ServerEventsApiService>(userId).invoke {
            getLatestProtonEvent()
        }.toApiResponse()

    override suspend fun getServerEvents(userId: UserId, sinceServerEventId: String): ApiResponse<ServerEventsApiResponse> =
        apiProvider.get<ServerEventsApiService>(userId).invoke {
            getProtonEvents(sinceServerEventId)
        }.toApiResponse()

}

@Serializable
data class LatestServerEventApiResponse(
    @SerialName("EventID")
    val eventId: String
)

@Serializable
data class ServerEventsApiResponse(
    @SerialName("EventID")
    val eventId: String, // new eventId to send with next request
    @SerialName("Refresh")
    val refresh: Int, // bitmap, 255 means throw out client cache and reload everything from server, 1 is mail, 2 is contacts
    @SerialName("More")
    val more: Int, // 0 or 1 if more events exist and should be fetched
    @SerialName("User")
    val user: UserEntity? = null, // doesn't contain "Action", it's always "update"
    @SerialName("UserSettings")
    val userSettings: UserSettingsEntity? = null,
    @SerialName("Addresses")
    val addresses: List<ServerEvent.AddressesApiResponse>? = null,
    @SerialName("Calendars")
    val calendars: List<ServerEvent.CalendarsApiResponse>? = null,
    @SerialName("CalendarKeys")
    val calendarKeys: List<ServerEvent.CalendarKeysApiResponse>? = null,
    @SerialName("CalendarPassphrases")
    val calendarPassphrases: List<ServerEvent.PassphrasesApiResponse>? = null,
    @SerialName("CalendarMembers")
    val calendarMembers: List<ServerEvent.MembersApiResponse>? = null,
    @SerialName("CalendarEvents")
    val calendarEvents: List<ServerEvent.EventsApiResponse>? = null,
    @SerialName("CalendarSettings")
    val calendarSettings: List<ServerEvent.CalendarSettingsApiResponse>? = null,
    @SerialName("CalendarAlarms")
    val calendarAlarms: List<ServerEvent.AlarmsApiResponse>? = null,
    @SerialName("CalendarUserSettings")
    val calendarUserSettings: CalendarUserSettingsEntity? = null
)

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

    @Serializable
    data class AddressesApiResponse(
        @SerialName("ID")
        override val id: String,
        @SerialName("Action")
        override val action: Int,
        @SerialName("Address")
        val address: AddressEntity? = null // all the payloads here are nullable, because action = 0 (delete) sends no payload
    ) : BaseServerEventApiResponse()

    @Serializable
    data class CalendarsApiResponse(
        @SerialName("ID")
        override val id: String,
        @SerialName("Action")
        override val action: Int,
        @SerialName("Calendar")
        val calendar: CalendarEntity? = null
    ) : BaseServerEventApiResponse()

    @Serializable
    data class CalendarKeysApiResponse(
        @SerialName("ID")
        override val id: String,
        @SerialName("Action")
        override val action: Int,
        @SerialName("Key")
        val key: CalendarKeyEntity? = null
    ) : BaseServerEventApiResponse()

    @Serializable
    data class PassphrasesApiResponse(
        @SerialName("ID")
        override val id: String,
        @SerialName("Action")
        override val action: Int,
        @SerialName("Passphrase")
        val passphrase: PassphraseEntity? = null
    ) : BaseServerEventApiResponse()

    @Serializable
    data class MembersApiResponse(
        @SerialName("ID")
        override val id: String,
        @SerialName("Action")
        override val action: Int,
        @SerialName("Member")
        val member: MemberEntity? = null
    ) : BaseServerEventApiResponse()

    @Serializable
    data class EventsApiResponse(
        @SerialName("ID")
        override val id: String,
        @SerialName("Action")
        override val action: Int,
        @SerialName("Event")
        val event: EventEntityMetadata? = null
    ) : BaseServerEventApiResponse()

    // this class is not persisted in the database
    @Serializable
    data class EventEntityMetadata(
        @SerialName("ID")
        val id: String,
        @SerialName("CalendarID")
        val calendarId: String,
        @SerialName("StartTime")
        val startTime: Long,
        @SerialName("StartTimezone")
        val startTimeZone: String,
        @SerialName("EndTime")
        val endTime: Long,
        @SerialName("EndTimezone")
        val endTimeZone: String,
        @SerialName("FullDay")
        val fullDay: Int,
        @SerialName("UID")
        val uid: String,
        @SerialName("RecurrenceID")
        val recurrenceID: Long?,
        @SerialName("Exdates")
        val exDates: List<Long>,
        @SerialName("RRule")
        val rRule: String?,
        @SerialName("CreateTime")
        val createTime: Long, // unix timestamps
        @SerialName("ModifyTime")
        val modifyTime: Long,
        @SerialName("IsOrganizer")
        val isOrganizer: Int
    )

    @Serializable
    class CalendarSettingsApiResponse(
        @SerialName("ID")
        override val id: String,
        @SerialName("Action")
        override val action: Int,
        @SerialName("CalendarSettings")
        val calendarSettings: CalendarSettingsEntity? = null
    ) : BaseServerEventApiResponse()

    @Serializable
    class AlarmsApiResponse(
        @SerialName("ID")
        override val id: String,
        @SerialName("Action")
        override val action: Int,
        @SerialName("Alarm")
        val alarm: EventAlarmEntity? = null
    ) : BaseServerEventApiResponse()

}

