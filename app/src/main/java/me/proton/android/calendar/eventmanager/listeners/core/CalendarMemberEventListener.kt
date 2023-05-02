package me.proton.android.calendar.eventmanager.listeners.core

import androidx.annotation.VisibleForTesting
import me.proton.android.calendar.common.utils.getAddressesOrNull
import me.proton.android.calendar.data.api.ServerCoreEventsApiResponse
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.MemberEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.KeySetupUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.eventmanager.listeners.CalendarBaseEventListener
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.eventmanager.domain.entity.Action
import me.proton.core.eventmanager.domain.entity.Event
import me.proton.core.eventmanager.domain.entity.EventsResponse
import me.proton.core.user.domain.UserManager
import me.proton.core.util.kotlin.deserializeOrNull
import javax.inject.Inject

class CalendarMemberEventListener @Inject constructor(
    db: AppDatabase,
    private val calendarsRepository: CalendarsRepository,
    private val logger: Logger,
    private val keySetupUseCase: KeySetupUseCase,
    private val userManager: UserManager
): CalendarBaseEventListener<String, MemberEntity>(db) {
    override val order: Int = 2
    override val type: Type = Type.Core

    override suspend fun deserializeEvents(
        config: EventManagerConfig,
        response: EventsResponse
    ): List<Event<String, MemberEntity>>? {
        return response.body.deserializeOrNull<ServerCoreEventsApiResponse>()?.calendarMembers?.map {
            Event(requireNotNull(Action.map[it.action]), it.id, it.member)
        }?.let { handleIncompleteKeys(config, it) }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal suspend fun handleIncompleteKeys(
        config: EventManagerConfig,
        events: List<Event<String, MemberEntity>>
    ): List<Event<String, MemberEntity>> {
        // Try to complete key setup for calendar/member:
        // - we use newly updated member fetched from API if it succeeds
        // - we use member from server event if it fails, manually changing the flags
        return events.map { event ->
            val entity = event.entity ?: return@map event

            // incomplete key setup flag is set, should only happen when newly created calendar wasn't setup properly
            // by another frontend client
            logger.d("handleIncompleteKeys member flags = ${entity.flags}")
            val updatedCalendar = if (entity.hasIncompleteKeySetup) {
                when (val result = keySetupUseCase.execute(config.userId, entity.calendarId)) {
                    is UseCase.Result.Success<*> -> {
                        val fetchedMember = calendarsRepository.fetchMembers(config.userId, entity.calendarId)?.firstOrNull()
                        if (fetchedMember == null) {
                            logger.e("CalendarMemberEventListener: error getting member from API after keySetupUseCase success")

                            var calendarFlags = entity.flags
                            calendarFlags -= MemberEntity.CalendarFlags.INCOMPLETE_SETUP.value
                            // if calendar is inactive and no other error flags are set, make it active
                            if (calendarFlags == 0) calendarFlags = MemberEntity.CalendarFlags.ACTIVE.value

                            entity.copy(flags = calendarFlags)
                        } else {
                            fetchedMember
                        }
                    }
                    is UseCase.Result.InvalidParams -> {
                        logger.e("CalendarMemberEventListener: keySetupResult invalid params: ${result.message}")
                        entity
                    }
                    is UseCase.Result.Error -> {
                        // Try and fetch the Member to check that the key setup wasn't done by another client in the meantime
                        val fetchedMember = calendarsRepository.fetchMembers(config.userId, entity.calendarId)?.firstOrNull()
                        if (fetchedMember == null || fetchedMember.hasIncompleteKeySetup) {
                            logger.e("CalendarMemberEventListener: keySetupResult error: ${result.message}")
                            entity
                        } else {
                            fetchedMember
                        }
                    }
                }
            } else {
                entity
            }
            event.copy(entity = updatedCalendar)
        }
    }

    override suspend fun onCreateOrUpdate(config: EventManagerConfig, entities: List<MemberEntity>) {
        entities.forEach {
            if (calendarsRepository.hasCalendar(it.calendarId)) {
                calendarsRepository.persistMember(it)
            } else {
                logger.i("action CREATE/UPDATE for calendarMember ${it.id} in deleted calendar ${it.calendarId}")
            }
        }
    }

    override suspend fun onDelete(config: EventManagerConfig, keys: List<String>) {
        super.onDelete(config, keys)

        // Get user addresses emails to compare with members email
        val addresses = userManager.getAddressesOrNull(config.userId)
        keys.forEach {
            val member = calendarsRepository.selectMemberById(it)
            // We first delete Member
            calendarsRepository.deleteMemberById(it)
            member?.let {
                val memberAddress = calendarsRepository.getAddressForMember(config.userId, member, addresses)
                if (memberAddress != null) {
                    // If member belongs to user, delete the calendar linked to it
                    calendarsRepository.deleteCalendarById(member.calendarId)
                }
            }
        }
    }
}
