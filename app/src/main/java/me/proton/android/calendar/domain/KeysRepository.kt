package me.proton.android.calendar.domain

import me.proton.android.calendar.data.entity.AddressEntity
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.data.entity.UserEntity
import me.proton.android.calendar.domain.model.Address
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.User
import kotlinx.coroutines.flow.Flow

/**
 * Manages PublicKeys of email addresses.
 */
interface KeysRepository {

    suspend fun persistKeys(email: String) // TODO

    suspend fun selectAddresses(userId: String): List<Address>

}
