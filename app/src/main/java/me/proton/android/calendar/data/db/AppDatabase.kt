package me.proton.android.calendar.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import me.proton.android.calendar.data.entity.*
import me.proton.core.account.data.db.AccountConverters
import me.proton.core.account.data.db.AccountDatabase
import me.proton.core.account.data.entity.AccountEntity
import me.proton.core.account.data.entity.AccountMetadataEntity
import me.proton.core.account.data.entity.SessionDetailsEntity
import me.proton.core.account.data.entity.SessionEntity
import me.proton.core.challenge.data.db.ChallengeConverters
import me.proton.core.challenge.data.db.ChallengeDatabase
import me.proton.core.challenge.data.entity.ChallengeFrameEntity
import me.proton.core.contact.data.local.db.ContactConverters
import me.proton.core.contact.data.local.db.ContactDatabase
import me.proton.core.contact.data.local.db.entity.ContactCardEntity
import me.proton.core.contact.data.local.db.entity.ContactEmailEntity
import me.proton.core.contact.data.local.db.entity.ContactEmailLabelEntity
import me.proton.core.contact.data.local.db.entity.ContactEntity
import me.proton.core.crypto.android.keystore.CryptoConverters
import me.proton.core.data.room.db.BaseDatabase
import me.proton.core.data.room.db.CommonConverters
import me.proton.core.eventmanager.data.db.EventManagerConverters
import me.proton.core.eventmanager.data.db.EventMetadataDatabase
import me.proton.core.eventmanager.data.entity.EventMetadataEntity
import me.proton.core.featureflag.data.db.FeatureFlagDatabase
import me.proton.core.featureflag.data.entity.FeatureFlagEntity
import me.proton.core.humanverification.data.db.HumanVerificationConverters
import me.proton.core.humanverification.data.db.HumanVerificationDatabase
import me.proton.core.humanverification.data.entity.HumanVerificationEntity
import me.proton.core.key.data.db.KeySaltDatabase
import me.proton.core.key.data.db.PublicAddressDatabase
import me.proton.core.key.data.entity.KeySaltEntity
import me.proton.core.key.data.entity.PublicAddressEntity
import me.proton.core.key.data.entity.PublicAddressKeyEntity
import me.proton.core.keytransparency.data.local.KeyTransparencyDatabase
import me.proton.core.keytransparency.data.local.entity.AddressChangeEntity
import me.proton.core.keytransparency.data.local.entity.SelfAuditResultEntity
import me.proton.core.mailsettings.data.db.MailSettingsDatabase
import me.proton.core.mailsettings.data.entity.MailSettingsEntity
import me.proton.core.notification.data.local.db.NotificationConverters
import me.proton.core.notification.data.local.db.NotificationDatabase
import me.proton.core.notification.data.local.db.NotificationEntity
import me.proton.core.observability.data.db.ObservabilityDatabase
import me.proton.core.observability.data.entity.ObservabilityEventEntity
import me.proton.core.payment.data.local.db.PaymentDatabase
import me.proton.core.payment.data.local.entity.GooglePurchaseEntity
import me.proton.core.push.data.local.db.PushConverters
import me.proton.core.push.data.local.db.PushDatabase
import me.proton.core.push.data.local.db.PushEntity
import me.proton.core.telemetry.data.db.TelemetryDatabase
import me.proton.core.telemetry.data.entity.TelemetryEventEntity
import me.proton.core.user.data.db.AddressDatabase
import me.proton.core.user.data.db.UserConverters
import me.proton.core.user.data.db.UserDatabase
import me.proton.core.user.data.entity.AddressEntity
import me.proton.core.user.data.entity.AddressKeyEntity
import me.proton.core.user.data.entity.UserEntity
import me.proton.core.user.data.entity.UserKeyEntity
import me.proton.core.usersettings.data.db.OrganizationDatabase
import me.proton.core.usersettings.data.db.UserSettingsConverters
import me.proton.core.usersettings.data.db.UserSettingsDatabase
import me.proton.core.usersettings.data.entity.OrganizationEntity
import me.proton.core.usersettings.data.entity.OrganizationKeysEntity

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
        me.proton.core.usersettings.data.entity.UserSettingsEntity::class,
        OrganizationEntity::class,
        OrganizationKeysEntity::class,
        ContactCardEntity::class,
        ContactEmailEntity::class,
        ContactEmailLabelEntity::class,
        ContactEntity::class,
        EventMetadataEntity::class,
        FeatureFlagEntity::class,
        ChallengeFrameEntity::class,
        GooglePurchaseEntity::class,
        ObservabilityEventEntity::class,
        AddressChangeEntity::class,
        SelfAuditResultEntity::class,
        NotificationEntity::class,
        PushEntity::class,
        TelemetryEventEntity::class,
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
        UserSettingsEntity::class,
        ManagedHolidayCalendarEntity::class
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
    UserSettingsConverters::class,
    ContactConverters::class,
    EventManagerConverters::class,
    ChallengeConverters::class,
    NotificationConverters::class,
    PushConverters::class,
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
    MailSettingsDatabase,
    UserSettingsDatabase,
    OrganizationDatabase,
    ContactDatabase,
    EventMetadataDatabase,
    FeatureFlagDatabase,
    ChallengeDatabase,
    PaymentDatabase,
    ObservabilityDatabase,
    KeyTransparencyDatabase,
    NotificationDatabase,
    PushDatabase,
    TelemetryDatabase {

    abstract fun calendarsDao(): CalendarsDao
    abstract fun eventsDao(): EventsDao
    abstract fun calendarSettingsDao(): CalendarSettingsDao
    abstract fun calendarSubscriptionDao(): CalendarSubscriptionDao
    abstract fun calendarUserSettingsDao(): CalendarUserSettingsDao
    @Deprecated("Use me.proton.core.usersettings module.")
    abstract fun deprecatedUserSettingsDao(): UserSettingsDao
    abstract fun calendarKeysDao(): CalendarKeysDao
    abstract fun eventAlarmsDao(): EventAlarmsDao
    abstract fun membersDao(): MembersDao
    abstract fun passphrasesDao(): PassphrasesDao
    abstract fun managedHolidayCalendarDao(): ManagedHolidayCalendarDao

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
        const val TABLE_MANAGED_HOLIDAY_CALENDARS = "managed_holiday_calendars"

        const val name = "proton.calendar.db"
        const val version = 61

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
            AppDatabaseMigrations.MIGRATION_30_31,
            AppDatabaseMigrations.MIGRATION_31_32,
            AppDatabaseMigrations.MIGRATION_32_33,
            AppDatabaseMigrations.MIGRATION_33_34,
            AppDatabaseMigrations.MIGRATION_34_35,
            AppDatabaseMigrations.MIGRATION_35_36,
            AppDatabaseMigrations.MIGRATION_36_37,
            AppDatabaseMigrations.MIGRATION_37_38,
            AppDatabaseMigrations.MIGRATION_38_39,
            AppDatabaseMigrations.MIGRATION_39_40,
            AppDatabaseMigrations.MIGRATION_40_41,
            AppDatabaseMigrations.MIGRATION_41_42,
            AppDatabaseMigrations.MIGRATION_42_43,
            AppDatabaseMigrations.MIGRATION_43_44,
            AppDatabaseMigrations.MIGRATION_44_45,
            AppDatabaseMigrations.MIGRATION_45_46,
            AppDatabaseMigrations.MIGRATION_46_47,
            AppDatabaseMigrations.MIGRATION_47_48,
            AppDatabaseMigrations.MIGRATION_48_49,
            AppDatabaseMigrations.MIGRATION_49_50,
            AppDatabaseMigrations.MIGRATION_50_51,
            AppDatabaseMigrations.MIGRATION_51_52,
            AppDatabaseMigrations.MIGRATION_52_53,
            AppDatabaseMigrations.MIGRATION_53_54,
            AppDatabaseMigrations.MIGRATION_54_55,
            AppDatabaseMigrations.MIGRATION_55_56,
            AppDatabaseMigrations.MIGRATION_56_57,
            AppDatabaseMigrations.MIGRATION_57_58,
            AppDatabaseMigrations.MIGRATION_58_59,
            AppDatabaseMigrations.MIGRATION_59_60,
            AppDatabaseMigrations.MIGRATION_60_61,
        )

        fun buildDatabase(context: Context): AppDatabase =
            databaseBuilder<AppDatabase>(context, name)
                // Add old pre v29 migrations.
                .apply { oldMigrations.forEach { addMigrations(it) } }
                // Add unified DB migration.
                .addMigrations(AppDatabaseMigrations.MIGRATION_28_29(context))
                // Add new post v29 migrations.
                .apply { migrations.forEach { addMigrations(it) } }
                .enableMultiInstanceInvalidation()
                .build()
    }
}

/**
 * Custom Type Converters for Room.
 */
class DatabaseTypeConverters {

    @TypeConverter
    fun toListOfJsonElements(value: String?): List<JsonElement>? {
        return value?.run { Json { ignoreUnknownKeys = true }.decodeFromString<List<JsonElement>>(value) }
    }

    @TypeConverter
    fun fromListOfJsonElement(json: List<JsonElement>?): String? {
        return json?.run { Json { ignoreUnknownKeys = true }.encodeToString(json) }
    }

    @TypeConverter
    fun toJsonObject(value: String?): JsonObject? {
        return value?.run { Json { ignoreUnknownKeys = true }.decodeFromString<JsonObject>(value) }
    }

    @TypeConverter
    fun fromJsonObject(json: JsonObject?): String? {
        return json?.run { Json { ignoreUnknownKeys = true }.encodeToString(json) }
    }

}
