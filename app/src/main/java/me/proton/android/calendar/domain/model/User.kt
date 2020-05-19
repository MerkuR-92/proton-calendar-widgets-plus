package me.proton.android.calendar.domain.model

import com.google.gson.JsonElement

data class User(
    override val id: String,
    val keys: List<UserKey>,
    val email: String, // TODO find out how this can be changed over time
    val displayName: String
) : BaseModel() {

    val primaryKey = keys.find { it.primary == 1 }

}