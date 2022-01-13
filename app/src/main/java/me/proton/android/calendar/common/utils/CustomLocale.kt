package me.proton.android.calendar.common.utils

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import androidx.preference.PreferenceManager
import me.proton.android.calendar.common.FeatureFlag.CHANGE_LANGUAGE
import me.proton.android.calendar.common.SharedPreferencesKeys
import me.proton.android.calendar.common.logger.TimberLogger
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

        if (locale == "") { // go back to default
            val defaultSupportedLanguageTag = getLocaleForFormatting().toLanguageTag()
            languageToSet = defaultSupportedLanguageTag.substringBefore("-")
            countryToSet = defaultSupportedLanguageTag.substringAfter("-", "")
        }

        val localeToSet = Locale(languageToSet, countryToSet)
        Locale.setDefault(localeToSet)

        val resources = context.resources
        val configuration = Configuration(resources.configuration)
        configuration.setLocale(localeToSet)
        val updatedContext = context.createConfigurationContext(configuration)
        return updatedContext ?: context
    }
}

