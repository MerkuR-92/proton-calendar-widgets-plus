package me.proton.android.calendar.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.model.User
import me.proton.android.calendar.domain.model.UserKey

@Serializable
@Entity(tableName = AppDatabase.TABLE_USERS)
data class UserEntity(
    @SerialName("ID")
    @PrimaryKey
    val id: String,
    @SerialName("Keys")
    val keys: List<JsonElement>, // this is actually UserKey
    @SerialName("Email")
    val email: String, // TODO this can change over time! this is user's primary address!
    @SerialName("Name")
    val name: String,
    @SerialName("DisplayName")
    val displayName: String,
    @SerialName("Subscribed")
    val subscribed: Int,
    @SerialName("UsedSpace")
    val usedSpace: Long,
    @SerialName("MaxSpace")
    val maxSpace: Long,
    @SerialName("Delinquent")
    val delinquent: Int
) {

    fun toUser(): User {
        return User(
            id = this.id,
            keys = this.keys.map {
                Json { this.ignoreUnknownKeys = true }.decodeFromJsonElement<UserKey>(it)
            },
            email = this.email,
            displayName = if (this.displayName.isNotEmpty()) this.displayName else this.name
        )
    }




}

