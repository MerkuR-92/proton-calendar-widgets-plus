package me.proton.android.calendar.presentation.holidayCalendar

import me.proton.android.calendar.data.entity.ManagedHolidayCalendarEntity

object HolidayCalendarUtils {

    fun distinctCountries(
        calendars: List<ManagedHolidayCalendarEntity>,
        deviceLanguageCode: String
    ): List<ManagedHolidayCalendarEntity> =
        calendars.groupBy { it.countryCode }
            .map { (_, variants) -> variants.canonical(deviceLanguageCode) }
            .sortedBy { it.country }

    fun languagesForCountry(
        calendars: List<ManagedHolidayCalendarEntity>,
        countryCode: String
    ): List<String> =
        calendars.filteredByCountryCode(countryCode).map { it.language }.distinct()

    fun defaultCalendarForCountry(
        calendars: List<ManagedHolidayCalendarEntity>,
        countryCode: String,
        deviceLanguageCode: String
    ): ManagedHolidayCalendarEntity? =
        calendars.filteredByCountryCode(countryCode).let { variants ->
            variants.firstOrNull { it.hasLanguageCode(deviceLanguageCode) } ?: variants.firstOrNull()
        }

    private fun List<ManagedHolidayCalendarEntity>.canonical(deviceLanguageCode: String) =
        firstOrNull { it.hasLanguageCode(deviceLanguageCode) }
            ?: minByOrNull { it.country }
            ?: first()
}

private fun List<ManagedHolidayCalendarEntity>.filteredByCountryCode(countryCode: String) =
    filter { it.countryCode == countryCode }

private fun ManagedHolidayCalendarEntity.hasLanguageCode(languageCode: String) =
    this.languageCode.equals(languageCode, ignoreCase = true)
