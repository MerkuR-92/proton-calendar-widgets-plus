package me.proton.android.calendar.domain.model

// TODO Temporary object for Holidays, to update / remove
data class Holidays(
    val id: String,
    val countryName: String,
    val timeZoneIds: List<String>,
    val flagDrawable: Int
) {
}