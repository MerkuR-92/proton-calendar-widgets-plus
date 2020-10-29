package me.proton.android.calendar.data.entity

import androidx.room.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
@Serializable
data class CalendarKeyEntity(
    @PrimaryKey
    @SerialName("ID")
    val id: String,
    @SerialName("Flags")
    val flags: Int, // bitmap 1: Primary, 2: Active
    @SerialName("PrivateKey")
    val privateKey: String, // encrypted with a passphrase
    @SerialName("PassphraseID")
    val passphraseId: String,
    @SerialName("CalendarID")
    val calendarId: String
) {
    val isPrimary: Boolean get() = flags and 1 > 0
    val isActive: Boolean get() = flags and 2 > 0
}
