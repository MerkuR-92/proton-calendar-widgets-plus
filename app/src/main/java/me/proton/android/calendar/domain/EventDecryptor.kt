package me.proton.android.calendar.domain

import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.domain.model.Event

interface EventDecryptor {

    suspend fun decrypt(eventEntity: EventEntity): Event?

    suspend fun clearCache()

}