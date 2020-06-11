package me.proton.android.calendar.data.entity

import androidx.room.*
import me.proton.android.calendar.data.db.AppDatabase

// = calendar key
// The calendar key can be decrypted using the token.
@Entity(tableName = AppDatabase.TABLE_CALENDAR_KEYS,
    foreignKeys = [ForeignKey(
        entity = CalendarEntity::class,
        parentColumns = ["id"],
        childColumns = ["calendarId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["calendarId"])])
data class CalendarKeyEntity(
    @PrimaryKey
    val id: String, //
    val flags: Int, // bitmap 1: Primary, 2: Active
    val privateKey: String, // The private key, encrypted with a passphrase
    val passphraseId: String, // "ARy95iNxhniEgYJrRrGvagmzRdnmv==",
    val calendarId: String
) {
    val isPrimary: Boolean get() = flags and 1 > 0
    val isActive: Boolean get() = flags and 2 > 0
}
