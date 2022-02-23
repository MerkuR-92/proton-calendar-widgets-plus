package me.proton.android.calendar.common

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import me.proton.android.calendar.data.entity.UserSettingsEntity
import me.proton.android.calendar.domain.Logger
import me.proton.core.domain.arch.DataResult
import me.proton.core.domain.entity.SessionUserId
import me.proton.core.domain.entity.UserId
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.entity.User
import me.proton.core.usersettings.domain.repository.UserSettingsRepository

suspend fun UserManager.getUserOrNull(
    sessionUserId: SessionUserId,
    logger: Logger? = null,
    refresh: Boolean = false
): User? {
    return kotlin.runCatching {
        this.getUser(sessionUserId, refresh)
    }.getOrElse {
        logger?.e("Exception in UserManager.getUserOrNull: ${it.message}", it)
        null
    }
}

fun UserSettingsRepository.getTimeFormatFlow(userId: UserId): Flow<Int> {
    return this.getUserSettingsFlow(userId).map { if (it is DataResult.Success) it.value.timeFormat?.value ?: 0 else 0 /* locale default */ }.distinctUntilChanged()
}

suspend fun UserSettingsRepository.getTimeFormat(userId: UserId): Int {
    return this.getUserSettingsEntity(userId).timeFormat
}

fun UserSettingsRepository.getWeekStartFlow(userId: UserId): Flow<Int> {
    return this.getUserSettingsFlow(userId).map { if (it is DataResult.Success) it.value.weekStart?.value ?: 0 else 0 /* locale default = 0 */ }.distinctUntilChanged()
}

suspend fun UserSettingsRepository.getWeekStart(userId: UserId): Int {
    return this.getUserSettingsEntity(userId).weekStart
}

suspend fun UserSettingsRepository.containsUserSettings(userId: UserId): Boolean {
    return this.getUserSettingsFlow(userId).map { if (it is DataResult.Success) it.value else null }.firstOrNull() != null
}

suspend fun UserSettingsRepository.getUserSettingsEntity(userId: UserId): UserSettingsEntity {
    val coreUserSettings = this.getUserSettingsFlow(userId).map { if (it is DataResult.Success) it.value else null }.firstOrNull()

    return UserSettingsEntity(
        fkUserId = userId.id,
        weekStart = coreUserSettings?.weekStart?.value ?: 0,
        dateFormat = coreUserSettings?.dateFormat?.value ?: 0,
        timeFormat = coreUserSettings?.timeFormat?.value ?: 0
    )
}
