package me.proton.android.calendar.mocks

import me.proton.android.calendar.data.entity.UserSettingsEntity
import me.proton.core.user.domain.entity.User

object UserMocks {

    fun getUserSettingsEntity(): UserSettingsEntity {
        return UserSettingsEntity(
            fkUserId = userId.id,
            weekStart = weekStart, // 0: Locale default, 1: Monday, 6: Saturday 7: Sunday
            dateFormat = dateFormat, // 0: Locale default, 1: DD_MM_YYYY, 2: MM_DD_YYYY, 3: YYYY_MM_DD
            timeFormat = timeFormat // 0: Locale default, 1: 24H, 2: 12H
        )
    }

    fun getUser(): User {
        return User(
            userId = userId,
            email = userEmail,
            name = userName,
            displayName = userDisplayName,
            currency = currency,
            credit = credit,
            usedSpace = usedSpace,
            maxSpace = maxSpace,
            maxUpload = maxUpload,
            role = role,
            private = private,
            services = services,
            subscribed = subscribed,
            delinquent = delinquent,
            keys = emptyList()
        )
    }
}
