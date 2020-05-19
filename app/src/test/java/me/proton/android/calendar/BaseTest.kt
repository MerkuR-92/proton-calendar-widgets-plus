package me.proton.android.calendar

import me.proton.android.calendar.common.*
import org.koin.core.context.startKoin
import org.koin.test.KoinTest

internal abstract class BaseTest : KoinTest {

    init {
        startKoin { modules(commonModule, viewModelModule, repositoryModule, networkModule, useCaseModule) }
    }

}