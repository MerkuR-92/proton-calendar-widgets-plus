package me.proton.android.calendar.uitest

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication
import me.proton.core.util.android.sharedpreferences.set
import me.proton.core.util.kotlin.CoreLogger
import me.proton.test.fusion.FusionConfig

class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }
}
