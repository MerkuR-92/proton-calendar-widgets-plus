package me.proton.android.calendar.common

import me.proton.android.calendar.domain.utils.KotlinUtils

object KotlinUtilsImpl : KotlinUtils {

    /**
     * Assumes that elements matching the predicate will be continous in the list,
     * so it breaks the loop eagerly.
     */
    override fun <T> List<T>.filterFromTheEnd(predicate: (T) -> Boolean): List<T> {

        val filtered = mutableListOf<T>()
        var insideWindow = false
        for (i in (this.size - 1) downTo 0) {
            if (predicate.invoke(this[i])) {
                insideWindow = true
                filtered.add(0, this[i])
            } else {
                if (insideWindow) break
            }
        }

        return filtered
    }

}
