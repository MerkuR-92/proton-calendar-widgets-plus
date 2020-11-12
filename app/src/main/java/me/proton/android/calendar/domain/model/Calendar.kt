package me.proton.android.calendar.domain.model

data class Calendar(
        override val id: String,
        val name: String,
        val color: String,
        val flags: Int,
        val display: Boolean
) : BaseModel() {

        //Functions to check all three states because it can be disabled but not inactive, or inactive but not disabled
        val isActive: Boolean get() = !isDisabled && !isInactive && (flags and 1 == 1)
        val isInactive: Boolean get() = (flags and (0 + 2 + 4 + 8 + 16) >= 1)
        val isDisabled: Boolean get() = !isInactive && (flags and (32 + 64) >= 1)
        val isSuperOwnerDisabled: Boolean get() = flags and 64 >= 1
}
    // TODO fields need to be duplicated here, plus local metadata added
