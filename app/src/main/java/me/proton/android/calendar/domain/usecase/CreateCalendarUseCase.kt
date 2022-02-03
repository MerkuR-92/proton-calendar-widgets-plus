package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.common.DEFAULT_CALENDAR_COLOR
import me.proton.android.calendar.common.utils.AndroidUtils.tryCast
import me.proton.android.calendar.common.utils.getAddressesOrNull
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.CreateCalendarApiRequest
import me.proton.android.calendar.data.api.valueOrNullAndLogErrors
import me.proton.android.calendar.data.entity.CalendarKeyEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.core.domain.entity.UserId
import me.proton.core.key.domain.extension.primary
import me.proton.core.user.domain.UserManager
import me.proton.core.util.kotlin.takeIfNotEmpty
import javax.inject.Inject

class CreateCalendarUseCase @Inject constructor(
    private val logger: Logger,
    private val calendarsApi: CalendarsApi,
    private val keySetupUseCase: KeySetupUseCase,
    private val userManager: UserManager,
    private val calendarsRepository: CalendarsRepository,
    private val cacheCalendarPassphraseUseCase: CacheCalendarPassphraseUseCase
): UseCase {

    suspend fun execute(userId: UserId, name: String, description: String = "", color: String = DEFAULT_CALENDAR_COLOR, display: Int = 1, email: String? = null) : UseCase.Result {

        val address = userManager.getAddressesOrNull(userId, refresh = true)?.firstOrNull { address ->
            email?.let { address.email == it } ?: address.canSend && address.canReceive
        } ?: return UseCase.Result.Error("CreateCalendarUseCase: No valid Address found")

        val createCalendarApiRequest =
            CreateCalendarApiRequest(
                name = name,
                description = description,
                addressId = address.addressId.id,
                color = color,
                display = display
            )

        // Create calendar
        return when (val createCalendarApiResponse = calendarsApi.createCalendar(userId, createCalendarApiRequest)) {
            is ApiResponse.Success -> {

                val calendarId = createCalendarApiResponse.data.calendar.id

                // Get member created for address
                return when (val memberListApiResponse = calendarsApi.getMemberList(userId, calendarId)) {
                    is ApiResponse.Success -> {

                        // Save calendar in DB
                        calendarsRepository.persistCalendar(userId.id, createCalendarApiResponse.data.calendar)

                        // Save member in DB
                        val memberEntity = memberListApiResponse.data.members.firstOrNull() ?: return UseCase.Result.Error("CreateCalendarUseCase: member was null")
                        calendarsRepository.persistMember(memberEntity)

                        val keySetupResult = keySetupUseCase.execute(
                            userId,
                            address.addressId.id,
                            address.keys.primary() ?: return UseCase.Result.Error("CreateCalendarUseCase: No valid Primary Address Key found for Address"),
                            calendarId,
                            memberEntity.id
                        )

                        when (keySetupResult) {
                            is UseCase.Result.Success<*> -> {

                                // save CalendarKey in DB
                                keySetupResult.returnValue.tryCast<CalendarKeyEntity> {
                                    calendarsRepository.persistCalendarKey(this)
                                }

                                // TODO use another server call to only get the active Passphrase
                                // fetch CalendarPassphrase from server
                                val passphrases = calendarsApi.getPassphrases(userId, calendarId)
                                    .valueOrNullAndLogErrors(logger)?.passphrases?.takeIfNotEmpty()
                                    ?: return UseCase.Result.Error("CreateCalendarUseCase: could not fetch Calendar Passphrases")

                                if (passphrases.size > 1) logger.e("CreateCalendarUseCase: got more than 1 CalendarPassphrases (${passphrases.size})")

                                val passphraseEntity = passphrases.first()

                                // save Passphrase in DB
                                calendarsRepository.persistPassphrase(passphraseEntity)

                                // cache Passphrase
                                val cachePassphraseResult =
                                    cacheCalendarPassphraseUseCase.execute(userId, passphraseEntity.calendarId)

                                return when (cachePassphraseResult) {
                                    is UseCase.Result.Success<*> -> {
                                        UseCase.Result.Success(calendarId)
                                    }
                                    is UseCase.Result.Error -> {
                                        logger.e("CreateCalendarUseCase: Error in KeySetupUseCase when caching Passphrase: ${cachePassphraseResult.message}")
                                        cachePassphraseResult
                                    }
                                    is UseCase.Result.InvalidParams -> {
                                        logger.e("CreateCalendarUseCase: InvalidParams in KeySetupUseCase when caching Passphrase: ${cachePassphraseResult.message}")
                                        cachePassphraseResult
                                    }
                                }

                            }
                            is UseCase.Result.InvalidParams -> {
                                logger.e("CreateCalendarUseCase: InvalidParams in KeySetupUseCase: ${keySetupResult.message}")
                                keySetupResult
                            }
                            is UseCase.Result.Error -> {
                                logger.e("CreateCalendarUseCase: Error in KeySetupUseCase: ${keySetupResult.message}")
                                keySetupResult
                            }
                        }

                    }
                    is ApiResponse.Error -> UseCase.Result.Error("CreateCalendarUseCase: error fetching members: ${memberListApiResponse.error}")
                    is ApiResponse.Exception -> UseCase.Result.Error("CreateCalendarUseCase: error fetching members: ${memberListApiResponse.exception.message ?: "(no exception message)"}")
                }
            }
            is ApiResponse.Error -> UseCase.Result.Error("CreateCalendarUseCase: error creating calendar: ${createCalendarApiResponse.error}")
            is ApiResponse.Exception -> UseCase.Result.Error("CreateCalendarUseCase: error creating calendar: ${createCalendarApiResponse.exception.message ?: "(no exception message)"}")
        }
    }
}
