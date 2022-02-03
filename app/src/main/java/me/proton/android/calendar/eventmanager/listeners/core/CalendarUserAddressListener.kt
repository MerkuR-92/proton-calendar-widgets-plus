package me.proton.android.calendar.eventmanager.listeners.core

import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.eventmanager.listeners.CalendarBaseEventListener
import me.proton.core.eventmanager.domain.EventListener
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.eventmanager.domain.entity.Action
import me.proton.core.eventmanager.domain.entity.Event
import me.proton.core.eventmanager.domain.entity.EventsResponse
import me.proton.core.key.data.api.response.AddressResponse
import me.proton.core.user.data.UserAddressEvents
import me.proton.core.user.data.db.AddressDatabase
import me.proton.core.user.data.extension.toAddress
import me.proton.core.user.domain.entity.AddressId
import me.proton.core.user.domain.repository.UserAddressRepository
import me.proton.core.util.kotlin.deserializeOrNull
import me.proton.core.util.kotlin.toBoolean
import javax.inject.Inject
import javax.inject.Singleton

// TODO(CALAND-1617): Extend Core UserAddressEventListener instead when Core libs v5.0.1 or greater are merged
@Singleton
class CalendarUserAddressListener @Inject constructor(
    db: AddressDatabase,
    private val userAddressRepository: UserAddressRepository,
    private val calendarsRepository: CalendarsRepository,
) : CalendarBaseEventListener<String, AddressResponse>(db) {

    override val type = Type.Core
    override val order = 1

    override suspend fun deserializeEvents(
        config: EventManagerConfig,
        response: EventsResponse
    ): List<Event<String, AddressResponse>>? {
        return response.body.deserializeOrNull<UserAddressEvents>()?.addresses?.map {
            Event(requireNotNull(Action.map[it.action]), it.address.id, it.address)
        }
    }

    override suspend fun onCreateOrUpdate(config: EventManagerConfig, entities: List<AddressResponse>) {
        userAddressRepository.updateAddresses(entities.map { it.toAddress(config.userId) })
    }

    override suspend fun onUpdate(config: EventManagerConfig, entities: List<AddressResponse>) {
        super.onUpdate(config, entities)

        entities.forEach {
            calendarsRepository.refreshCalendarsFlagsForAddress(it.email, it.status.toBoolean(), config.userId.id)
        }
    }

    override suspend fun onDelete(config: EventManagerConfig, keys: List<String>) {
        userAddressRepository.deleteAddresses(keys.map { AddressId(it) })
    }

    override suspend fun onResetAll(config: EventManagerConfig) {
        userAddressRepository.deleteAllAddresses(config.userId)
        userAddressRepository.getAddresses(config.userId, refresh = true)
    }
}
