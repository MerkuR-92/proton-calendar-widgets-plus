package me.proton.android.calendar.eventmanager.listeners.core

import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.eventmanager.domain.entity.Action
import me.proton.core.eventmanager.domain.entity.Event
import me.proton.core.eventmanager.domain.entity.EventsResponse
import me.proton.core.key.data.api.response.AddressResponse
import me.proton.core.user.data.UserAddressEventListener
import me.proton.core.user.data.UserAddressEvents
import me.proton.core.user.data.db.AddressDatabase
import me.proton.core.user.domain.repository.UserAddressRepository
import me.proton.core.util.kotlin.deserializeOrNull
import me.proton.core.util.kotlin.toBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarUserAddressListener @Inject constructor(
    db: AddressDatabase,
    private val userAddressRepository: UserAddressRepository,
    private val calendarsRepository: CalendarsRepository,
) : UserAddressEventListener(db, userAddressRepository) {

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

    override suspend fun onUpdate(config: EventManagerConfig, entities: List<AddressResponse>) {
        super.onUpdate(config, entities)

        entities.forEach {
            calendarsRepository.refreshCalendarsFlagsForAddress(it.email, it.status.toBoolean(), config.userId.id)
        }
    }
}
