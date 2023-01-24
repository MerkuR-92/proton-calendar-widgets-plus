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
import me.proton.android.calendar.data.api.*
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.di.CalendarsModule_ProvideKotlinxJsonFactory
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.api.*
import me.proton.android.calendar.domain.usecase.*
import me.proton.android.calendar.presentation.main.viewModel.MainViewModel
import me.proton.android.calendar.presentation.account.AccountViewModel
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import me.proton.android.calendar.presentation.calendar.viewModel.EventViewModel
import me.proton.android.calendar.presentation.importAssistant.viewModel.ImportAssistantViewModel
import me.proton.android.calendar.presentation.settings.viewModel.CalendarFormViewModel
import me.proton.core.accountmanager.data.AccountStateHandler
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.auth.presentation.AuthOrchestrator
import me.proton.core.contact.domain.repository.ContactRepository
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.crypto.common.keystore.KeyStoreCrypto
import me.proton.core.domain.entity.Product
import me.proton.core.humanverification.domain.HumanVerificationManager
import me.proton.core.humanverification.presentation.HumanVerificationOrchestrator
import me.proton.core.key.domain.repository.PublicAddressRepository
import me.proton.core.mailmessage.domain.usecase.GetRecipientPublicAddresses
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.domain.NetworkManager
import me.proton.core.network.domain.scopes.MissingScopeListener
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.repository.UserAddressRepository
import me.proton.core.user.domain.repository.UserRepository
import me.proton.core.usersettings.domain.repository.UserSettingsRepository
import org.koin.android.ext.koin.androidApplication
import org.koin.android.viewmodel.dsl.viewModel
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
    single<KeysApi> { KeysApiImpl(get()) }
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

val viewModelModule = module {
    viewModel<CalendarViewModel> { CalendarViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel<MainViewModel> {
        MainViewModel(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get()
        )
    }
    viewModel<EventViewModel> { EventViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel<AccountViewModel> { AccountViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel<CalendarFormViewModel> { CalendarFormViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel<ImportAssistantViewModel> { ImportAssistantViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
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
    factory<SyncAlarmsUseCase> { SyncAlarmsUseCase(get(), get(), get(), get(), get()) }
    factory<HandleAlarmsUseCase> { HandleAlarmsUseCase(get(), get(), get(), get(), get()) }
    factory<UpdateAlarmsUseCase> { UpdateAlarmsUseCase(get(), get(), get(), get(), get(), get()) }
    factory<CreateCalendarUseCase> { CreateCalendarUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory<RecreateCalendarUseCase> { RecreateCalendarUseCase(get(), get(), get(), get(), get()) }
    factory<KeySetupUseCase> { KeySetupUseCase(get(), get(), get(), get(), get()) }
    factory<ResetCalendarsKeyUseCase> { ResetCalendarsKeyUseCase(get(), get(), get(), get(), get()) }
    factory<ShowNotificationUseCase> { ShowNotificationUseCase(get(), get(), get(), get(), get(), get()) }
    factory<HandleEventsMetadataUseCase> { HandleEventsMetadataUseCase(get(), get(), get(), get(), get(), get()) }
    factory<SendBugReportUseCase> { SendBugReportUseCase(get(), get()) }
    factory<GetCanonicalEmailsUseCase> { GetCanonicalEmailsUseCase(get(), get()) }
    factory<CalendarUserSettingsChangedUseCase> { CalendarUserSettingsChangedUseCase(get(), get(), get(), get()) }
    factory<ReactivateCalendarKeyUseCase> { ReactivateCalendarKeyUseCase(get(), get(), get(), get(), get()) }
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
}

fun coreModule(
    product: Product,
    apiProvider: ApiProvider,
    accountManager: AccountManager,
    accountStateHandler: AccountStateHandler,
    authOrchestrator: AuthOrchestrator,
    humanVerificationManager: HumanVerificationManager,
    humanVerificationOrchestrator: HumanVerificationOrchestrator,
    userManager: UserManager,
    userRepository: UserRepository,
    userAddressRepository: UserAddressRepository,
    keyStoreCrypto: KeyStoreCrypto,
    getRecipientPublicAddresses: GetRecipientPublicAddresses,
    contactEmailsRepository: ContactRepository,
    cryptoContext: CryptoContext,
    sendEmailDirect: SendEmailDirect,
    publicAddressRepository: PublicAddressRepository,
    networkManager: NetworkManager,
    defaultSharedPreferencesProvider: DefaultSharedPreferencesProvider,
    appDatabase: AppDatabase,
    calendarsRepository: CalendarsRepository,
    userSettingsRepository: UserSettingsRepository,
    missingScopeListener: MissingScopeListener,
    eventDecryptor: EventDecryptor,
    crypto: Crypto
) = module {
    single<Product> { product }
    // TODO: Remove when all *ApiImpl will be provided by a Dagger module.
    single<ApiProvider> { apiProvider }
    // TODO: Remove when all *ViewModel/*UseCase will be provided by a Dagger module.
    single<AccountManager> { accountManager }
    single<AccountStateHandler> { accountStateHandler }
    // TODO: Remove when AccountViewModel will be provided by a Dagger module.
    factory<AuthOrchestrator> { authOrchestrator }
    single<HumanVerificationManager> { humanVerificationManager }
    factory<HumanVerificationOrchestrator> { humanVerificationOrchestrator }
    single<UserManager> { userManager }
    single<UserRepository> { userRepository }
    single<UserAddressRepository> { userAddressRepository }
    single<KeyStoreCrypto> { keyStoreCrypto }
    single<CryptoContext> { cryptoContext }
    factory<ObtainSendPreferencesUseCase> { ObtainSendPreferencesUseCase(get(), contactEmailsRepository, get(), get(), get(), getRecipientPublicAddresses) }
    factory<ObtainPinnedKeysUseCase> { ObtainPinnedKeysUseCase(get(), contactEmailsRepository, get(), get(), getRecipientPublicAddresses) }
    factory<SendEmailDirect> { sendEmailDirect /*SendEmailDirect(get(), get(), get(), get())*/ }
    single<PublicAddressRepository> { publicAddressRepository }
    single<NetworkManager> { networkManager }
    single<DefaultSharedPreferencesProvider> { defaultSharedPreferencesProvider }
    single<AppDatabase> { appDatabase }
    single<CalendarsRepository> { calendarsRepository }
    single<UserSettingsRepository> { userSettingsRepository }
    factory<MissingScopeListener> { missingScopeListener }
    single<EventDecryptor> { eventDecryptor }
    single<Crypto> { crypto }
}
