package me.proton.android.calendar.eventmanager.listeners.core

import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.user.data.UserAddressEventListener
import me.proton.core.user.data.db.AddressDatabase
import me.proton.core.user.domain.repository.UserAddressRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarUserAddressListener @Inject constructor(
    db: AddressDatabase,
    private val userAddressRepository: UserAddressRepository,
    private val calendarsRepository: CalendarsRepository,
) : UserAddressEventListener(db, userAddressRepository) {

    override val type = Type.Core
    override val order = 2

    override suspend fun onSuccess(config: EventManagerConfig) {
        super.onSuccess(config)
        // TODO We could probably optimise by only refreshing members linked to addressIds that changed
        calendarsRepository.refreshMembersFlags(config.userId)
    }
}
