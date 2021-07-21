package me.proton.android.calendar.domain

import kotlinx.coroutines.flow.Flow
import me.proton.android.calendar.data.entity.UserSettingsEntity

interface UserSettingsRepository {

    // user settings
    suspend fun selectUserSettings(userId: String): UserSettingsEntity?

    suspend fun persistUserSettings(userId: String, userSettings: UserSettingsEntity)

    suspend fun selectTimeFormat(userId: String): Int?

    fun flowTimeFormat(userId: String): Flow<Int?>

    fun flowWeekStart(userId: String): Flow<Int?>
}
