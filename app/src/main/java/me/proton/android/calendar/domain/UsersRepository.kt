package me.proton.android.calendar.domain

import me.proton.android.calendar.domain.model.Address
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.User
import kotlinx.coroutines.flow.Flow
import me.proton.android.calendar.data.entity.*
import me.proton.core.domain.entity.UserId

/**
 * Manages Users, UserKeys, Addresses etc.
 */
interface UsersRepository {

    // users
    fun usersFlow(): Flow<List<UserEntity>>

    suspend fun persistUser(user: UserEntity)

    suspend fun updateUser(user: UserEntity)

    suspend fun deleteUserById(userId: String)

    suspend fun selectUserById(userId: String): User?

    suspend fun getUserAddresses(userId: String): List<Address>?

    // addresses
    fun addressesFlow(userId: String): Flow<List<Address>>

    suspend fun selectAddresses(userId: String): List<Address>

    suspend fun persistAddress(userId: String, address: AddressEntity)

    suspend fun updateAddress(userId: String, address: AddressEntity)

    suspend fun deleteAddressById(id: String)

    // user settings
    suspend fun selectUserSettings(userId: String): UserSettingsEntity?

    suspend fun persistUserSettings(userId: String, userSettings: UserSettingsEntity)

    suspend fun hasReactivatedAddressKeys(address: AddressEntity): Boolean

    suspend fun selectTimeFormat(userId: String): Int?

    fun flowTimeFormat(userId: String): Flow<Int?>

    fun flowWeekStart(userId: String): Flow<Int?>
}
