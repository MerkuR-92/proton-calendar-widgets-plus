package me.proton.android.calendar.data.api

import android.content.SharedPreferences
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.common.SharedPreferencesKeys
import me.proton.android.calendar.common.provider.DefaultSharedPreferencesProvider
import me.proton.android.calendar.presentation.forceUpdate.ForceUpdateViewModel
import org.junit.jupiter.api.Test

internal class CalendarApiClientTest {

    private val prefs: SharedPreferences = mockk()
    private val prefsProvider: DefaultSharedPreferencesProvider = mockk {
        every { sharedPreferences } returns prefs
    }
    private val forceUpdateViewModel: ForceUpdateViewModel = mockk(relaxed = true)

    private val apiClient = CalendarApiClient(forceUpdateViewModel, prefsProvider)

    private fun setup(enabledLocally: Boolean, disabledRemotely: Boolean) {
        every { prefs.getBoolean(SharedPreferencesKeys.ALTERNATIVE_ROUTING, true) } returns enabledLocally
        every {
            prefs.getBoolean(SharedPreferencesKeys.ALTERNATIVE_ROUTING_REMOTELY_DISABLED, false)
        } returns disabledRemotely
    }

    @Test
    fun `uses doh when enabled locally and not disabled remotely`() = runBlocking {
        setup(enabledLocally = true, disabledRemotely = false)
        assertThat(apiClient.shouldUseDoh()).isTrue()
    }

    @Test
    fun `remote kill switch overrides local setting`() = runBlocking {
        setup(enabledLocally = true, disabledRemotely = true)
        assertThat(apiClient.shouldUseDoh()).isFalse()
    }

    @Test
    fun `does not use doh when disabled locally`() = runBlocking {
        setup(enabledLocally = false, disabledRemotely = false)
        assertThat(apiClient.shouldUseDoh()).isFalse()
    }

    @Test
    fun `does not use doh when disabled both locally and remotely`() = runBlocking {
        setup(enabledLocally = false, disabledRemotely = true)
        assertThat(apiClient.shouldUseDoh()).isFalse()
    }
}
