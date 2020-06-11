package me.proton.android.calendar.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import me.proton.android.calendar.data.db.AppDatabase

@Entity(tableName = AppDatabase.TABLE_PUBLIC_KEYS, indices = [Index(value = ["email"])])
data class PublicKeyEntity(
    val email: String,
    val flags: Int, // bitmap 1: Primary, 2: Active
    val publicKey: String // bitmap: 1: can be used to verify, 2: can be used to encrypt
) {
    @PrimaryKey(autoGenerate = true)
    var _id: Long = 0
}
