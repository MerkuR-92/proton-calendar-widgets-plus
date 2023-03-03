package me.proton.android.calendar.domain.usecase

import biweekly.component.VAlarm
import com.google.crypto.tink.subtle.Base64
import com.proton.gopenpgp.crypto.Crypto.generateSessionKey
import com.proton.gopenpgp.crypto.PGPMessage
import com.proton.gopenpgp.crypto.PlainMessage
import com.proton.gopenpgp.crypto.SessionKey
import me.proton.android.calendar.WidgetRefresher
import me.proton.android.calendar.common.utils.getAddressesOrNull
import me.proton.android.calendar.common.utils.toHexColor
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.JoinCalendarApiRequest
import me.proton.android.calendar.data.entity.HolidaysCalendarEntity
import me.proton.android.calendar.data.entity.NotificationEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Ciphertext
import me.proton.android.calendar.domain.Crypto
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ValueSet
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.api.ServerEventsApi
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.crypto.common.pgp.Armored
import me.proton.core.domain.entity.UserId
import me.proton.core.key.domain.decryptDataOrNull
import me.proton.core.key.domain.decryptSessionKey
import me.proton.core.key.domain.encryptData
import me.proton.core.key.domain.encryptText
import me.proton.core.key.domain.extension.primary
import me.proton.core.key.domain.publicKey
import me.proton.core.key.domain.signData
import me.proton.core.key.domain.signText
import me.proton.core.key.domain.useKeys
import me.proton.core.user.domain.UserManager
import java.nio.charset.StandardCharsets
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

class JoinCalendarUseCase @Inject constructor(
    private val logger: Logger,
    private val calendarsApi: CalendarsApi,
    private val calendarsRepository: CalendarsRepository,
    private val userManager: UserManager,
    private val cryptoContext: CryptoContext,
    private val crypto: Crypto,
    private val cacheCalendarPassphraseUseCase: CacheCalendarPassphraseUseCase,
    private val fetchEventsUseCase: FetchEventsUseCase,
    private val updateAlarmsUseCase: UpdateAlarmsUseCase,
    private val widgetRefresher: WidgetRefresher,
    private val valueStoreProvider: ValueStoreProvider,
    private val serverEventsApi: ServerEventsApi
): UseCase {

    companion object {
        const val WORKER_ID = "WORKER_ID_JOIN_CALENDAR"
    }

    suspend fun joinHolidaysCalendar(
        userId: UserId,
        holidaysCalendarEntity: HolidaysCalendarEntity,
        calendarColor: Int,
        defaultFullDayNotifications: List<VAlarm>?
    ): UseCase.Result {

        val defaultUserEmail = userManager.getUser(userId).email
        val address = userManager.getAddressesOrNull(userId)?.firstOrNull { address ->
            defaultUserEmail?.let { address.email == it } ?: address.canSend && address.canReceive
        } ?: return UseCase.Result.Error("JoinCalendarUseCase: No valid Address found")

        val memberAddressKey = address.keys.primary() ?: return UseCase.Result.Error("JoinCalendarUseCase: No valid Primary Address Key found for Address")

        // Encrypt the session key with the member primary key
        val sessionKey = SessionKey(
            Base64.decode(holidaysCalendarEntity.sessionKey.key),
            holidaysCalendarEntity.sessionKey.algorithm
        )
        val keyPacket = crypto.getKeyPacket(sessionKey, memberAddressKey.privateKey.publicKey(cryptoContext).key)

        // Sign the passphrase using the primary address key
        val tokenSignature = kotlin.runCatching { memberAddressKey.privateKey.signText(cryptoContext, holidaysCalendarEntity.passphrase) }.getOrNull() ?: return UseCase.Result.Error("JoinCalendarUseCase: could not sign token")

        val joinCalendarApiRequest = JoinCalendarApiRequest(
            signature = tokenSignature,
            passphraseKeyPacket = keyPacket!!,
            color = calendarColor.toHexColor(),
            defaultFullDayNotifications = defaultFullDayNotifications?.map {
                NotificationEntity(
                    type = if (it.action.isEmail) 0 else 1,
                    trigger = it.trigger.duration.toString()
                )
            }
        )

        return when (val joinCalendarResponse =
            calendarsApi.joinCalendar(userId, holidaysCalendarEntity.calendarId, address.addressId.id, joinCalendarApiRequest)
        ) {
            is ApiResponse.Success -> {
                val calendarId = holidaysCalendarEntity.calendarId
                // Save Calendar to DB
                calendarsRepository.persistCalendar(userId.id, joinCalendarResponse.data.calendar)
                // Save Calendar Settings
                calendarsRepository.persistCalendarSettings(joinCalendarResponse.data.calendarSettings)
                // Save Member
                val memberEntity = joinCalendarResponse.data.members.firstOrNull() ?: return UseCase.Result.Error("JoinCalendarUseCase: memberEntity was null")
                calendarsRepository.persistMember(memberEntity)
                // Save Calendar Key
                val calendarKeyEntity = joinCalendarResponse.data.keys.firstOrNull() ?: return UseCase.Result.Error("JoinCalendarUseCase: calendarKeyEntity was null")
                calendarsRepository.persistCalendarKey(calendarKeyEntity)
                // Save passphrase
                val passphraseEntity = joinCalendarResponse.data.passphrase
                calendarsRepository.persistPassphrase(passphraseEntity)

                // cache Passphrase
                val cachePassphraseResult =
                    cacheCalendarPassphraseUseCase.execute(userId, passphraseEntity.calendarId)

                return when (cachePassphraseResult) {
                    is UseCase.Result.Success<*> -> {

                        // Fetch shared calendar events
                        val displayTimeZoneId = calendarsRepository.selectCalendarUserSettings(userId.id)?.primaryTimezone
                            ?: ZoneId.systemDefault().id
                        val now = ZonedDateTime.now(ZoneId.of(displayTimeZoneId))
                        val fetchEventsResult = fetchEventsUseCase.execute(
                            userId,
                            listOf(calendarId),
                            now.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate(),
                            now.with(TemporalAdjusters.lastDayOfMonth()).toLocalDate(),
                            displayTimeZoneId
                        )

                        fetchEventsResult.first.ifSuccessAndLogErrors(logger) {
                            if (fetchEventsResult.second == null) {
                                logger.e("JoinCalendarUseCase: fetchEventsResult: null event list when Success")
                            } else {
                                fetchEventsResult.second?.let {
                                    calendarsRepository.persistEvents(*it.toTypedArray())
                                    updateAlarmsUseCase.execute(userId.id, it.map { it.id })
                                    widgetRefresher.refreshEventList()
                                }
                            }
                        }

                        // for each bootstrapped calendar, get its latest Event ID
                        val valueStore = valueStoreProvider.provideValueStore(userId.id)
                        val latestEventIdResponse = serverEventsApi.getLatestServerCalendarEvent(userId, calendarId)
                        if (latestEventIdResponse is ApiResponse.Success) {
                            valueStore.putStringInSet(ValueSet.LAST_SERVER_CALENDAR_EVENT_ID, calendarId, latestEventIdResponse.data.calendarEventId)
                        } else {
                            logger.e("JoinCalendarUseCase: could not get latest calendar server event ID response in BootstrapCalendarsUseCase")
                        }

                        UseCase.Result.Success(calendarId)
                    }
                    is UseCase.Result.Error -> {
                        logger.e("JoinCalendarUseCase: Error when caching Passphrase: ${cachePassphraseResult.message}")
                        cachePassphraseResult
                    }
                    is UseCase.Result.InvalidParams -> {
                        logger.e("JoinCalendarUseCase: InvalidParams when caching Passphrase: ${cachePassphraseResult.message}")
                        cachePassphraseResult
                    }
                }
            }
            is ApiResponse.Error -> {
                logger.e("api error join calendar: $joinCalendarResponse")
                UseCase.Result.Error(joinCalendarResponse.error)
            }
            is ApiResponse.Exception -> {
                logger.e("api error join calendar: $joinCalendarResponse")
                UseCase.Result.Error(joinCalendarResponse.exception.message ?: "(no exception message)")
            }
        }
    }
}