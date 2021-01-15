package me.proton.android.calendar.data

import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.AddressEntity
import me.proton.android.calendar.data.entity.UserEntity
import me.proton.android.calendar.domain.UsersRepository
import me.proton.android.calendar.domain.model.Address
import me.proton.android.calendar.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import me.proton.android.calendar.data.entity.UserSettingsEntity
import timber.log.Timber

// TODO better name? move to separate package?
class UsersRepositoryImpl(
    private val database: AppDatabase /*TODO probably will need more than 1 API here*/,
    private val json: Json
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

    override suspend fun getUserEmails(userId: String): List<String>? {
        return database.addressesDao().select(userId).map {
            it.email
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
        address.fkUserId = userId // TODO if we scope repository with userId, this will not be needed
        database.addressesDao().insert(address)
    }

    override suspend fun updateAddress(userId: String, address: AddressEntity) {
        address.fkUserId = userId // TODO if we scope repository with userId, this will not be needed
        database.addressesDao().update(address)
    }

    override suspend fun deleteAddressById(id: String) {
        database.addressesDao().deleteById(id)
    }

    override suspend fun selectUserSettings(userId: String): UserSettingsEntity? {
        return database.userSettingsDao().select(userId)
    }

    override suspend fun persistUserSettings(userId: String, userSettings: UserSettingsEntity) {
        userSettings.fkUserId = userId
        database.userSettingsDao().updateOrInsert(userSettings)
    }

    override suspend fun hasReactivatedAddressKeys(address: AddressEntity): Boolean {
        val newAddress = address.toAddress(json)
        val dbAddress = database.addressesDao().selectById(address.id)?.toAddress(json) ?: return false
        dbAddress.keys.forEach { dbAddressKey ->
            if (dbAddressKey.active == 0 && newAddress.keys.firstOrNull { it.id == dbAddressKey.id }?.active == 1) return true
        }
        return false
    }

    override fun flowTimeFormat(userId: String): Flow<Int?> {
        return database.userSettingsDao().flowTimeFormat(userId).distinctUntilChanged()
    }

    override fun flowWeekStart(userId: String): Flow<Int?> {
        return database.userSettingsDao().flowWeekStart(userId).distinctUntilChanged()
    }

}
