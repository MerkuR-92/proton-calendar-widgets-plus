package me.proton.android.calendar.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.data.db.AppDatabase

@Serializable
data class UserKey(
    @SerialName("ID")
    override val id: String,
    @SerialName("Version")
    val version: Int, // TODO how to handle this?
    @SerialName("Primary")
    val primary: Int, // 1 -- primary
    @SerialName("PrivateKey")
    val privateKey: String //,
//    val fingerprint: String // do not rely on the fingerprint from API
): BaseModel()

