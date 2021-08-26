package me.proton.android.calendar.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.UserSettingsEntity
import me.proton.android.calendar.domain.UserSettingsRepository

class UserSettingsRepositoryImpl(
    private val database: AppDatabase
) : UserSettingsRepository {

    override suspend fun selectUserSettings(userId: String): UserSettingsEntity? {
        return database.deprecatedUserSettingsDao().select(userId)
    }

    override suspend fun persistUserSettings(userId: String, userSettings: UserSettingsEntity) {
        database.deprecatedUserSettingsDao().updateOrInsert(userSettings.copy(fkUserId = userId))
    }

    override suspend fun selectTimeFormat(userId: String): Int? {
        return database.deprecatedUserSettingsDao().selectTimeFormat(userId)
    }

    override fun flowTimeFormat(userId: String): Flow<Int?> {
        return database.deprecatedUserSettingsDao().flowTimeFormat(userId).distinctUntilChanged()
    }

    override fun flowWeekStart(userId: String): Flow<Int?> {
        return database.deprecatedUserSettingsDao().flowWeekStart(userId).distinctUntilChanged()
    }
}
