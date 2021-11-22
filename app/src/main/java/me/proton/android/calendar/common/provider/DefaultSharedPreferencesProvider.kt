package me.proton.android.calendar.common.provider

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import me.proton.android.calendar.domain.ValueKey
import me.proton.android.calendar.domain.ValueStore
import me.proton.android.calendar.domain.ValueStoreProvider

class DefaultSharedPreferencesProvider(private val context: Context) {

    val sharedPreferences: SharedPreferences
    get() = PreferenceManager.getDefaultSharedPreferences(context)

}
