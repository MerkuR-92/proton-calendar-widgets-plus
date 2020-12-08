package me.proton.android.calendar.common

import kotlinx.serialization.json.Json
import me.proton.android.calendar.data.CalendarsRepositoryImpl
import me.proton.android.calendar.data.UsersRepositoryImpl
import me.proton.android.calendar.data.api.*
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.api.*
import me.proton.android.calendar.domain.usecase.*
import me.proton.android.calendar.presentation.MainViewModel
import me.proton.android.calendar.presentation.account.AccountViewModel
import me.proton.android.calendar.presentation.calendar.CalendarViewModel
import me.proton.android.calendar.presentation.calendar.EventViewModel
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.auth.presentation.AuthOrchestrator
import me.proton.core.network.data.ApiProvider
import org.koin.android.ext.koin.androidApplication
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Definitions of all modules for Koin.
 */

val commonModule = module {
    single<Json> { Json { ignoreUnknownKeys = true } }
    single<ICalUtils> { ICalUtils } // TODO maybe extract interface
    single<Logger> { TimberLogger }
    single<SharedPreferencesProvider> { SharedPreferencesProvider(androidApplication()) }
    single<ValueStoreProvider> { ValueStoreProviderImpl(get()) }
    single<AppDatabase> { AppDatabase(androidApplication()) }
    single<Crypto> { CryptoImpl(get()) }
    single<AndroidUtils> { AndroidUtils(get()) }

//    factory { new instance every time }
//    single(named("special logger")) { TimberLogger } -> single { SpecialRepository(get("special logger")) }
}

val networkModule = module {
    single<CalendarsApi> { CalendarsApiImpl(get()) }
    single<UsersApi> { UsersApiImpl(get()) }
    single<KeysApi> { KeysApiImpl(get()) }
    single<AddressesApi> { AddressesApiImpl(get()) }
    single<AuthenticationApi> { AuthenticationApiImpl(get()) }
    single<ServerEventsApi> { ServerEventsApiImpl(get()) }
    single<SettingsApi> { SettingsApiImpl(get()) }
}

val repositoryModule = module {
    single<CalendarsRepository> { CalendarsRepositoryImpl(get(), get(), get(), get(), get()) }
    single<UsersRepository> { UsersRepositoryImpl(get(), get()) }
//    single { FlightRepository(get(), get()) }
//    single { EventRepository(get(), get()) }
}

val viewModelModule = module {
    viewModel<CalendarViewModel> { CalendarViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel<MainViewModel> {
        MainViewModel(
            get(),
            get()
        )
    }
    viewModel<EventViewModel> { EventViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel<AccountViewModel> { AccountViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
}

val useCaseModule = module {
    factory<FetchPublicKeysUseCase> { FetchPublicKeysUseCase(get(), get(), get()) }
    factory<FetchUserUseCase> { FetchUserUseCase(get(), get(), get()) }
    factory<FetchEventsUseCase> { FetchEventsUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory<EditCreateEventUseCase> { EditCreateEventUseCase(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory<BootstrapCalendarsUseCase> { BootstrapCalendarsUseCase(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory<CacheCalendarPassphraseUseCase> { CacheCalendarPassphraseUseCase(get(), get(), get(), get(), get()) }
    factory<TransformEventUseCase> { TransformEventUseCase(get(), get(), get(), get(), get(), get()) }
    factory<DeleteEventUseCase> { DeleteEventUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory<HandleServerEventsUseCase> { HandleServerEventsUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory<UpdateCalendarUseCase> { UpdateCalendarUseCase(get(), get(), get()) }
    factory<SyncServerEventsUseCase> { SyncServerEventsUseCase(get(), get(), get(), get()) }
    factory<SyncAlarmsUseCase> { SyncAlarmsUseCase(get(), get(), get(), get(), get()) }
    factory<HandleAlarmsUseCase> { HandleAlarmsUseCase(get(), get(), get(), get(), get()) }
    factory<CreateCalendarUseCase> { CreateCalendarUseCase(get(), get(), get(), get(), get(), get()) }
    factory<KeySetupUseCase> { KeySetupUseCase(get(), get(), get(), get()) }
    factory<ShowNotificationUseCase> { ShowNotificationUseCase(get(), get(), get(), get()) }
}

fun coreModule(
    apiProvider: ApiProvider,
    accountManager: AccountManager,
    authOrchestrator: AuthOrchestrator
) = module {
    // TODO: Remove when all *ApiImpl will be provided by a Dagger module.
    single<ApiProvider> { apiProvider }
    // TODO: Remove when all *ViewModel/*UseCase will be provided by a Dagger module.
    single<AccountManager> { accountManager }
    // TODO: Remove when AccountViewModel will be provided by a Dagger module.
    single<AuthOrchestrator> { authOrchestrator }
}
