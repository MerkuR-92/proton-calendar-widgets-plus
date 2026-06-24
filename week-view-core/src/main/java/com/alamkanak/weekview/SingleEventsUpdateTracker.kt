package com.alamkanak.weekview

internal data class SingleEventsScene(
    val minHour: Int,
    val maxHour: Int,
    val hourHeight: Float,
    val headerHeight: Float,
    val dayWidth: Float,
    val isSingleDay: Boolean,
    val singleDayPadding: Int,
    val dateRangeSize: Int,
)

internal sealed interface SingleEventsUpdate {
    data class Recompute(
        val cacheChanged: Boolean,
        val staleDateMillis: List<Long>,
    ) : SingleEventsUpdate

    data class Offset(val dx: Float, val dy: Float) : SingleEventsUpdate

    object NoChange : SingleEventsUpdate
}

/**
 * Decides each frame whether the single-event chips need a full recompute, a cheap pan offset, or nothing
 */
internal class SingleEventsUpdateTracker {

    private var lastScene: SingleEventsScene? = null
    private var lastOriginX: Float? = null
    private var lastOriginY: Float? = null
    private var lastDateRange: List<Long>? = null
    private var lastCacheGeneration: Long = UNSET_GENERATION
    private var offsetFrameCount: Int = 0

    fun decide(
        scene: SingleEventsScene,
        originX: Float,
        originY: Float,
        dateRange: List<Long>,
        cacheGeneration: Long,
    ): SingleEventsUpdate {
        val prevDateRange = lastDateRange
        val prevOriginX = lastOriginX
        val prevOriginY = lastOriginY

        val sceneChanged = lastScene == null || lastScene != scene
        val dateRangeChanged = prevDateRange == null || prevDateRange != dateRange
        val cacheChanged = cacheGeneration != lastCacheGeneration

        val update = when {
            sceneChanged || dateRangeChanged || cacheChanged -> {
                offsetFrameCount = 0
                SingleEventsUpdate.Recompute(cacheChanged, staleDates(prevDateRange, dateRange))
            }

            prevOriginX != null && prevOriginY != null -> {
                val dx = originX - prevOriginX
                val dy = originY - prevOriginY
                when {
                    dx == 0f && dy == 0f -> SingleEventsUpdate.NoChange
                    offsetFrameCount < REANCHOR_INTERVAL -> {
                        offsetFrameCount++
                        SingleEventsUpdate.Offset(dx, dy)
                    }
                    else -> {
                        // Recompute from scratch periodically to correct accumulated float drift.
                        offsetFrameCount = 0
                        SingleEventsUpdate.Recompute(cacheChanged = false, staleDateMillis = emptyList())
                    }
                }
            }

            else -> SingleEventsUpdate.NoChange
        }

        lastScene = scene
        lastOriginX = originX
        lastOriginY = originY
        lastDateRange = dateRange
        lastCacheGeneration = cacheGeneration
        return update
    }

    private fun staleDates(previous: List<Long>?, current: List<Long>): List<Long> {
        previous ?: return emptyList()
        val currentSet = current.toHashSet()
        return previous.filter { it !in currentSet }
    }

    companion object {
        private const val UNSET_GENERATION = -1L
        private const val REANCHOR_INTERVAL = 120
    }
}
