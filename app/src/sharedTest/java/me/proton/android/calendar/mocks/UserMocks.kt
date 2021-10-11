package me.proton.android.calendar.mocks

import me.proton.android.calendar.data.entity.UserSettingsEntity
import me.proton.android.calendar.domain.model.SendPreferences
import me.proton.core.key.domain.extension.areAllLocked
import me.proton.core.key.domain.useKeys
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.entity.AddressId
import me.proton.core.user.domain.entity.AddressType
import me.proton.core.user.domain.entity.User
import me.proton.core.user.domain.entity.UserAddress

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

    fun getUserAddress(canSendParam: Boolean? = null, canReceiveParam: Boolean? = null, enabledParam: Boolean? = null): UserAddress {
        return UserAddress(
            userId = userId,
            addressId = addressId,
            email = userEmail,
            displayName = userDisplayName,
            signature = null, // TODO
            domainId = null, // TODO
            canSend = canSendParam ?: canSend,
            canReceive = canReceiveParam ?: canReceive,
            enabled = enabledParam ?: enabled,
            type = addressType,
            order = order,
            keys = emptyList() // TODO
        )
    }

    fun getSendPreferences(): SendPreferences {
        return SendPreferences(
            encrypt = encrypt,
            sign = sign,
            pgpScheme = pgpScheme,
            mimeType = mimeType,
            publicKey = publicKey
        )
    }
}
