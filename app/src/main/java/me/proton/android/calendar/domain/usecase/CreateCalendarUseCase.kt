package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.common.DEFAULT_CALENDAR_COLOR
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.CreateCalendarApiRequest
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.core.domain.entity.UserId
import me.proton.core.user.domain.UserManager

class CreateCalendarUseCase(
    private val logger: Logger,
    private val calendarsApi: CalendarsApi,
    private val keySetupUseCase: KeySetupUseCase,
    private val userManager: UserManager
): UseCase {

    suspend fun execute(userId: UserId, name: String, description: String = "", color: String = DEFAULT_CALENDAR_COLOR, display: Int = 1) : UseCase.Result {

        val address = userManager.getAddresses(userId, refresh = true).firstOrNull {
            it.canSend && it.canReceive
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

                        val memberId = memberListApiResponse.data.members.firstOrNull()?.id ?: return UseCase.Result.Error("CreateCalendarUseCase: memberId was null")

                        val keySetupResult = keySetupUseCase.execute(
                            userId,
                            address.addressId.id,
                            calendarId,
                            memberId)

                        when (keySetupResult) {
                            is UseCase.Result.InvalidParams -> { logger.e("CreateCalendarUseCase: InvalidParams in KeySetupUseCase: ${keySetupResult.message}") }
                            is UseCase.Result.Error -> { logger.e("CreateCalendarUseCase: Error in KeySetupUseCase: ${keySetupResult.message}") }
                        }

                        return keySetupResult
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
