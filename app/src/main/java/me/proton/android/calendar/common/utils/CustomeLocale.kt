package me.proton.android.calendar.common.utils

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import androidx.preference.PreferenceManager
import me.proton.android.calendar.common.FeatureFlag.CHANGE_LANGUAGE
import me.proton.android.calendar.common.SharedPreferencesKeys
import me.proton.android.calendar.common.logger.TimberLogger
import java.util.*

object CustomLocale {

    fun apply(context: Context): Context {
        if (!CHANGE_LANGUAGE) return updateResources(context, "en-US")
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        return updateResources(context, preferences.getString(SharedPreferencesKeys.APP_LANGUAGE, null) ?: "")
    }

    private fun updateResources(context: Context, locale: String): Context {

        var languageToSet = locale.substringBefore("_")
        var countryToSet = locale.substringAfter("_", "")

        if (locale == "") { // go back to default
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Resources.getSystem().configuration.locales.get(0)
            } else {
                Resources.getSystem().configuration.locale
            }.apply {
                languageToSet = language ?: "en"
                countryToSet = country ?: ""
            }
        }

        TimberLogger.e("Test test updateResources languageToSet $languageToSet countryToSet $countryToSet")

        val localeToSet = Locale(languageToSet, countryToSet)
        Locale.setDefault(localeToSet)

        val resources = context.resources
        val configuration = Configuration(resources.configuration)
        configuration.setLocale(localeToSet)
        val updatedContext = context.createConfigurationContext(configuration)
        return updatedContext ?: context
    }
}

