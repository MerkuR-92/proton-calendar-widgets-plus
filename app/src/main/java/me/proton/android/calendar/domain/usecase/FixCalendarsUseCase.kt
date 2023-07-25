package me.proton.android.calendar.domain.usecase

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import me.proton.android.calendar.common.utils.getAddressesOrNull
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ValueSet
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.core.domain.entity.UserId
import me.proton.core.user.domain.UserAddressManager
import me.proton.core.util.kotlin.takeIfNotEmpty
import java.time.ZoneId
import javax.inject.Inject

class FixCalendarsUseCase @Inject constructor(
    private val logger: Logger,
    private val database: AppDatabase,
    private val json: Json,
    private val userAddressManager: UserAddressManager,
    private val bootstrapCalendarUseCase: BootstrapCalendarUseCase,
    private val valueStoreProvider: ValueStoreProvider
): UseCase {

    suspend fun execute(userId: UserId): UseCase.Result {
        val calendarEntities = database.calendarsDao().selectCalendars(userId.id)
        val calendarsToBootstrap = arrayListOf<CalendarEntity>()
        calendarEntities.forEach { calendarEntity ->
            val calendarId = calendarEntity.id

            // Check calendar private keys
            database.calendarKeysDao().select(calendarId).filter {
                it.isActive
            }.map {
                it.privateKey
            }.takeIfNotEmpty() ?: run {
                logger.e("FixCalendarsUseCase, calendarKey was null")
                calendarsToBootstrap.add(calendarEntity)
                return@forEach
            }

            // Check calendar passphrase
            val calendarPassphrase = database.passphrasesDao().select(calendarId).map {
                it.toPassphrase(json)
            }.firstOrNull {
                it.isActive
            } ?: run {
                logger.e("FixCalendarsUseCase, calendarPassphrase was null")
                calendarsToBootstrap.add(calendarEntity)
                return@forEach
            }

            // Check calendar key passphrase
            valueStoreProvider.provideValueStore(userId.id).getStringFromSet(
                ValueSet.CALENDAR_PASSPHRASE,
                calendarPassphrase.id
            ) ?: run {
                logger.e("FixCalendarsUseCase, keyPassphrase was null")
                calendarsToBootstrap.add(calendarEntity)
                return@forEach
            }
        }

        // Try to get addresses and force refresh if null
        val userAddresses = userAddressManager.getAddressesOrNull(userId)
            ?: userAddressManager.getAddressesOrNull(userId, refresh = true)
        if (userAddresses == null) {
            // If user addresses are still null, log and return
            logger.e("FixCalendarsUseCase, userAddresses are null")
            return UseCase.Result.Error("FixCalendarsUseCase, userAddresses are null")
        }

        // Retry bootstrap for calendars with missing data
        if (calendarsToBootstrap.isNotEmpty()) {
            val timezone = database.calendarUserSettingsDao().select(userId.id)?.primaryTimezone
                ?: ZoneId.systemDefault().id
            coroutineScope {
                calendarsToBootstrap.map {
                    async {
                        bootstrapCalendarUseCase.executeBootstrap(it, userId, timezone, userAddresses)
                    }
                }.awaitAll()
            }
        }
        return UseCase.Result.Success<Unit>()
    }

}
