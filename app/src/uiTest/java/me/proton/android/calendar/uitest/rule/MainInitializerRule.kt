package me.proton.android.calendar.uitest.rule

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import me.proton.android.calendar.init.MainInitializer
import org.junit.rules.ExternalResource

class MainInitializerRule(val context: Context) : ExternalResource() {
    override fun before() {
        MainInitializer.init(context)
    }
}
