package me.proton.android.calendar.common.utils

import android.content.Context
import android.content.res.Configuration
import androidx.preference.PreferenceManager
import me.proton.android.calendar.common.AppTheme
import me.proton.android.calendar.common.FeatureFlag.CHANGE_LANGUAGE
import me.proton.android.calendar.common.SharedPreferencesKeys
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.getLocaleForFormatting
import java.util.*

object CustomLocale {

    fun apply(context: Context): Context {
        if (!CHANGE_LANGUAGE) return updateResources(context, "en-US")
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        return updateResources(context, preferences.getString(SharedPreferencesKeys.APP_LANGUAGE, null) ?: "")
    }

    private fun updateResources(context: Context, locale: String): Context {

        var languageToSet = locale.substringBefore("-")
        var countryToSet = locale.substringAfter("-", "")

        if (locale == "") {
            // If settings are in Auto Detect, use System language if supported, or fallback to en-US
            val defaultSupportedLanguageTag = getLocaleForFormatting().toLanguageTag()
            languageToSet = defaultSupportedLanguageTag.substringBefore("-")
            countryToSet = defaultSupportedLanguageTag.substringAfter("-", "")
        }

        // Create custom Locale
        val localeToSet = Locale(languageToSet, countryToSet)
        Locale.setDefault(localeToSet)

        val resources = context.resources
        val configuration = Configuration(resources.configuration)

        // Set the custom locale in Configuration
        configuration.setLocale(localeToSet)

        // Make sure we also set the app theme in Configuration
        when (AppTheme.values()[PreferenceManager.getDefaultSharedPreferences(context).getInt(SharedPreferencesKeys.THEME, AppTheme.SYSTEM_DEFAULT.value)]) {
            AppTheme.LIGHT -> configuration.uiMode = Configuration.UI_MODE_NIGHT_NO
            AppTheme.DARK -> configuration.uiMode = Configuration.UI_MODE_NIGHT_YES
            else -> configuration.uiMode = Configuration.UI_MODE_NIGHT_UNDEFINED
        }

        val updatedContext = context.createConfigurationContext(configuration)
        return updatedContext ?: context
    }
}

