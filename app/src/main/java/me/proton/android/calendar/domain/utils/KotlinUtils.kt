package me.proton.android.calendar.domain.utils

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

interface KotlinUtils {

    /**
     * Assumes that elements matching the predicate will be continous in the list,
     * so it breaks the loop eagerly.
     */
    fun <T> List<T>.filterFromTheEnd(predicate: (T) -> Boolean): List<T>

    /**
     * Applies the `debounce` to a flow, except for the first emit.
     */
    fun <T> Flow<T>.debounceExceptFirst(debounceDuration: Duration): Flow<T>

}
