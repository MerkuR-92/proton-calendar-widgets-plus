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
 * Manages Users, UserKeys, Addresses etc.
 */
interface UsersRepository {

    // users
    fun usersFlow(): Flow<List<UserEntity>>

    suspend fun persistUser(user: UserEntity)

    suspend fun selectUserById(userId: String): User?

    // addresses
    fun addressesFlow(userId: String): Flow<List<Address>>

    suspend fun selectAddresses(userId: String): List<Address>

    suspend fun persistAddress(userId: String, address: AddressEntity)

    suspend fun deleteAddressById(id: String)

}