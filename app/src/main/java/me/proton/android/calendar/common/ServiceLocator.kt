@file:Suppress("MaxLineLength", "RedundantExplicitType")
package me.proton.android.calendar.common

import kotlinx.serialization.json.Json
import me.proton.android.calendar.CalendarWidgetRefresher
import me.proton.android.calendar.WidgetRefresher
import me.proton.android.calendar.common.logger.TimberLogger
import me.proton.android.calendar.common.provider.DefaultSharedPreferencesProvider
import me.proton.android.calendar.common.provider.ResourceProviderImpl
import me.proton.android.calendar.common.provider.SharedPreferencesProvider
import me.proton.android.calendar.common.provider.ValueStoreProviderImpl
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.data.api.AddressesApiImpl
import me.proton.android.calendar.data.api.AuthenticationApiImpl
import me.proton.android.calendar.data.api.BugReportsApiImpl
import me.proton.android.calendar.data.api.CalendarsApiImpl
import me.proton.android.calendar.data.api.FeedbackApiImpl
import me.proton.android.calendar.data.api.ImporterApiImpl
import me.proton.android.calendar.data.api.MailSettingsApiImpl
import me.proton.android.calendar.data.api.ServerEventsApiImpl
import me.proton.android.calendar.data.api.SettingsApiImpl
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.di.CalendarsModule_ProvideKotlinxJsonFactory
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Crypto
import me.proton.android.calendar.domain.EventDecryptor
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ResourceProvider
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.api.AddressesApi
import me.proton.android.calendar.domain.api.AuthenticationApi
import me.proton.android.calendar.domain.api.BugReportsApi
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.api.FeedbackApi
import me.proton.android.calendar.domain.api.ImporterApi
import me.proton.android.calendar.domain.api.MailSettingsApi
import me.proton.android.calendar.domain.api.ServerEventsApi
import me.proton.android.calendar.domain.api.SettingsApi
import me.proton.android.calendar.domain.usecase.BootstrapAllCalendarsUseCase
import me.proton.android.calendar.domain.usecase.BootstrapCalendarUseCase
import me.proton.android.calendar.domain.usecase.CacheCalendarPassphraseUseCase
import me.proton.android.calendar.domain.usecase.CalendarSettingsChangedUseCase
import me.proton.android.calendar.domain.usecase.CalendarUserSettingsChangedUseCase
import me.proton.android.calendar.domain.usecase.CreateCalendarUseCase
import me.proton.android.calendar.domain.usecase.DeleteCalendarUseCase
import me.proton.android.calendar.domain.usecase.EditCreateEventUseCase
import me.proton.android.calendar.domain.usecase.FetchCachedViewsEventsUseCase
import me.proton.android.calendar.domain.usecase.FetchEventsUseCase
import me.proton.android.calendar.domain.usecase.FetchPublicKeysUseCase
import me.proton.android.calendar.domain.usecase.GetCanonicalEmailsUseCase
import me.proton.android.calendar.domain.usecase.HandleAlarmsUseCase
import me.proton.android.calendar.domain.usecase.HandleDeleteUseCase
import me.proton.android.calendar.domain.usecase.HandleIcsUseCase
import me.proton.android.calendar.domain.usecase.HandleSaveUseCase
import me.proton.android.calendar.domain.usecase.JoinCalendarUseCase
import me.proton.android.calendar.domain.usecase.KeySetupUseCase
import me.proton.android.calendar.domain.usecase.LeaveCalendarUseCase
import me.proton.android.calendar.domain.usecase.ObtainPinnedKeysUseCase
import me.proton.android.calendar.domain.usecase.ObtainSendPreferencesUseCase
import me.proton.android.calendar.domain.usecase.ReactivateCalendarKeyUseCase
import me.proton.android.calendar.domain.usecase.RecreateCalendarUseCase
import me.proton.android.calendar.domain.usecase.RefreshCalendarUserSettingsUseCase
import me.proton.android.calendar.domain.usecase.ResetLocalEventDatabaseUseCase
import me.proton.android.calendar.domain.usecase.ResetCalendarsKeyUseCase
import me.proton.android.calendar.domain.usecase.SafePersistEventAlarmUseCase
import me.proton.android.calendar.domain.usecase.SendBugReportUseCase
import me.proton.android.calendar.domain.usecase.SendEmailUseCase
import me.proton.android.calendar.domain.usecase.ShowNotificationUseCase
import me.proton.android.calendar.domain.usecase.SyncAlarmsUseCase
import me.proton.android.calendar.domain.usecase.TransformEventUseCase
import me.proton.android.calendar.domain.usecase.UpdateAlarmsUseCase
import me.proton.android.calendar.domain.usecase.UpdateCalendarSettingsUseCase
import me.proton.android.calendar.domain.usecase.UpdateCalendarUseCase
import me.proton.android.calendar.domain.usecase.UpdateCalendarUserSettingsUseCase
import me.proton.android.calendar.domain.usecase.UpdateParticipationStatusUseCase
import me.proton.android.calendar.domain.usecase.UpdatePersonalPartUseCase
import me.proton.android.calendar.domain.usecase.UpdateUserSettingsUseCase
import me.proton.android.calendar.domain.usecase.UpgradeEventUseCase
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.contact.domain.repository.ContactRepository
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.mailmessage.domain.usecase.GetRecipientPublicAddresses
import me.proton.core.network.data.ApiProvider
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.repository.UserAddressRepository
import me.proton.core.user.domain.repository.UserRepository
import me.proton.core.usersettings.domain.repository.UserSettingsRepository
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

/**
 * Definitions of all modules for Koin.
 */

val commonModule = module {
    single<Json> { CalendarsModule_ProvideKotlinxJsonFactory.provideKotlinxJson() }
    single<ICalUtilsImpl> { ICalUtilsImpl } // TODO maybe extract interface
    single<Logger> { TimberLogger }
    single<SharedPreferencesProvider> { SharedPreferencesProvider(androidApplication()) }
    single<ValueStoreProvider> { ValueStoreProviderImpl(get()) }
    single<ResourceProvider> { ResourceProviderImpl(androidApplication().resources) }

    single<WidgetRefresher> { CalendarWidgetRefresher(androidApplication()) }

//    factory { new instance every time }
//    single(named("special logger")) { TimberLogger } -> single { SpecialRepository(get("special logger")) }
}

val networkModule = module {
    single<CalendarsApi> { CalendarsApiImpl(get()) }
    single<AddressesApi> { AddressesApiImpl(get()) }
    single<AuthenticationApi> { AuthenticationApiImpl(get()) }
    single<ServerEventsApi> { ServerEventsApiImpl(get()) }
    single<SettingsApi> { SettingsApiImpl(get()) }
    single<BugReportsApi> { BugReportsApiImpl(get()) }
    single<MailSettingsApi> { MailSettingsApiImpl(get()) }
    single<FeedbackApi> { FeedbackApiImpl(get()) }
    single<ImporterApi> { ImporterApiImpl(get()) }
}

val repositoryModule = module {
//    single { FlightRepository(get(), get()) }
//    single { EventRepository(get(), get()) }
}

val useCaseModule = module {
    factory<FetchPublicKeysUseCase> { FetchPublicKeysUseCase(get(), get(), get()) }
    factory<FetchEventsUseCase> { FetchEventsUseCase(get(), get(), get()) }
    factory<EditCreateEventUseCase> { EditCreateEventUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory<UpgradeEventUseCase> { UpgradeEventUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory<BootstrapAllCalendarsUseCase> { BootstrapAllCalendarsUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory<BootstrapCalendarUseCase> { BootstrapCalendarUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory<CacheCalendarPassphraseUseCase> { CacheCalendarPassphraseUseCase(get(), get(), get(), get(), get(), get()) }
    factory<TransformEventUseCase> { TransformEventUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory<HandleDeleteUseCase> { HandleDeleteUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory<UpdateCalendarUseCase> { UpdateCalendarUseCase(get(), get(), get(), get()) }
    factory<SyncAlarmsUseCase> { SyncAlarmsUseCase(get(), get(), get(), get(), get(), get()) }
    factory<HandleAlarmsUseCase> { HandleAlarmsUseCase(get(), get(), get(), get(), get()) }
    factory<UpdateAlarmsUseCase> { UpdateAlarmsUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory<CreateCalendarUseCase> { CreateCalendarUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory<RecreateCalendarUseCase> { RecreateCalendarUseCase(get(), get(), get(), get(), get()) }
    factory<KeySetupUseCase> { KeySetupUseCase(get(), get(), get(), get(), get()) }
    factory<ResetCalendarsKeyUseCase> { ResetCalendarsKeyUseCase(get(), get(), get(), get(), get(), get()) }
    factory<ShowNotificationUseCase> { ShowNotificationUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory<SendBugReportUseCase> { SendBugReportUseCase(get(), get()) }
    factory<GetCanonicalEmailsUseCase> { GetCanonicalEmailsUseCase(get(), get()) }
    factory<CalendarUserSettingsChangedUseCase> { CalendarUserSettingsChangedUseCase(get(), get(), get(), get()) }
    factory<ReactivateCalendarKeyUseCase> { ReactivateCalendarKeyUseCase(get(), get(), get(), get(), get(), get()) }
    factory<UpdateCalendarUserSettingsUseCase> { UpdateCalendarUserSettingsUseCase(get(), get(), get(), get()) }
    factory<UpdateUserSettingsUseCase> { UpdateUserSettingsUseCase(get(), get(), get()) }
    factory<UpdateParticipationStatusUseCase> { UpdateParticipationStatusUseCase(get(), get(), get(), get(), get()) }
    factory<SendEmailUseCase> { SendEmailUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory<HandleIcsUseCase> { HandleIcsUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory<UpdatePersonalPartUseCase> { UpdatePersonalPartUseCase(get(), get(), get(), get()) }
    factory<HandleSaveUseCase> { HandleSaveUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory<UpdateCalendarSettingsUseCase> { UpdateCalendarSettingsUseCase(get(), get(), get(), get()) }
    factory<DeleteCalendarUseCase> { DeleteCalendarUseCase(get(), get(), get(), get()) }
    factory<CalendarSettingsChangedUseCase> { CalendarSettingsChangedUseCase(get(), get(), get(), get()) }
    factory<RefreshCalendarUserSettingsUseCase> { RefreshCalendarUserSettingsUseCase(get(), get(), get()) }
    factory<SafePersistEventAlarmUseCase> { SafePersistEventAlarmUseCase(get(), get()) }
    factory<ObtainSendPreferencesUseCase> { ObtainSendPreferencesUseCase(get(), get(), get(), get(), get(), get()) }
    factory<ObtainPinnedKeysUseCase> { ObtainPinnedKeysUseCase(get(), get(), get(), get(), get()) }
    factory<JoinCalendarUseCase> { JoinCalendarUseCase(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory<LeaveCalendarUseCase> { LeaveCalendarUseCase(get(), get(), get(), get()) }
    factory<FetchCachedViewsEventsUseCase> { FetchCachedViewsEventsUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory<ResetLocalEventDatabaseUseCase> { ResetLocalEventDatabaseUseCase(get(), get(), get(), get(), get(), get(), get()) }
}

fun coreModule(
    appDatabase: AppDatabase,
    apiProvider: ApiProvider,
    crypto: Crypto,
    cryptoContext: CryptoContext,
    eventDecryptor: EventDecryptor,
    accountManager: AccountManager,
    userManager: UserManager,
    userRepository: UserRepository,
    userAddressRepository: UserAddressRepository,
    calendarsRepository: CalendarsRepository,
    contactEmailsRepository: ContactRepository,
    userSettingsRepository: UserSettingsRepository,
    getRecipientPublicAddresses: GetRecipientPublicAddresses,
    defaultSharedPreferencesProvider: DefaultSharedPreferencesProvider,
) = module {
    single<AppDatabase> { appDatabase }
    single<ApiProvider> { apiProvider }
    single<Crypto> { crypto }
    single<CryptoContext> { cryptoContext }
    single<AccountManager> { accountManager }
    single<UserManager> { userManager }
    single<UserRepository> { userRepository }
    single<UserAddressRepository> { userAddressRepository }
    single<CalendarsRepository> { calendarsRepository }
    single<ContactRepository> { contactEmailsRepository }
    single<UserSettingsRepository> { userSettingsRepository }
    single<GetRecipientPublicAddresses> { getRecipientPublicAddresses }
    single<EventDecryptor> { eventDecryptor }
    single<DefaultSharedPreferencesProvider> { defaultSharedPreferencesProvider }
}
