package me.proton.android.calendar.di

import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.proton.android.calendar.common.FeatureFlag
import me.proton.android.calendar.eventmanager.listeners.calendar.*
import me.proton.android.calendar.eventmanager.listeners.core.CalendarListener
import me.proton.android.calendar.eventmanager.listeners.core.CalendarMemberEventListener
import me.proton.android.calendar.eventmanager.listeners.core.CalendarUserAddressListener
import me.proton.android.calendar.eventmanager.listeners.core.CalendarUserSettingsEventListener
import me.proton.core.contact.data.ContactEmailEventListener
import me.proton.core.contact.data.ContactEventListener
import me.proton.core.eventmanager.data.EventManagerConfigProviderImpl
import me.proton.core.eventmanager.data.EventManagerCoroutineScope
import me.proton.core.eventmanager.data.EventManagerFactory
import me.proton.core.eventmanager.data.EventManagerProviderImpl
import me.proton.core.eventmanager.data.db.EventMetadataDatabase
import me.proton.core.eventmanager.data.repository.EventMetadataRepositoryImpl
import me.proton.core.eventmanager.data.work.EventWorkerManagerImpl
import me.proton.core.eventmanager.domain.EventListener
import me.proton.core.eventmanager.domain.EventManagerConfigProvider
import me.proton.core.eventmanager.domain.EventManagerProvider
import me.proton.core.eventmanager.domain.repository.EventMetadataRepository
import me.proton.core.eventmanager.domain.work.EventWorkerManager
import me.proton.core.network.data.ApiProvider
import me.proton.core.presentation.app.AppLifecycleProvider
import me.proton.core.user.data.UserEventListener
import me.proton.core.usersettings.data.UserSettingsEventListener
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EventManagerModule {

    @Provides
    @Singleton
    @EventManagerCoroutineScope
    fun provideEventManagerCoroutineScope(): CoroutineScope =
        CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Provides
    @Singleton
    fun provideEventManagerConfigProvider(
        eventMetadataRepository: EventMetadataRepository
    ): EventManagerConfigProvider = EventManagerConfigProviderImpl(eventMetadataRepository)

    @Provides
    @Singleton
    @JvmSuppressWildcards
    fun provideEventManagerProvider(
        eventManagerFactory: EventManagerFactory,
        eventManagerConfigProvider: EventManagerConfigProvider,
        eventListeners: Set<EventListener<*, *>>
    ): EventManagerProvider =
        EventManagerProviderImpl(eventManagerFactory, eventManagerConfigProvider, eventListeners)

    @Provides
    @Singleton
    fun provideEventMetadataRepository(
        db: EventMetadataDatabase,
        provider: ApiProvider
    ): EventMetadataRepository = EventMetadataRepositoryImpl(db, provider)

    @Provides
    @Singleton
    fun provideEventWorkManager(
        workManager: WorkManager,
        appLifecycleProvider: AppLifecycleProvider
    ): EventWorkerManager = EventWorkerManagerImpl(workManager, appLifecycleProvider)

    @Provides
    @Singleton
    @ElementsIntoSet
    @JvmSuppressWildcards
    fun provideEventListenerSet(
        // Core listeners
        userEventListener: UserEventListener,
        userSettingsEventListener: UserSettingsEventListener,
        contactEventListener: ContactEventListener,
        contactEmailEventListener: ContactEmailEventListener,
        // Custom Core listener
        userAddressEventListener: CalendarUserAddressListener,
        // Calendar only listeners in Core event loop
        calendarListener: CalendarListener,
        calendarMemberEventListener: CalendarMemberEventListener,
        calendarUserSettingsEventListener: CalendarUserSettingsEventListener,
        // Calendar only listeners in Calendar event loop
        calendarEventListener: CalendarEventListener,
        calendarAlarmEventListener: CalendarAlarmEventListener,
        calendarPassphraseEventListener: CalendarPassphraseEventListener,
        calendarKeyEventListener: CalendarKeyEventListener,
        calendarSubscriptionsEventListener: CalendarSubscriptionsEventListener,
        calendarSettingsEventListener: CalendarSettingsEventListener,
    ): Set<EventListener<*, *>> = if (FeatureFlag.USE_EVENT_MANAGER) setOf(
        userEventListener,
        userSettingsEventListener,
        contactEventListener,
        contactEmailEventListener,
        userAddressEventListener,
        calendarListener,
        calendarMemberEventListener,
        calendarUserSettingsEventListener,
        calendarEventListener,
        calendarAlarmEventListener,
        calendarPassphraseEventListener,
        calendarKeyEventListener,
        calendarSubscriptionsEventListener,
        calendarSettingsEventListener,
    ) else emptySet()

}
