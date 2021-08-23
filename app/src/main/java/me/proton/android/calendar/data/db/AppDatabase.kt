package me.proton.android.calendar.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import me.proton.android.calendar.data.entity.*
import me.proton.core.account.data.db.AccountConverters
import me.proton.core.account.data.db.AccountDatabase
import me.proton.core.account.data.entity.AccountEntity
import me.proton.core.account.data.entity.AccountMetadataEntity
import me.proton.core.account.data.entity.SessionDetailsEntity
import me.proton.core.account.data.entity.SessionEntity
import me.proton.core.crypto.android.keystore.CryptoConverters
import me.proton.core.data.room.db.BaseDatabase
import me.proton.core.data.room.db.CommonConverters
import me.proton.core.humanverification.data.db.HumanVerificationConverters
import me.proton.core.humanverification.data.db.HumanVerificationDatabase
import me.proton.core.humanverification.data.entity.HumanVerificationEntity
import me.proton.core.key.data.db.KeySaltDatabase
import me.proton.core.key.data.db.PublicAddressDatabase
import me.proton.core.key.data.entity.KeySaltEntity
import me.proton.core.key.data.entity.PublicAddressEntity
import me.proton.core.key.data.entity.PublicAddressKeyEntity
import me.proton.core.mailsettings.data.db.MailSettingsDatabase
import me.proton.core.mailsettings.data.entity.MailSettingsEntity
import me.proton.core.user.data.db.AddressDatabase
import me.proton.core.user.data.db.UserConverters
import me.proton.core.user.data.db.UserDatabase
import me.proton.core.user.data.entity.AddressEntity
import me.proton.core.user.data.entity.AddressKeyEntity
import me.proton.core.user.data.entity.UserEntity
import me.proton.core.user.data.entity.UserKeyEntity

@Database(
    entities = [
        // Core
        AccountEntity::class,
        AccountMetadataEntity::class,
        SessionEntity::class,
        SessionDetailsEntity::class,
        UserEntity::class,
        UserKeyEntity::class,
        AddressEntity::class,
        AddressKeyEntity::class,
        KeySaltEntity::class,
        PublicAddressEntity::class,
        PublicAddressKeyEntity::class,
        HumanVerificationEntity::class,
        MailSettingsEntity::class,
        // Calendar
        CalendarEntity::class,
        EventEntity::class,
        CalendarSettingsEntity::class,
        CalendarUserSettingsEntity::class,
        CalendarKeyEntity::class,
        CalendarSubscriptionEntity::class,
        EventAlarmEntity::class,
        MemberEntity::class,
        PassphraseEntity::class,
        UserSettingsEntity::class
    ],
    version = AppDatabase.version,
    exportSchema = true
)
@TypeConverters(
    // Core
    CommonConverters::class,
    AccountConverters::class,
    UserConverters::class,
    CryptoConverters::class,
    HumanVerificationConverters::class,
    // Calendar
    DatabaseTypeConverters::class
)
abstract class AppDatabase :
    BaseDatabase(),
    AccountDatabase,
    UserDatabase,
    AddressDatabase,
    KeySaltDatabase,
    HumanVerificationDatabase,
    PublicAddressDatabase,
    MailSettingsDatabase {

    abstract fun calendarsDao(): CalendarsDao
    abstract fun eventsDao(): EventsDao
    abstract fun calendarSettingsDao(): CalendarSettingsDao
    abstract fun calendarSubscriptionDao(): CalendarSubscriptionDao
    abstract fun calendarUserSettingsDao(): CalendarUserSettingsDao
    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun calendarKeysDao(): CalendarKeysDao
    abstract fun eventAlarmsDao(): EventAlarmsDao
    abstract fun membersDao(): MembersDao
    abstract fun passphrasesDao(): PassphrasesDao

    companion object {

        const val TABLE_CALENDARS = "calendars"
        const val TABLE_EVENTS = "events"
        const val TABLE_USERS = "users"
        const val TABLE_ADDRESSES = "addresses"
        const val TABLE_CALENDAR_SETTINGS = "calendar_settings"
        const val TABLE_CALENDAR_USER_SETTINGS = "calendar_user_settings"
        const val TABLE_CALENDAR_KEYS = "calendar_keys"
        const val TABLE_CALENDAR_SUBSCRIPTIONS = "calendar_subscriptions"
        const val TABLE_USER_SETTINGS = "user_settings"
        const val TABLE_EVENT_ALARMS = "event_alarms"
        const val TABLE_PUBLIC_KEYS = "public_keys"
        const val TABLE_PASSPHRASES = "passphrases"
        const val TABLE_MEMBERS = "members"

        const val name = "proton.calendar.db"
        const val version = 30

        // Migrations before version 29.
        private val oldMigrations = listOf(
            AppDatabaseMigrations.MIGRATION_24_25,
            AppDatabaseMigrations.MIGRATION_25_26,
            AppDatabaseMigrations.MIGRATION_26_27,
            AppDatabaseMigrations.MIGRATION_27_28,
        )

        // Migrations after version 29.
        private val migrations = listOf(
            AppDatabaseMigrations.MIGRATION_29_30,
        )

        fun buildDatabase(context: Context): AppDatabase =
            databaseBuilder<AppDatabase>(context, name)
                // Add old pre v29 migrations.
                .apply { oldMigrations.forEach { addMigrations(it) } }
                // Add unified DB migration.
                .addMigrations(AppDatabaseMigrations.MIGRATION_28_29(context))
                // Add new post v29 migrations.
                .apply { migrations.forEach { addMigrations(it) } }
                .build()
    }
}

/**
 * Custom Type Converters for Room.
 */
class DatabaseTypeConverters {

    @TypeConverter
    fun toListOfJsonElements(value: String): List<JsonElement> {
        return Json { ignoreUnknownKeys = true }.decodeFromString<List<JsonElement>>(value)
    }

    @TypeConverter
    fun fromListOfJsonElement(json: List<JsonElement>): String {
        return Json { ignoreUnknownKeys = true }.encodeToString(json)
    }

}
