package me.proton.android.calendar.common

import com.google.gson.Gson
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import me.proton.android.calendar.BuildConfig
import me.proton.android.calendar.presentation.MainViewModel
import me.proton.android.calendar.data.CalendarsRepositoryImpl
import me.proton.android.calendar.data.UsersRepositoryImpl
import me.proton.android.calendar.data.api.*
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.api.*
import me.proton.android.calendar.domain.usecase.*
import me.proton.android.calendar.presentation.calendar.CalendarViewModel
import me.proton.android.calendar.presentation.calendar.EventViewModel
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
    single<AuthenticationApi> { AuthenticationApiImpl(RetrofitFactory.createService(get(), get(), get()), get(), get()) }
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
