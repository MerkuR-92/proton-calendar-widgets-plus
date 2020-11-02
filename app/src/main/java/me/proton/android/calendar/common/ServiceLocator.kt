package me.proton.android.calendar.common

import com.google.gson.Gson
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import me.proton.android.calendar.BuildConfig
import me.proton.android.calendar.data.CalendarsRepositoryImpl
import me.proton.android.calendar.data.UsersRepositoryImpl
import me.proton.android.calendar.data.api.*
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.api.*
import me.proton.android.calendar.domain.usecase.*
import me.proton.android.calendar.presentation.MainViewModel
import me.proton.android.calendar.presentation.calendar.CalendarViewModel
import me.proton.android.calendar.presentation.calendar.EventViewModel
import me.proton.core.account.data.db.AccountDatabase
import me.proton.core.account.data.repository.AccountRepositoryImpl
import me.proton.core.account.domain.repository.AccountRepository
import me.proton.core.accountmanager.data.AccountManagerImpl
import me.proton.core.accountmanager.data.SessionManagerImpl
import me.proton.core.accountmanager.data.db.AccountManagerDatabase
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.accountmanager.domain.SessionManager
import me.proton.core.auth.data.repository.AuthRepositoryImpl
import me.proton.core.auth.domain.repository.AuthRepository
import me.proton.core.auth.presentation.AuthOrchestrator
import me.proton.core.data.crypto.KeyStoreStringCrypto
import me.proton.core.data.crypto.StringCrypto
import me.proton.core.domain.entity.Product
import me.proton.core.humanverification.data.repository.HumanVerificationLocalRepositoryImpl
import me.proton.core.humanverification.data.repository.HumanVerificationRemoteRepositoryImpl
import me.proton.core.humanverification.domain.repository.HumanVerificationLocalRepository
import me.proton.core.humanverification.domain.repository.HumanVerificationRemoteRepository
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.data.di.ApiFactory
import me.proton.core.network.data.di.NetworkManager
import me.proton.core.network.data.di.NetworkPrefs
import me.proton.core.network.domain.ApiClient
import me.proton.core.network.domain.NetworkManager
import me.proton.core.network.domain.NetworkPrefs
import me.proton.core.network.domain.session.SessionListener
import me.proton.core.network.domain.session.SessionProvider
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidApplication
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.dsl.module
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Definitions of all modules for Koin.
 */

val commonModule = module {
    single<Gson> { GsonCommon.gson }
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
    single<GsonConverterFactory> { GsonConverterFactory.create(get()) }
    single<CoroutineCallAdapterFactory> { CoroutineCallAdapterFactory() }
    single<OkHttpClient>() {/*qualifier = named("no_request_interceptor")*/
        OkHttpClient.Builder().apply {
            addInterceptor(RequestInterceptor(get(), get())) // qualifier = named("no_request_interceptor")
            if (BuildConfig.DEBUG) {
                this.addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
            }
        }.build()
    }
    /*single<OkHttpClient> { OkHttpClient.Builder().apply {
            addInterceptor(RequestInterceptor(get(), get(qualifier = named("no_request_interceptor")), get()))
            if (BuildConfig.DEBUG) {
                this.addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
            }
        }.build()
    }*/

//    single(named("special logger"))<OkHttpClient> { OkHttpClient.Builder().apply {
//        if (BuildConfig.DEBUG) {
//            this.addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
//        }
//        }.build()
//    }



    single<CalendarsApi> { CalendarsApiImpl(RetrofitFactory.createService(get(), get(), get()), get(), get()) }
    single<UsersApi> { UsersApiImpl(RetrofitFactory.createService(get(), get(), get()), get(), get()) }
    single<KeysApi> { KeysApiImpl(RetrofitFactory.createService(get(), get(), get()), get(), get()) }
    single<AddressesApi> { AddressesApiImpl(RetrofitFactory.createService(get(), get(), get()), get(), get()) }
    single<AuthenticationApi> { AuthenticationApiImpl(get()) }
    single<ServerEventsApi> { ServerEventsApiImpl(RetrofitFactory.createService(get(), get(), get()), get(), get()) }
    single<SettingsApi> { SettingsApiImpl(RetrofitFactory.createService(get(), get(), get()), get(), get()) }
}

val repositoryModule = module {
    single<CalendarsRepository> { CalendarsRepositoryImpl(get(), get(), get(), get(), get(), get()) }
    single<UsersRepository> { UsersRepositoryImpl(get(), get()) }
//    single { FlightRepository(get(), get()) }
//    single { EventRepository(get(), get()) }
}

val viewModelModule = module {
    viewModel<CalendarViewModel> { CalendarViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel<MainViewModel> {
        MainViewModel(
            get(),
            get()
        )
    }
    viewModel<EventViewModel> { EventViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
}

val useCaseModule = module {
    factory<FetchPublicKeysUseCase> { FetchPublicKeysUseCase(get(), get(), get()) }
    factory<LoginUserUseCase> { LoginUserUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory<FetchEventsUseCase> { FetchEventsUseCase(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory<EditCreateEventUseCase> { EditCreateEventUseCase(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory<BootstrapCalendarsUseCase> { BootstrapCalendarsUseCase(get(), get(), get(), get(), get(), get()) }
    factory<CacheCalendarPassphraseUseCase> { CacheCalendarPassphraseUseCase(get(), get(), get(), get(), get()) }
    factory<TransformEventUseCase> { TransformEventUseCase(get(), get(), get(), get(), get(), get()) }
    factory<DeleteEventUseCase> { DeleteEventUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory<HandleServerEventsUseCase> { HandleServerEventsUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory<UpdateCalendarUseCase> { UpdateCalendarUseCase(get(), get(), get(), get()) }
    factory<SyncServerEventsUseCase> { SyncServerEventsUseCase(get(), get(), get(), get()) }
    factory<SyncAlarmsUseCase> { SyncAlarmsUseCase(get(), get(), get(), get()) }
    factory<HandleAlarmsUseCase> { HandleAlarmsUseCase(get(), get(), get(), get(), get()) }
    factory<ShowNotificationUseCase> { ShowNotificationUseCase(get(), get(), get(), get()) }
}


val coreModule = module {
    // Proton Core.
    single<Product> { Product.Calendar }
    single<ApiClient> { CalendarApiClient() }

    single<NetworkManager> { NetworkManager(get()) }
    single<NetworkPrefs> { NetworkPrefs(get()) }
    single<StringCrypto> { KeyStoreStringCrypto()  }

    single<ApiFactory> { ApiFactory(API_BASE_URL, get(), CoreLogger, get(), get(), get(), get(), CoroutineScope(Job() + Dispatchers.Default)) }
    single<ApiProvider> { ApiProvider(get(), get())  }

    single<AccountManagerDatabase> { AccountManagerDatabase.buildDatabase(get()) }
    single<AccountRepository> { AccountRepositoryImpl(get(), get(), get()) }
    single<AuthRepository> { AuthRepositoryImpl(get())  }
    single<HumanVerificationLocalRepository> { HumanVerificationLocalRepositoryImpl(get()) }
    single<HumanVerificationRemoteRepository> { HumanVerificationRemoteRepositoryImpl(get()) }

    single<AccountManagerImpl> { AccountManagerImpl(get(), get(), get()) }
    single<SessionManager> { SessionManagerImpl(get()) }

    single<AuthOrchestrator> { AuthOrchestrator(get<AccountManagerImpl>()) }

    // Bindings.
    single<AccountManager> { get<AccountManagerImpl>() }
    single<AccountDatabase> { get<AccountManagerDatabase>() }
    single<SessionProvider> { get<SessionManager>() }
    single<SessionListener> { get<SessionManager>() }
}