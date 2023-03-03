package me.proton.android.calendar.eventmanager

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.model.Calendar
import me.proton.core.account.domain.entity.AccountState
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.accountmanager.domain.getAccounts
import me.proton.core.accountmanager.presentation.observe
import me.proton.core.accountmanager.presentation.onAccountDisabled
import me.proton.core.accountmanager.presentation.onAccountReady
import me.proton.core.domain.entity.UserId
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.eventmanager.domain.EventManagerConfig.Core
import me.proton.core.eventmanager.domain.EventManagerProvider
import me.proton.core.presentation.app.AppLifecycleProvider
import me.proton.core.util.kotlin.CoroutineScopeProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarEventManagerStarter @Inject constructor(
    private val appLifecycleProvider: AppLifecycleProvider,
    private val eventManagerProvider: EventManagerProvider,
    private val accountManager: AccountManager,
    private val calendarsRepository: CalendarsRepository,
    private val scopeProvider: CoroutineScopeProvider
) {
    fun start() {
        accountManager.observe(appLifecycleProvider.lifecycle)
            .onAccountReady { eventManagerProvider.get(Core(it.userId)).start() }
            .onAccountDisabled { eventManagerProvider.get(Core(it.userId)).stop() }

        accountManager.getAccounts(AccountState.Ready)
            .flatMapLatest { accounts -> observeAllCalendarsForUsers(accounts.map { it.userId }) }
            .onEach { allUserCalendars ->
                for ((userId, calendars) in allUserCalendars) {
                    val managers = eventManagerProvider.getAll(userId).filter { it.config.listenerType == me.proton.core.eventmanager.domain.EventListener.Type.Calendar }
                    // Stop all calendars managers, for this userId.
                    managers.forEach { manager -> manager.stop() }
                    // Start all enabled calendars, for this userId.
                    calendars.filter { it.display }.forEach {
                        eventManagerProvider.get(EventManagerConfig.Calendar(userId, it.id)).start()
                    }
                }
            }.launchIn(scopeProvider.GlobalDefaultSupervisedScope)
    }

    private fun observeAllCalendarsForUsers(userIds: List<UserId>): Flow<Map<UserId, Set<Calendar>>> =
        combine(
            userIds.map { userId -> observeUserCalendars(userId).map { userId to it } }
        ) {
            it.toMap()
        }

    private fun observeUserCalendars(userId: UserId): Flow<Set<Calendar>> =
        combine(
            calendarsRepository.flowUserCalendars(userId.id),
            calendarsRepository.flowSubscribedCalendars(userId.id),
            calendarsRepository.flowHolidaysCalendars(userId.id)
        ) { calendars, subscriptions, holidays ->
            (calendars + subscriptions + holidays).toSet()
        }.distinctUntilChanged()
}
