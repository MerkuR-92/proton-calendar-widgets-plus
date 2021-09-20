package me.proton.android.calendar.common

import kotlinx.serialization.json.Json
import me.proton.android.calendar.data.CalendarsRepositoryImpl
import me.proton.android.calendar.data.UserSettingsRepositoryImpl
import me.proton.android.calendar.data.api.*
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.di.NetworkModule
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.api.*
import me.proton.android.calendar.domain.usecase.*
import me.proton.android.calendar.presentation.MainViewModel
import me.proton.android.calendar.presentation.account.AccountViewModel
import me.proton.android.calendar.presentation.calendar.CalendarViewModel
import me.proton.android.calendar.presentation.calendar.EventViewModel
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
import me.proton.core.network.data.NetworkManager
import me.proton.core.network.domain.NetworkManager
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.repository.UserAddressRepository
import me.proton.core.user.domain.repository.UserRepository
import org.koin.android.ext.koin.androidApplication
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Definitions of all modules for Koin.
 */

val commonModule = module {
    single<Json> { Json { ignoreUnknownKeys = true } }
    single<ICalUtilsImpl> { ICalUtilsImpl } // TODO maybe extract interface
    single<Logger> { TimberLogger }
    single<SharedPreferencesProvider> { SharedPreferencesProvider(androidApplication()) }
    single<ValueStoreProvider> { ValueStoreProviderImpl(get()) }
    single<ResourceProvider> { ResourceProviderImpl(androidApplication().resources) }
    single<AppDatabase> { AppDatabase.buildDatabase(androidApplication()) }
    single<Crypto> { CryptoImpl(get()) }

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
    single<ReportsApi> { ReportsApiImpl(get()) }
    single<MailSettingsApi> { MailSettingsApiImpl(get()) }
}

val repositoryModule = module {
    single<CalendarsRepository> { CalendarsRepositoryImpl(get(), get(), get(), get(), get(), get(), get()) }
    single<UserSettingsRepository> { UserSettingsRepositoryImpl(get()) }
//    single { FlightRepository(get(), get()) }
//    single { EventRepository(get(), get()) }
}

val viewModelModule = module {
    viewModel<CalendarViewModel> { CalendarViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel<MainViewModel> {
        MainViewModel(
            get(),
            get(),
            get()
        )
    }
    viewModel<EventViewModel> { EventViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel<AccountViewModel> { AccountViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
}

val useCaseModule = module {
    factory<FetchPublicKeysUseCase> { FetchPublicKeysUseCase(get(), get()) }
    factory<FetchEventsUseCase> { FetchEventsUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory<EditCreateEventUseCase> { EditCreateEventUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory<BootstrapCalendarsUseCase> { BootstrapCalendarsUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory<CacheCalendarPassphraseUseCase> { CacheCalendarPassphraseUseCase(get(), get(), get(), get(), get(), get()) }
    factory<TransformEventUseCase> { TransformEventUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory<HandleDeleteUseCase> { HandleDeleteUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory<HandleServerEventsUseCase> { HandleServerEventsUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory<UpdateCalendarUseCase> { UpdateCalendarUseCase(get(), get(), get()) }
    factory<SyncServerEventsUseCase> { SyncServerEventsUseCase(get(), get(), get(), get(), get()) }
    factory<SyncAlarmsUseCase> { SyncAlarmsUseCase(get(), get(), get(), get(), get()) }
    factory<HandleAlarmsUseCase> { HandleAlarmsUseCase(get(), get(), get(), get(), get()) }
    factory<UpdateAlarmsUseCase> { UpdateAlarmsUseCase(get(), get(), get(), get(), get()) }
    factory<CreateCalendarUseCase> { CreateCalendarUseCase(get(), get(), get(), get()) }
    factory<KeySetupUseCase> { KeySetupUseCase(get(), get(), get(), get(), get()) }
    factory<ResetCalendarsKeyUseCase> { ResetCalendarsKeyUseCase(get(), get(), get(), get(), get()) }
    factory<ShowNotificationUseCase> { ShowNotificationUseCase(get(), get(), get(), get(), get()) }
    factory<HandleEventsMetadataUseCase> { HandleEventsMetadataUseCase(get(), get(), get(), get(), get()) }
    factory<SendBugReportUseCase> { SendBugReportUseCase(get(), get()) }
    factory<GetCanonicalEmailsUseCase> { GetCanonicalEmailsUseCase(get(), get()) }
    factory<CalendarUserSettingsChangedUseCase> { CalendarUserSettingsChangedUseCase(get(), get(), get(), get()) }
    factory<ReactivateCalendarKeyUseCase> { ReactivateCalendarKeyUseCase(get(), get(), get(), get(), get()) }
    factory<UpdateCalendarUserSettingsUseCase> { UpdateCalendarUserSettingsUseCase(get(), get(), get(), get()) }
    factory<UpdateUserSettingsUseCase> { UpdateUserSettingsUseCase(get(), get(), get()) }
    factory<UpdateParticipationStatusUseCase> { UpdateParticipationStatusUseCase(get(), get(), get(), get()) }
    factory<SendEmailUseCase> { SendEmailUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory<HandleIcsUseCase> { HandleIcsUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory<UpdatePersonalPartUseCase> { UpdatePersonalPartUseCase(get(), get(), get(), get(), get()) }
    factory<HandleSaveUseCase> { HandleSaveUseCase(get(), get(), get(), get(), get(), get(), get()) }
}

fun coreModule(
    product: Product,
    apiProvider: ApiProvider,
    accountManager: AccountManager,
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
    networkManager: NetworkManager
) = module {
    single<Product> { product }
    // TODO: Remove when all *ApiImpl will be provided by a Dagger module.
    single<ApiProvider> { apiProvider }
    // TODO: Remove when all *ViewModel/*UseCase will be provided by a Dagger module.
    single<AccountManager> { accountManager }
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
    factory<SendEmailDirect> { sendEmailDirect /*SendEmailDirect(get(), get(), get(), get())*/ }
    single<PublicAddressRepository> { publicAddressRepository }
    single<NetworkManager> { networkManager }
}
