package me.proton.android.calendar.domain.model

data class Address(
    override val id: String,
    val email: String,
    val status: Int,
    val keys: List<AddressKey>
): BaseModel()

{

    val primaryKey = keys.find { it.primary == 1 }

}

