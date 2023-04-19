package me.proton.android.calendar.domain

interface EnvironmentConfiguration {
    val apiHost: String
    val hvHost: String
    val proxyToken: String?
}
