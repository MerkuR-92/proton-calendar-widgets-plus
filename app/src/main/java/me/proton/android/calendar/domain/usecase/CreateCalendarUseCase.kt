package me.proton.android.calendar.domain.usecase

import com.google.gson.Gson
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
    private val gson: Gson,
    private val usersRepository: UsersRepository,
    private val keySetupUseCase: KeySetupUseCase
): UseCase {

    companion object {
        const val WORKER_ID = "CREATE_CALENDAR"
    }

    suspend fun execute(userId: UserId, name: String, description: String = "", color: String = "#C26CC7", display: Int = 1) : UseCase.Result {

        val user = usersRepository.selectUserById(userId.id)
        val email = user?.email ?: return UseCase.Result.Error("Email for user was null in CreateCalendarUseCase")
        val address = database.addressesDao().select(userId.id, email).firstOrNull()?.toAddress(gson) ?: return UseCase.Result.Error("No address id found in CreateCalendarUseCase")

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
                        return keySetupUseCase.execute(
                            userId,
                            address.id,
                            calendarId,
                            address.primaryKey ?: address.keys[0],
                            memberId)
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
