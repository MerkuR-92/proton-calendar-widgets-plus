package me.proton.android.calendar.data.entity

import androidx.room.ColumnInfo
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
    val email: String? = null, // this can change over time, it's user's primary address but can be null and user still has valid Addresses
    @SerialName("Name")
    val name: String? = null,
    @SerialName("DisplayName")
    val displayName: String? = null,
    @SerialName("Subscribed")
    val subscribed: Int,
    @SerialName("UsedSpace")
    val usedSpace: Long,
    @SerialName("MaxSpace")
    val maxSpace: Long,
    @SerialName("Delinquent")
    val delinquent: Int,
) {

    fun toUser(): User {
        return User(
            id = this.id,
            keys = this.keys.map {
                Json { this.ignoreUnknownKeys = true }.decodeFromJsonElement<UserKey>(it)
            },
            email = this.email,
            name = this.name,
            displayName = this.displayName ?: this.name,
            subscribed = this.subscribed,
            usedSpace = this.usedSpace,
            maxSpace = this.maxSpace,
            delinquent = this.delinquent
        )
    }




}

