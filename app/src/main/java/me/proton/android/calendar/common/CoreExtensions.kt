package me.proton.android.calendar.common

import me.proton.android.calendar.domain.Logger
import me.proton.core.domain.entity.SessionUserId
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.entity.User

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
