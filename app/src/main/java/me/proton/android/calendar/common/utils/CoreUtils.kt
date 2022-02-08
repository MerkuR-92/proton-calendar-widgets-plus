package me.proton.android.calendar.common.utils

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.core.domain.entity.UserId
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.entity.UserAddress

fun ApiResponse.Error.isTimeout(): Boolean {
    // TODO hardcoded string because there is no dedicated code for timeout
    return this.httpCode == 0 && this.errorCode == 0 && this.error == "timeout"
}

suspend fun UserManager.getAddressesOrNull(userId: UserId, refresh: Boolean = false): List<UserAddress>? {
    return kotlin.runCatching { getAddresses(userId, refresh) }.getOrNull()
}
