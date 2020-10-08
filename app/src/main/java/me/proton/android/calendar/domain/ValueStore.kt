package me.proton.android.calendar.domain

// TODO move this to a dedicated package

/**
 * The actual implementation has to be scoped per User ID!
 */
interface ValueStore {
    fun putString(key: String, value: String)
    fun getString(key: String): String?
    fun putLong(key: String, value: Long)
    fun getLong(key: String): Long?
    fun putStringInSet(setName: String, key: String, value: String)
    fun getStringFromSet(setName: String, key: String): String?
    fun putLongInSet(setName: String, key: String, value: Long)
    fun getLongFromSet(setName: String, key: String): Long?
}

interface ValueStoreProvider {
    fun provideValueStore(userId: String): ValueStore
}

object ValueKey {
    const val LAST_SERVER_EVENT_ID = "LAST_SERVER_EVENT_ID"
    const val AUTH_ACCESS_TOKEN = "AUTH_ACCESS_TOKEN"
    const val AUTH_REFRESH_TOKEN = "AUTH_REFRESH_TOKEN"
    const val AUTH_UID = "AUTH_UID"
    const val USER_PASSPHRASE = "USER_PASSPHRASE"
    const val USER_CALENDAR_SETTINGS = "USER_CALENDAR_SETTINGS"
    const val LAST_ALARM_SYNC_SUCCESS = "LAST_ALARM_SYNC_SUCCESS"
//    const val PRIMARY_USER_KEY = "PRIMARY_USER_KEY"
//    const val PRIMARY_USER_KEY_ID = "PRIMARY_USER_KEY_ID"
//    const val PRIMARY_USER_KEY_SALT = "PRIMARY_USER_KEY_SALT"
}

object ValueSet {
    const val CALENDAR_PASSPHRASE = "CALENDAR_PASSPHRASE"
}

