package me.proton.android.calendar.domain.model

data class Participant(
    val email: String,
    val commonName: String? = null,
    val photoUri: String? = null,
) {

    var added: Boolean = false
}
