package me.proton.android.calendar.domain.usecase

import biweekly.component.VAlarm
import com.google.crypto.tink.subtle.Base64
import com.proton.gopenpgp.crypto.SessionKey
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import me.proton.android.calendar.WidgetRefresher
import me.proton.android.calendar.common.utils.getAddressesOrNull
import me.proton.android.calendar.common.utils.toHexColor
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.JoinCalendarApiRequest
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.data.entity.HolidaysCalendarEntity
import me.proton.android.calendar.data.entity.NotificationEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Crypto
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ValueSet
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.api.ServerEventsApi
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.domain.entity.UserId
import me.proton.core.key.domain.extension.primary
import me.proton.core.key.domain.publicKey
import me.proton.core.key.domain.signText
import me.proton.core.user.domain.UserManager
import java.time.LocalDate
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
        defaultFullDayNotifications: List<VAlarm>?,
        selectedDate: LocalDate,
        displayTimeZoneId: String
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
                val calendarId = joinCalendarResponse.data.calendar.id
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

                        coroutineScope {
                            val timeWindows = arrayListOf<Pair<LocalDate, LocalDate>>()
                            timeWindows.add(
                                Pair( // Current month
                                    selectedDate.with(TemporalAdjusters.firstDayOfMonth()),
                                    selectedDate.with(TemporalAdjusters.lastDayOfMonth())
                                )
                            )
                            val maxMonthsCached = 2L
                            for (i in 1L ..maxMonthsCached) {
                                timeWindows.add(
                                    Pair( // Current month + i month
                                        selectedDate.plusMonths(i).with(TemporalAdjusters.firstDayOfMonth()),
                                        selectedDate.plusMonths(i).with(TemporalAdjusters.lastDayOfMonth()).plusWeeks(if (i == maxMonthsCached) 1 else 0) // plus one week to max to cover offset days
                                    )
                                )
                                timeWindows.add(
                                    Pair( // Current month - i month
                                        selectedDate.minusMonths(i).with(TemporalAdjusters.firstDayOfMonth()).minusWeeks(if (i == maxMonthsCached) 1 else 0), // minus one week to min to cover offset days
                                        selectedDate.minusMonths(i).with(TemporalAdjusters.lastDayOfMonth())
                                    )
                                )
                            }
                            val resultsEventsMap = timeWindows.map {
                                async {
                                    val events = mutableListOf<EventEntity>()
                                    val fetchEventsResult = fetchEventsUseCase.execute(
                                        userId,
                                        listOf(calendarId),
                                        it.first,
                                        it.second,
                                        displayTimeZoneId
                                    )
                                    val result = if (fetchEventsResult.first is UseCase.Result.Success<*>) {
                                        if (fetchEventsResult.second == null) {
                                            logger.e("fetchEventsResult: null event list when Success")
                                        }

                                        fetchEventsResult.second?.let {
                                            events.addAll(it)
                                        }
                                        UseCase.Result.Success<Unit>()
                                    } else {
                                        UseCase.Result.Error("error fetching events")
                                    }
                                    Pair(result, events)
                                }
                            }.awaitAll()

                            val events = arrayListOf<EventEntity>()
                            resultsEventsMap.forEach {
                                if (it.first is UseCase.Result.Success<*>) {
                                    events.addAll(it.second)
                                }
                            }
                            calendarsRepository.persistEvents(*events.toTypedArray())
                            updateAlarmsUseCase.execute(userId.id, events.map { it.id })
                            widgetRefresher.refreshEventList()
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