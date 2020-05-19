package me.proton.android.calendar.domain.model

data class Calendar(
        override val id: String,
        val name: String,
        val color: String
) : BaseModel()
    // TODO fields need to be duplicated here, plus local metadata added
