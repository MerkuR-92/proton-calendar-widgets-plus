package me.proton.android.calendar.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import me.proton.android.calendar.data.db.AppDatabase

data class UserKey(
    override val id: String,
    val version: Int, // TODO how to handle this?
    val primary: Int, // 1 -- primary
    val privateKey: String //,
//    val fingerprint: String // do not rely on the fingerprint from API
): BaseModel()

