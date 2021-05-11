package me.proton.android.calendar.domain.utils

interface KotlinUtils {

    /**
     * Assumes that elements matching the predicate will be continous in the list,
     * so it breaks the loop eagerly.
     */
    fun <T> List<T>.filterFromTheEnd(predicate: (T) -> Boolean): List<T>

}
