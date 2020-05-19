package me.proton.android.calendar.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.JsonElement
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.PassphraseEntity

data class Passphrase(
    override val id: String,
    val flags: Int, // 0: Inactive, 1: Active
    val memberPassphrases: List<MemberPassphrase>,
    val calendarId: String
): BaseModel() {
    val isActive = flags == 1
}

    data class MemberPassphrase(
        val memberId: String, // TODO this is probably the same as User's AddressID
        val passphrase: String, // encrypted passphrase, can be decrypted using the primary AddressKey linked to the member’s address.
        val signature: String // signature of plaintext passphrase, needs to be validated TODO!
    )


