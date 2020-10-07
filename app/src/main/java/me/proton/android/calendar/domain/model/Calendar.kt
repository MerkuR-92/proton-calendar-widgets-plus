package me.proton.android.calendar.domain.model

data class Calendar(
        override val id: String,
        val name: String,
        val color: String,
        val isActive: Boolean,
        val display: Boolean
) : BaseModel()
    // TODO fields need to be duplicated here, plus local metadata added
