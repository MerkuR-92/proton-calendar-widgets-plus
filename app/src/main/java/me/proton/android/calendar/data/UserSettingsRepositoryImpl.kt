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
        return database.userSettingsDao().select(userId)
    }

    override suspend fun persistUserSettings(userId: String, userSettings: UserSettingsEntity) {
        database.userSettingsDao().updateOrInsert(userSettings.copy(fkUserId = userId))
    }

    override suspend fun selectTimeFormat(userId: String): Int? {
        return database.userSettingsDao().selectTimeFormat(userId)
    }

    override fun flowTimeFormat(userId: String): Flow<Int?> {
        return database.userSettingsDao().flowTimeFormat(userId).distinctUntilChanged()
    }

    override fun flowWeekStart(userId: String): Flow<Int?> {
        return database.userSettingsDao().flowWeekStart(userId).distinctUntilChanged()
    }
}
