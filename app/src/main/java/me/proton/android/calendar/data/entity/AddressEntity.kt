package me.proton.android.calendar.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.CASCADE
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.model.Address
import me.proton.android.calendar.domain.model.AddressKey

@Entity(
    tableName = AppDatabase.TABLE_ADDRESSES,
    foreignKeys = [ForeignKey(
        entity = UserEntity::class,
        parentColumns = ["id"],
        childColumns = ["fkUserId"],
        onDelete = CASCADE
    )],
    indices = [Index(value = ["fkUserId"])]
)
@Serializable
data class AddressEntity(
    @SerialName("ID")
    @PrimaryKey
    val id: String,
    @SerialName("Email")
    val email: String,
    @SerialName("Status")
    val status: Int,
    @SerialName("DisplayName")
    val displayName: String?,
    @SerialName("Keys")
    val keys: List<JsonElement>
) {

    //@Expose(serialize = false, deserialize = false)
    @kotlinx.serialization.Transient
    lateinit var fkUserId: String

    fun toAddress(json: Json): Address {
        return Address(
            id = this.id,
            email = this.email,
            status = this.status,
            displayName = this.displayName,
            keys = this.keys.map {
                json.decodeFromJsonElement<AddressKey>(it)
            }
        )
    }
}

enum class AddressStatus(val value: Int) {
    /** 0 - The address disabled, calendars can be updated but events are read-only */
    DISABLED(0),
    /** 1 - The address is enabled */
    ENABLED(1)
}




