package me.proton.android.calendar.data.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HolidaysCalendarEntity(
    @SerialName("CalendarID")
    val calendarId: String,
    @SerialName("Country")
    val country: String,
    @SerialName("Country_Code")
    val countryCode: String,
    @SerialName("Language")
    val language: String,
    @SerialName("Timezones")
    val timezones: List<String>,
    @SerialName("Passphrase")
    val passphrase: String,
    @SerialName("SessionKey")
    val sessionKey: SessionKeyEntity
)

@Serializable
data class SessionKeyEntity(
    @SerialName("Key")
    val key: String,
    @SerialName("Algorithm")
    val algorithm: String
)
