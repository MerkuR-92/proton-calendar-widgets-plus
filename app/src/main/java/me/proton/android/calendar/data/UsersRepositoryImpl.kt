package me.proton.android.calendar.data

import com.google.gson.Gson
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.AddressEntity
import me.proton.android.calendar.data.entity.UserEntity
import me.proton.android.calendar.domain.UsersRepository
import me.proton.android.calendar.domain.model.Address
import me.proton.android.calendar.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import timber.log.Timber

// TODO better name? move to separate package?
class UsersRepositoryImpl(
    private val database: AppDatabase /*TODO probably will need more than 1 API here*/,
    private val gson: Gson
) : UsersRepository {

    override fun usersFlow(): Flow<List<UserEntity>> {
        return database.usersDao().selectUsers().distinctUntilChanged()/*.map {
            it.map { it.toUser(gson) }
        }*/
    }

    override suspend fun persistUser(user: UserEntity) {
        Timber.d("persisting user entity ${user}")
        database.usersDao().insert(user)
    }

    override suspend fun selectUserById(userId: String): User? {
        return database.usersDao().selectUserById(userId)?.toUser(gson)
    }

    override fun addressesFlow(userId: String): Flow<List<Address>> {
        return database.addressesDao().selectFlow(userId).distinctUntilChanged().map {
            it.map { it.toAddress(gson) }
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

    override suspend fun deleteAddressById(id: String) {
        database.addressesDao().deleteById(id)
    }


}