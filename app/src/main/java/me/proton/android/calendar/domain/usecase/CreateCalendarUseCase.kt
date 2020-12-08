package me.proton.android.calendar.domain.usecase

import kotlinx.serialization.json.Json
import me.proton.android.calendar.common.DEFAULT_CALENDAR_COLOR
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.CreateCalendarApiRequest
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.UsersRepository
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.core.domain.entity.UserId

class CreateCalendarUseCase(
    private val logger: Logger,
    private val calendarsApi: CalendarsApi,
    private val database: AppDatabase,
    private val json: Json,
    private val usersRepository: UsersRepository,
    private val keySetupUseCase: KeySetupUseCase
): UseCase {

    suspend fun execute(userId: UserId, name: String, description: String = "", color: String = DEFAULT_CALENDAR_COLOR, display: Int = 1) : UseCase.Result {

        val user = usersRepository.selectUserById(userId.id)
        val email = user?.email ?: return UseCase.Result.Error("Email for user was null in CreateCalendarUseCase")
        val address = database.addressesDao().select(userId.id, email).firstOrNull()?.toAddress(json) ?: return UseCase.Result.Error("No address id found in CreateCalendarUseCase")

        val createCalendarApiRequest =
            CreateCalendarApiRequest(
                name = name,
                description = description,
                addressId = address.id,
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

                        val memberId = memberListApiResponse.data.members.firstOrNull()?.id ?: return UseCase.Result.Error("memberId was null in CreateCalendarUseCase")

                        val keySetupResult = keySetupUseCase.execute(
                            userId,
                            address.id,
                            calendarId,
                            address.primaryKey ?: address.keys[0],
                            memberId)

                        when (keySetupResult) {
                            is UseCase.Result.InvalidParams -> { logger.e("InvalidParams in CreateCalendarUseCase: ${keySetupResult.message}") }
                            is UseCase.Result.Error -> { logger.e("Error in CreateCalendarUseCase: ${keySetupResult.message}") }
                        }

                        return keySetupResult
                    }
                    is ApiResponse.Error -> UseCase.Result.Error(memberListApiResponse.error)
                    is ApiResponse.Exception -> UseCase.Result.Error(memberListApiResponse.exception.message ?: "(no exception message)")
                }
            }
            is ApiResponse.Error -> UseCase.Result.Error(createCalendarApiResponse.error)
            is ApiResponse.Exception -> UseCase.Result.Error(createCalendarApiResponse.exception.message ?: "(no exception message)")
        }
    }
}
