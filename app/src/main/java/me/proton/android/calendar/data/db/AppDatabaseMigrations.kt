/*
 * Copyright (c) 2021 Proton Technologies AG
 * This file is part of Proton Technologies AG and ProtonCore.
 *
 * ProtonCore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonCore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonCore.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.proton.android.calendar.data.db

import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.proton.android.calendar.data.api.MailSettingsEntity
import me.proton.core.account.data.db.AccountDatabase
import me.proton.core.account.data.entity.AccountEntity
import me.proton.core.account.data.entity.AccountMetadataEntity
import me.proton.core.account.data.entity.SessionDetailsEntity
import me.proton.core.account.data.entity.SessionEntity
import me.proton.core.contact.data.local.db.ContactDatabase
import me.proton.core.data.room.db.extension.*
import me.proton.core.humanverification.data.db.HumanVerificationDatabase
import me.proton.core.humanverification.data.entity.HumanVerificationEntity
import me.proton.core.key.data.db.KeySaltDatabase
import me.proton.core.key.data.db.PublicAddressDatabase
import me.proton.core.key.data.entity.KeySaltEntity
import me.proton.core.key.data.entity.PublicAddressEntity
import me.proton.core.key.data.entity.PublicAddressKeyEntity
import me.proton.core.mailsettings.data.db.MailSettingsDatabase
import me.proton.core.user.data.db.AddressDatabase
import me.proton.core.user.data.db.UserDatabase
import me.proton.core.user.data.entity.AddressEntity
import me.proton.core.user.data.entity.AddressKeyEntity
import me.proton.core.user.data.entity.UserEntity
import me.proton.core.user.data.entity.UserKeyEntity
import me.proton.core.usersettings.data.db.OrganizationDatabase
import me.proton.core.usersettings.data.db.UserSettingsDatabase

object AppDatabaseMigrations {

    val MIGRATION_24_25 = object : Migration(24, 25) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.addTableColumn(AppDatabase.TABLE_ADDRESSES, "displayName", "TEXT")
        }
    }

    val MIGRATION_25_26 = object : Migration(25, 26) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.addTableColumn(AppDatabase.TABLE_EVENTS, "sharedEventId", "TEXT")
        }
    }

    val MIGRATION_26_27 = object : Migration(26, 27) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.addTableColumn(AppDatabase.TABLE_EVENTS, "isProtonProtonInvite", "INTEGER")
        }
    }

    val MIGRATION_27_28 = object : Migration(27, 28) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.addTableColumn(AppDatabase.TABLE_CALENDARS, "type", "INTEGER NOT NULL" , "0")

            database.execSQL("CREATE TABLE ${AppDatabase.TABLE_CALENDAR_SUBSCRIPTIONS} (calendarId TEXT NOT NULL PRIMARY KEY, createTime INTEGER NOT NULL, lastUpdateTime INTEGER NOT NULL, status INTEGER NOT NULL, url TEXT NOT NULL, FOREIGN KEY (calendarId) REFERENCES calendars (id) ON DELETE CASCADE ON UPDATE NO ACTION)")
            database.execSQL("CREATE INDEX index_calendar_subscriptions_calendarId ON ${AppDatabase.TABLE_CALENDAR_SUBSCRIPTIONS} (calendarId)")
        }
    }

    /**
     * Copy Core DB into Calendar DB.
     */
    fun MIGRATION_28_29(context: Context) = object : Migration(28, 29) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Create all needed tables before attaching old Core DB.
            AccountDatabase.MIGRATION_0.migrate(database)
            AccountDatabase.MIGRATION_1.migrate(database)
            AccountDatabase.MIGRATION_2.migrate(database)
            AccountDatabase.MIGRATION_3.migrate(database)
            UserDatabase.MIGRATION_0.migrate(database)
            AddressDatabase.MIGRATION_0.migrate(database)
            AddressDatabase.MIGRATION_1.migrate(database)
            KeySaltDatabase.MIGRATION_0.migrate(database)
            HumanVerificationDatabase.MIGRATION_0.migrate(database)
            PublicAddressDatabase.MIGRATION_0.migrate(database)
            MailSettingsDatabase.MIGRATION_0.migrate(database)

            // End current transaction (from FrameworkSQLiteOpenHelper.onUpgrade(db, version, mNewVersion))
            database.setTransactionSuccessful()
            database.endTransaction()
            // Attach old Core DB to current DB (cannot be done within a transaction).
            val coreDbFile = context.getDatabasePath("db-account-manager")
            val coreDbPath = coreDbFile.path
            database.execSQL("ATTACH DATABASE '$coreDbPath' AS coreDb")
            // Begin transaction for attached migration.
            database.beginTransaction()

            // Import all data from Core DB to current DB.
            listOf(
                AccountEntity::class.simpleName,
                AccountMetadataEntity::class.simpleName,
                AddressEntity::class.simpleName,
                AddressKeyEntity::class.simpleName,
                HumanVerificationEntity::class.simpleName,
                MailSettingsEntity::class.simpleName,
                PublicAddressEntity::class.simpleName,
                PublicAddressKeyEntity::class.simpleName,
                KeySaltEntity::class.simpleName,
                SessionDetailsEntity::class.simpleName,
                SessionEntity::class.simpleName,
                UserEntity::class.simpleName,
                UserKeyEntity::class.simpleName
            ).forEach { table ->
                runCatching { database.execSQL("INSERT INTO main.$table SELECT * FROM coreDb.$table") }
            }

            // End current transaction to detach coreDb.
            database.setTransactionSuccessful()
            database.endTransaction()
            database.execSQL("DETACH DATABASE coreDb")
            // Begin transaction as it should be for a migration/onUpgrade.
            database.beginTransaction()

            // Recreate tables with proper foreign key, while keeping data.
            database.recreateTable(
                table = AppDatabase.TABLE_CALENDARS,
                createTable = { execSQL("CREATE TABLE IF NOT EXISTS `${AppDatabase.TABLE_CALENDARS}` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `color` TEXT NOT NULL, `display` INTEGER NOT NULL, `flags` INTEGER NOT NULL, `type` INTEGER NOT NULL, `fkUserId` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`fkUserId`) REFERENCES `UserEntity`(`userId`) ON UPDATE NO ACTION ON DELETE CASCADE )") },
                createIndices = { execSQL("CREATE INDEX IF NOT EXISTS `index_calendars_fkUserId` ON `${AppDatabase.TABLE_CALENDARS}` (`fkUserId`)") },
            )
            database.recreateTable(
                table = AppDatabase.TABLE_CALENDAR_USER_SETTINGS,
                createTable = { execSQL("CREATE TABLE IF NOT EXISTS `${AppDatabase.TABLE_CALENDAR_USER_SETTINGS}` (`fkUserId` TEXT NOT NULL, `weekLength` INTEGER NOT NULL, `displayWeekNumber` INTEGER NOT NULL, `autoDetectPrimaryTimezone` INTEGER NOT NULL, `primaryTimezone` TEXT NOT NULL, `displaySecondaryTimezone` INTEGER NOT NULL, `secondaryTimezone` TEXT, `viewPreference` INTEGER NOT NULL, `defaultCalendarId` TEXT, PRIMARY KEY(`fkUserId`), FOREIGN KEY(`fkUserId`) REFERENCES `UserEntity`(`userId`) ON UPDATE NO ACTION ON DELETE CASCADE )") },
                createIndices = { execSQL("CREATE INDEX IF NOT EXISTS `index_calendar_user_settings_fkUserId` ON `${AppDatabase.TABLE_CALENDAR_USER_SETTINGS}` (`fkUserId`)") },
            )

            database.recreateTable(
                table = AppDatabase.TABLE_USER_SETTINGS,
                createTable = { execSQL("CREATE TABLE IF NOT EXISTS `${AppDatabase.TABLE_USER_SETTINGS}` (`fkUserId` TEXT NOT NULL, `weekStart` INTEGER NOT NULL, `dateFormat` INTEGER NOT NULL, `timeFormat` INTEGER NOT NULL, PRIMARY KEY(`fkUserId`), FOREIGN KEY(`fkUserId`) REFERENCES `UserEntity`(`userId`) ON UPDATE NO ACTION ON DELETE CASCADE )") },
                createIndices = { execSQL("CREATE INDEX IF NOT EXISTS `index_user_settings_fkUserId` ON `${AppDatabase.TABLE_USER_SETTINGS}` (`fkUserId`)") },
            )

            // Drop old Calendar tables.
            database.dropTable(AppDatabase.TABLE_USERS)
            database.dropTable(AppDatabase.TABLE_ADDRESSES)

            // Delete Core Database.
            coreDbFile.delete()
        }
    }

    val MIGRATION_29_30 = object : Migration(29, 30) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Drop unused public_keys table.
            database.dropTable(AppDatabase.TABLE_PUBLIC_KEYS)
        }
    }

    val MIGRATION_30_31 = object : Migration(30, 31) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Two new entities/migrations in core.
            UserSettingsDatabase.MIGRATION_0.migrate(database)
            OrganizationDatabase.MIGRATION_0.migrate(database)
        }
    }

    val MIGRATION_31_32 = object : Migration(31, 32) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Two new migrations in core.
            AddressDatabase.MIGRATION_2.migrate(database)
            PublicAddressDatabase.MIGRATION_1.migrate(database)
        }
    }

    val MIGRATION_32_33 = object : Migration(32, 33) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // One new migration in core.
            ContactDatabase.MIGRATION_0.migrate(database)
        }
    }
}
