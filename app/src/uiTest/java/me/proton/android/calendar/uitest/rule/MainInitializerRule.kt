package me.proton.android.calendar.uitest.rule

import androidx.test.platform.app.InstrumentationRegistry
import me.proton.android.calendar.init.MainInitializer
import org.junit.rules.ExternalResource

class MainInitializerRule : ExternalResource() {
    override fun before() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        MainInitializer.init(context)
    }
}
