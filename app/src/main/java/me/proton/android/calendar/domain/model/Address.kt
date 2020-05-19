package me.proton.android.calendar.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.JsonElement
import me.proton.android.calendar.data.db.AppDatabase

data class Address(
    override val id: String,
    val email: String,
    val keys: List<AddressKey>
): BaseModel()

{

    val primaryKey = keys.find { it.primary == 1 }

}

