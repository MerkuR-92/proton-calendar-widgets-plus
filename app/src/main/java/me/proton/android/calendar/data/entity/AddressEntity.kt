package me.proton.android.calendar.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.CASCADE
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.annotations.Expose
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
data class AddressEntity(
    @PrimaryKey
    val id: String,
    val email: String,
    val keys: List<JsonElement>
) {

    //@Expose(serialize = false, deserialize = false)
    lateinit var fkUserId: String

    fun toAddress(gson: Gson): Address {
        return Address(
            id = this.id,
            email = this.email,
            keys = this.keys.map {
                gson.fromJson(it, AddressKey::class.java)
            }
        )
    }

}




