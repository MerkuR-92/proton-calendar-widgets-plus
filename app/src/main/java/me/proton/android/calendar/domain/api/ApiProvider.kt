package me.proton.android.calendar.domain.api

interface ApiProvider {
    fun <T: Api> forUser(userId: String, clazz: Class<T>) : Api
}