package me.proton.android.calendar.data.db

import android.content.Context
import android.util.Log
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import me.proton.android.calendar.common.GsonCommon
import me.proton.android.calendar.data.entity.*
import me.proton.android.calendar.domain.model.AddressKey

@Database(
    entities = [CalendarEntity::class, EventEntity::class, UserEntity::class, AddressEntity::class, CalendarSettingsEntity::class, CalendarUserSettingsEntity::class, CalendarKeyEntity::class, EventAlarmEntity::class, MemberEntity::class, PassphraseEntity::class, PublicKeyEntity::class, UserSettingsEntity::class],
    version = 22
)
@TypeConverters(DatabaseTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun calendarsDao(): CalendarsDao
    abstract fun eventsDao(): EventsDao
    abstract fun usersDao(): UsersDao
    abstract fun addressesDao(): AddressesDao
    abstract fun calendarSettingsDao(): CalendarSettingsDao
    abstract fun calendarUserSettingsDao(): CalendarUserSettingsDao
    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun calendarKeysDao(): CalendarKeysDao
    abstract fun eventAlarmsDao(): EventAlarmsDao
    abstract fun membersDao(): MembersDao
    abstract fun passphrasesDao(): PassphrasesDao
    abstract fun publicKeysDao(): PublicKeysDao

//    fun calendarsDaoForUser(userId: String) : CalendarsDao {
//        return calendarsDao().apply {
//            this.userId = userId
//        }
//    }

    companion object {

        const val TABLE_CALENDARS = "calendars"
        const val TABLE_EVENTS = "events"
        const val TABLE_USERS = "users"
        const val TABLE_ADDRESSES = "addresses"
        const val TABLE_CALENDAR_SETTINGS = "calendar_settings"
        const val TABLE_CALENDAR_USER_SETTINGS = "calendar_user_settings"
        const val TABLE_USER_SETTINGS = "user_settings"
        const val TABLE_EVENT_ALARMS = "event_alarms"
        const val TABLE_CALENDAR_KEYS = "calendar_keys"
        const val TABLE_PUBLIC_KEYS = "public_keys"
        const val TABLE_PASSPHRASES = "passphrases"
        const val TABLE_MEMBERS = "members"
//        const val TABLE_USER_KEYS = "user_keys"

        @Volatile
        private var instance: AppDatabase? = null
        private val LOCK = Any()

        init {
            Log.isLoggable("SQLiteStatements", Log.DEBUG)
        }

        operator fun invoke(appContext: Context) = instance ?: synchronized(LOCK) {
            instance ?: buildDatabase(appContext).also { instance = it }
        }

        private fun buildDatabase(appContext: Context) =
            Room.databaseBuilder(appContext, AppDatabase::class.java, "proton.calendar.db")
                .fallbackToDestructiveMigration() // TODO fixme, force-clear & proper migrations
                //.addMigrations()
                .build()
    }
}

/**
 * Custom Type Converters for Room.
 */
private class DatabaseTypeConverters { // TODO inject GSON, but it seems to be unsupported

    // TODO REMOVE THIS
    @TypeConverter
    fun toListOfGSONJsonElements(value: String): List<com.google.gson.JsonElement> { // TODO if GSON passes null here, there be dragons
        return GsonCommon.gson.fromJson(value, GsonCommon.jsonElementListType)
    }

    // TODO REMOVE THIS
    @TypeConverter
    fun fromListOfGSONJsonElement(json: List<com.google.gson.JsonElement>): String { // TODO if GSON passes null here, there be dragons
        return GsonCommon.gson.toJson(json)
    }

    @TypeConverter
    fun toListOfJsonElements(value: String): List<JsonElement> {
        return Json.decodeFromString<List<JsonElement>>(value)
    }

    @TypeConverter
    fun fromListOfJsonElement(json: List<JsonElement>): String {
        return Json.encodeToString(json)
    }

}

private class Migration1To2 : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
//        database.execSQL("CREATE TABLE IF NOT EXISTS list_items" +
//                "('item_description' TEXT NOT NULL, 'item_priority' INTEGER NOT NULL," +
//                "'list_category_id' INTEGER NOT NULL, 'id' INTEGER NOT NULL, PRIMARY KEY(id)," +
//                "FOREIGN KEY('list_category_id') REFERENCES list_categories('id') ON DELETE CASCADE)")
    }
}
