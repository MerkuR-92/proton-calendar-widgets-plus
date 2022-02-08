package me.proton.android.calendar.eventmanager

import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.accountmanager.presentation.observe
import me.proton.core.accountmanager.presentation.onAccountReady
import me.proton.core.accountmanager.presentation.onAccountRemoved
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.eventmanager.domain.EventManagerProvider
import me.proton.core.presentation.app.AppLifecycleProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarEventManagerStarter @Inject constructor(
    private val appLifecycleProvider: AppLifecycleProvider,
    private val eventManagerProvider: EventManagerProvider,
    private val accountManager: AccountManager,
    private val calendarsRepository: CalendarsRepository,
) {
    private val observedCalendarIdsForUser = mutableMapOf<String, Set<String>>()
    private var observedCalendarScopes = mutableMapOf<String, CoroutineScope>()

    fun start() {
        accountManager.observe(appLifecycleProvider.lifecycle, minActiveState = Lifecycle.State.CREATED)
            .onAccountReady { account ->
                val userId = account.userId
                eventManagerProvider.get(EventManagerConfig.Core(userId)).start()

                val scope = CoroutineScope(Dispatchers.Default).also {
                    observedCalendarScopes[userId.id] = it
                }

                combine(
                    calendarsRepository.flowUserCalendars(userId.id),
                    calendarsRepository.flowSubscribedCalendars(userId.id),
                ) { calendars, subscriptions ->
                    (calendars + subscriptions).map { it.id }
                }
                    .distinctUntilChanged()
                    .onEach { calendarIds ->
                        val newCalendarIds = calendarIds.map { it }.toSet()
                        val oldCalendarIds = observedCalendarIdsForUser[userId.id].orEmpty()
                        val calendarIdsToRemove = oldCalendarIds - newCalendarIds
                        val calendarsToAdd = newCalendarIds - oldCalendarIds
                        calendarIdsToRemove.forEach {
                            eventManagerProvider.get(EventManagerConfig.Calendar(userId, it)).stop()
                        }
                        calendarsToAdd.forEach {
                            eventManagerProvider.get(EventManagerConfig.Calendar(userId, it)).start()
                        }
                        observedCalendarIdsForUser[userId.id] = newCalendarIds
                    }
                    .launchIn(scope)
            }
            .onAccountRemoved { account ->
                val userId = account.userId.id
                // There is no need to stop Core or Calendar managers, they'll be stopped when the account is removed
                // However, we should remove their ids from our set of running managers
                observedCalendarIdsForUser.remove(userId)
                // And stop listening to their calendars' updates until they authenticate again
                observedCalendarScopes.remove(userId)?.cancel()
            }
    }
}
