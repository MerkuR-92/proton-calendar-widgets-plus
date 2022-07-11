package me.proton.android.calendar.common.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.preference.PreferenceManager
import me.proton.android.calendar.common.FeatureFlag.CHANGE_LANGUAGE
import me.proton.android.calendar.common.SharedPreferencesKeys
import java.util.Locale

object CustomLocale {

    fun applyCurrent(context: Context): Context {
        val currentSelectedLocaleCode = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(SharedPreferencesKeys.APP_SETTINGS_LANGUAGE, null)
        return currentSelectedLocaleCode?.let {
            val locale = createLocaleFromCode(it)
            if (getSelectedLocale() == null) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.create(locale))
            }
            // This is needed because of ResourceProvider's usage. It's advised to remove it ASAP.
            val configuration = context.resources.configuration
            configuration.setLocale(locale)
            context.createConfigurationContext(configuration)
        } ?: context
    }

    fun apply(context: Context, localeCode: String?) {
        val localesToSet: LocaleListCompat = when {
            !CHANGE_LANGUAGE -> LocaleListCompat.create(Locale("en", "US"))
            localeCode.isNullOrBlank() -> {
                // If settings are in Auto Detect, use System language if supported, or fallback to en-US
                PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .remove(SharedPreferencesKeys.APP_SETTINGS_LANGUAGE)
                    .commit()
                LocaleListCompat.getEmptyLocaleList()
            }
            else -> {
                PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .putString(SharedPreferencesKeys.APP_SETTINGS_LANGUAGE, localeCode)
                    .commit()
                val locale = createLocaleFromCode(localeCode)
                LocaleListCompat.create(locale)
            }
        }
        AppCompatDelegate.setApplicationLocales(localesToSet)
    }

    private fun createLocaleFromCode(localeCode: String): Locale {
        val languageToSet = localeCode.substringBefore("-")
        val countryToSet = localeCode.substringAfter("-", "")
        // Create custom Locale
        return Locale(languageToSet, countryToSet)
    }

    /** Gets either a custom selected Locale for the app or null. */
    fun getSelectedLocale(): Locale? = AppCompatDelegate.getApplicationLocales().firstOrNull()
}

private fun LocaleListCompat.firstOrNull() = if (isEmpty) null else this[0]
