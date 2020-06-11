package me.proton.android.calendar.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.JsonElement
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.model.MemberPassphrase
import me.proton.android.calendar.domain.model.Passphrase

// this decrypts Calendar Key, it is a "Calendar Passphrase"
@Entity(tableName = AppDatabase.TABLE_PASSPHRASES,
    foreignKeys = [ForeignKey(
        entity = CalendarEntity::class,
        parentColumns = ["id"],
        childColumns = ["calendarId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["calendarId"])])
data class PassphraseEntity(
    @PrimaryKey
    val id: String, // "ARy95iNxhniEgYJrRrGvagmzRdnmv==",
    val flags: Int, // 0: Inactive, 1: Active
    val memberPassphrases: List<JsonElement>,
    val calendarId: String
) {

//    lateinit var fkCalendarKeyId: String

    fun toPassphrase(gson: Gson): Passphrase {
        return Passphrase(
            id = this.id,
            flags = this.flags,
            memberPassphrases = this.memberPassphrases.map {
                gson.fromJson(it, MemberPassphrase::class.java)
            },
            calendarId = this.calendarId
        )
    }

}
