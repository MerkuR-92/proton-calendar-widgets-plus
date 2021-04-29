package me.proton.android.calendar.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import me.proton.android.calendar.common.MAX_EMAILS_PER_QUERY
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.AddressEntity
import me.proton.android.calendar.data.entity.UserEntity
import me.proton.android.calendar.data.entity.UserSettingsEntity
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.UsersRepository
import me.proton.android.calendar.domain.api.AddressesApi
import me.proton.android.calendar.domain.model.Address
import me.proton.android.calendar.domain.model.User
import me.proton.core.domain.entity.UserId
import timber.log.Timber

// TODO better name? move to separate package?
class UsersRepositoryImpl(
    private val database: AppDatabase /*TODO probably will need more than 1 API here*/,
    private val json: Json,
    private val addressesApi: AddressesApi,
    private val logger: Logger
) : UsersRepository {

    override fun usersFlow(): Flow<List<UserEntity>> {
        return database.usersDao().usersFlow().distinctUntilChanged()
    }

    override suspend fun persistUser(user: UserEntity) {
        Timber.d("persisting user entity ${user}")
        database.usersDao().updateOrInsert(user)
    }

    override suspend fun updateUser(user: UserEntity) {
        database.usersDao().update(user)
    }

    override suspend fun deleteUserById(userId: String) {
        database.usersDao().deleteById(userId)
    }

    override suspend fun selectUserById(userId: String): User? {
        return database.usersDao().selectUserById(userId)?.toUser()
    }

    override suspend fun getUserAddresses(userId: String): List<Address>? {
        return database.addressesDao().select(userId).map {
            it.toAddress(json)
        }
    }

    override fun addressesFlow(userId: String): Flow<List<Address>> {
        return database.addressesDao().selectFlow(userId).distinctUntilChanged().map {
            it.map { it.toAddress(json) }
        }
    }

    override suspend fun selectAddresses(userId: String): List<Address> {
        TODO("Not yet implemented")
    }

    override suspend fun persistAddress(userId: String, address: AddressEntity) {
        Timber.d("persisting address entity for user ${userId} -> ${address}")
        database.addressesDao().insert(address.copy(fkUserId = userId)) // TODO if we scope repository with userId, this will not be needed
    }

    override suspend fun updateAddress(userId: String, address: AddressEntity) {
        database.addressesDao().update(address.copy(fkUserId = userId)) // TODO if we scope repository with userId, this will not be needed
    }

    override suspend fun deleteAddressById(id: String) {
        database.addressesDao().deleteById(id)
    }

    override suspend fun selectUserSettings(userId: String): UserSettingsEntity? {
        return database.userSettingsDao().select(userId)
    }

    override suspend fun persistUserSettings(userId: String, userSettings: UserSettingsEntity) {
        database.userSettingsDao().updateOrInsert(userSettings.copy(fkUserId = userId))
    }

    override suspend fun hasReactivatedAddressKeys(address: AddressEntity): Boolean {
        val newAddress = address.toAddress(json)
        val dbAddress = database.addressesDao().selectById(address.id)?.toAddress(json) ?: return false
        dbAddress.keys.forEach { dbAddressKey ->
            if (dbAddressKey.active == 0 && newAddress.keys.firstOrNull { it.id == dbAddressKey.id }?.active == 1) return true
        }
        return false
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
