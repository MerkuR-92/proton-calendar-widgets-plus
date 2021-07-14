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

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AppDatabaseMigrations {

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

            database.execSQL("ALTER TABLE ${AppDatabase.TABLE_ADDRESSES} ADD COLUMN displayName TEXT")
        }
    }

    val MIGRATION_25_26 = object : Migration(25, 26) {
        override fun migrate(database: SupportSQLiteDatabase) {

            database.execSQL("ALTER TABLE ${AppDatabase.TABLE_EVENTS} ADD COLUMN sharedEventId TEXT")
        }
    }

    val MIGRATION_26_27 = object : Migration(26, 27) {
        override fun migrate(database: SupportSQLiteDatabase) {

            database.execSQL("ALTER TABLE ${AppDatabase.TABLE_EVENTS} ADD COLUMN isProtonProtonInvite INTEGER")
        }
    }

    val MIGRATION_27_28 = object : Migration(27, 28) {
        override fun migrate(database: SupportSQLiteDatabase) {

            database.execSQL("ALTER TABLE ${AppDatabase.TABLE_CALENDARS} ADD COLUMN type INTEGER NOT NULL DEFAULT 0")

            database.execSQL("CREATE TABLE ${AppDatabase.TABLE_CALENDAR_SUBSCRIPTIONS} (calendarId TEXT NOT NULL PRIMARY KEY, createTime INTEGER NOT NULL, lastUpdateTime INTEGER NOT NULL, status INTEGER NOT NULL, url TEXT NOT NULL, FOREIGN KEY (calendarId) REFERENCES calendars (id) ON DELETE CASCADE ON UPDATE NO ACTION)")
            database.execSQL("CREATE INDEX index_calendar_subscriptions_calendarId ON ${AppDatabase.TABLE_CALENDAR_SUBSCRIPTIONS} (calendarId)")
        }
    }
}
