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
    fun putBoolean(key: String, value: Boolean)
    fun getBoolean(key: String): Boolean?
    fun putStringInSet(setName: String, key: String, value: String)
    fun getStringFromSet(setName: String, key: String): String?
    fun putLongInSet(setName: String, key: String, value: Long)
    fun getLongFromSet(setName: String, key: String): Long?
    fun clearAll()
    fun removeKey(key: String)
}

interface ValueStoreProvider {
    fun provideValueStore(userId: String): ValueStore
}

object ValueKey {
    const val USER_PASSPHRASE = "USER_PASSPHRASE" // TODO deprecated!
    const val LAST_EVENT_ALARM_HANDLED_TIMESTAMP = "LAST_EVENT_ALARM_HANDLED_TIMESTAMP"
    const val LAST_SERVER_EVENT_ID = "LAST_SERVER_EVENT_ID"
}

object ValueSet {
    const val CALENDAR_PASSPHRASE = "CALENDAR_PASSPHRASE"
    const val LAST_CALENDAR_ALARM_SYNC_SUCCESS_TIMESTAMP = "LAST_CALENDAR_ALARM_SYNC_SUCCESS_TIMESTAMP"
    const val LAST_SERVER_CALENDAR_EVENT_ID = "LAST_SERVER_CALENDAR_EVENT_ID"
}

