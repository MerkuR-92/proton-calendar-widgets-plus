package me.proton.android.calendar.domain.usecase

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import me.proton.android.calendar.common.getWeekStart
import me.proton.android.calendar.common.utils.ProtonUtilsImpl
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.domain.entity.UserId
import me.proton.core.usersettings.domain.repository.UserSettingsRepository
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Refreshes calendars from API,
 * then fetches the event metadata for all calendars, over the current cached window.
 * Should not really do anything visible other than show the loader,
 * but the idea is any potentially missing events will be fetched and inserted.
 */
class ManualRefreshUseCase @Inject constructor(
    private val calendarsRepository: CalendarsRepository,
    private val ensureCalendarsCompleteUseCase: EnsureCalendarsCompleteUseCase,
    private val refreshCalendarPassphraseUseCase: RefreshCalendarPassphraseUseCase,
    private val calendarsApi: CalendarsApi,
    private val accountManager: AccountManager,
    private val userSettingsRepository: UserSettingsRepository,
    private val database: AppDatabase,
    private val logger: Logger,
) : UseCase {

    suspend fun execute(): UseCase.Result {
        val userId = accountManager.getPrimaryUserId().firstOrNull() ?: return UseCase.Result.Error("User not logged in")

        val timeZoneId = calendarsRepository.selectCalendarUserSettingsPrimaryTimezone(userId.id)?.let {
            ZoneId.of(it)
        } ?: ZoneId.systemDefault()

        val weekStart = userSettingsRepository.getWeekStart(userId, database)
        val (fromDate, toDate) = ProtonUtilsImpl.getCachedMonthViewsTimeWindow(LocalDate.now(timeZoneId), weekStart)

        // refresh + bootstrap any calendar missing its settings, so it isn't dropped from the visible set
        ensureCalendarsCompleteUseCase.execute(userId)

        // resync keys + passphrase for owned calendars, so decryption isn't stuck on stale crypto
        syncOwnedCalendarCrypto(userId)

        val visibleIds = calendarsRepository.flowVisibleCalendarIds(userId.id).firstOrNull().orEmpty()
        if (visibleIds.isEmpty()) return UseCase.Result.Success<Unit>()

        calendarsRepository.fetchEvents(
            userId = userId,
            fromDate = fromDate,
            toDate = toDate,
            timeZoneId = timeZoneId.id,
            force = true,
        )
        return UseCase.Result.Success<Unit>()
    }

    private suspend fun syncOwnedCalendarCrypto(userId: UserId) = coroutineScope {
        calendarsRepository.selectAllCalendars(userId.id)
            .filter { it.isOwner }
            .map { calendar -> async { rebuildCrypto(userId, calendar.id) } }
            .awaitAll()
    }

    private suspend fun rebuildCrypto(userId: UserId, calendarId: String) {
        val keysResponse = calendarsApi.getKeys(userId, calendarId)
        if (keysResponse !is ApiResponse.Success || keysResponse.data.keys.isEmpty()) {
            logger.e("ManualRefresh: getKeys failed for $calendarId: $keysResponse")
            return
        }
        keysResponse.data.keys.forEach { calendarsRepository.persistCalendarKey(it) }

        val passphraseResult = refreshCalendarPassphraseUseCase(userId, calendarId)
        if (passphraseResult !is UseCase.Result.Success<*>) {
            logger.e("ManualRefresh: passphrase resync failed for $calendarId: $passphraseResult")
        }
    }
}
