package me.proton.android.calendar.domain.model

import com.google.gson.JsonElement
import kotlinx.serialization.SerialName

data class User(
    override val id: String,
    val keys: List<UserKey>,
    val email: String?,
    val displayName: String?,
    val subscribed: Int,
    val usedSpace: Long,
    val maxSpace: Long,
    val delinquent: Int
) : BaseModel() {

    val primaryKey = keys.find { it.primary == 1 }
    val isPaid: Boolean get() = subscribed > 0
    val isFree: Boolean get() = subscribed <= 0
}

// Delinquent values
object Delinquent {
    const val NOT_UNPAID = 0
    const val UNPAID_AVAILABLE = 1
    const val UNPAID_OVERDUE = 2
    const val UNPAID_DELINQUENT = 3
    const val UNPAID_NO_RECEIVE = 4
}
