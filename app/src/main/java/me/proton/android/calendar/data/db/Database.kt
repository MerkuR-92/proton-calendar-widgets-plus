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
import me.proton.android.calendar.data.db.AppDatabase.Companion.TABLE_ADDRESSES
import me.proton.android.calendar.data.db.AppDatabase.Companion.TABLE_USERS
import me.proton.android.calendar.data.entity.*

@Database(
    entities = [CalendarEntity::class, EventEntity::class, UserEntity::class, AddressEntity::class, CalendarSettingsEntity::class, CalendarUserSettingsEntity::class, CalendarKeyEntity::class, EventAlarmEntity::class, MemberEntity::class, PassphraseEntity::class, PublicKeyEntity::class, UserSettingsEntity::class],
    version = 25
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
                // TODO we leave it like this, it's better to crash the app
                //  in runtime than crash on startup and have no crash logs
                .fallbackToDestructiveMigration()
                .addMigrations(
                    MIGRATION_24_25
                ).build()
    }
}

//val MIGRATION_23_24 = object : Migration(23, 24) {
//    override fun migrate(database: SupportSQLiteDatabase) {
//
//        // add nullable versions of already existing columns
//        database.execSQL("ALTER TABLE $TABLE_USERS ADD COLUMN email_nullable TEXT")
//        database.execSQL("ALTER TABLE $TABLE_USERS ADD COLUMN name_nullable TEXT")
//        database.execSQL("ALTER TABLE $TABLE_USERS ADD COLUMN displayName_nullable TEXT")
//    }
//}

val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(database: SupportSQLiteDatabase) {

        database.execSQL("ALTER TABLE $TABLE_ADDRESSES ADD COLUMN displayName TEXT")
    }
}

/**
 * Custom Type Converters for Room.
 */
private class DatabaseTypeConverters {

    @TypeConverter
    fun toListOfJsonElements(value: String): List<JsonElement> {
        return Json { ignoreUnknownKeys = true }.decodeFromString<List<JsonElement>>(value)
    }

    @TypeConverter
    fun fromListOfJsonElement(json: List<JsonElement>): String {
        return Json { ignoreUnknownKeys = true }.encodeToString(json)
    }

}
