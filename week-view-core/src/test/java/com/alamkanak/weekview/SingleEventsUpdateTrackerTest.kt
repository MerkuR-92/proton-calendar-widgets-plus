package com.alamkanak.weekview

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SingleEventsUpdateTrackerTest {

    @Test
    fun `the first frame always recomputes`() {
        val update = tracker().decide(scene(), originX = 0f, originY = 0f, dateRange = range(), cacheGeneration = 7L)

        assertThat(update).isInstanceOf(SingleEventsUpdate.Recompute::class.java)
    }

    @Test
    fun `a settled frame with no changes does nothing`() {
        val tracker = tracker()
        val scene = scene()
        val range = range()

        tracker.decide(scene, 0f, 0f, range, 1L)

        val update = tracker.decide(scene, 0f, 0f, range, 1L)

        assertThat(update).isEqualTo(SingleEventsUpdate.NoChange)
    }

    @Test
    fun `a horizontal origin change offsets existing bounds instead of recomputing`() {
        val tracker = tracker()
        val scene = scene()
        val range = range()

        tracker.decide(scene, 0f, 0f, range, 1L)

        val update = tracker.decide(scene, originX = -30f, originY = 0f, dateRange = range, cacheGeneration = 1L)

        assertThat(update).isEqualTo(SingleEventsUpdate.Offset(dx = -30f, dy = 0f))
    }

    @Test
    fun `a vertical-only origin change only offsets - it cannot repair empty chips`() {
        // Why a vertical drag never fixes the seam: the offset path skips empty-bounds chips.
        val tracker = tracker()
        val scene = scene()
        val range = range()

        tracker.decide(scene, 0f, 0f, range, 1L)

        val update = tracker.decide(scene, originX = 0f, originY = -40f, dateRange = range, cacheGeneration = 1L)

        assertThat(update).isEqualTo(SingleEventsUpdate.Offset(dx = 0f, dy = -40f))
    }

    @Test
    fun `a scene change recomputes`() {
        val tracker = tracker()
        val range = range()

        tracker.decide(scene(hourHeight = 50f), 0f, 0f, range, 1L)

        val update = tracker.decide(scene(hourHeight = 60f), 0f, 0f, range, 1L)

        assertThat(update).isInstanceOf(SingleEventsUpdate.Recompute::class.java)
    }

    @Test
    fun `a date range change recomputes and reports the dates that scrolled out`() {
        val tracker = tracker()
        val scene = scene()

        tracker.decide(scene, 0f, 0f, listOf(0L, DAY, 2 * DAY), 1L)

        val update = tracker.decide(scene, 0f, 0f, listOf(DAY, 2 * DAY, 3 * DAY), 1L)

        assertThat(update).isInstanceOf(SingleEventsUpdate.Recompute::class.java)
        val recompute = update as SingleEventsUpdate.Recompute
        assertThat(recompute.staleDateMillis).containsExactly(0L)
        assertThat(recompute.cacheChanged).isFalse()
    }

    // The vertical-seam scenario: a background cache bump while the view is settled must trigger a
    // recompute on the next frame, otherwise the freshly added empty-bounds chips never get bounds.
    @Test
    fun `a cache generation change while settled recomputes on the next frame`() {
        val tracker = tracker()
        val scene = scene()
        val range = range()

        tracker.decide(scene, 0f, 0f, range, 1L)
        assertThat(tracker.decide(scene, 0f, 0f, range, 1L)).isEqualTo(SingleEventsUpdate.NoChange)

        val update = tracker.decide(scene, 0f, 0f, range, 2L)

        assertThat(update).isInstanceOf(SingleEventsUpdate.Recompute::class.java)
        assertThat((update as SingleEventsUpdate.Recompute).cacheChanged).isTrue()
    }

    @Test
    fun `panning re-anchors with a full recompute periodically to correct drift`() {
        val tracker = tracker()
        val scene = scene()
        val range = range()

        tracker.decide(scene, 0f, 0f, range, 1L)

        var sawRecompute = false
        var originX = 0f
        repeat(200) {
            originX -= 1f
            if (tracker.decide(scene, originX, 0f, range, 1L) is SingleEventsUpdate.Recompute) {
                sawRecompute = true
            }
        }

        assertThat(sawRecompute).isTrue()
    }

    private fun tracker() = SingleEventsUpdateTracker()

    private fun range() = listOf(0L, DAY, 2 * DAY)

    private fun scene(
        minHour: Int = 0,
        maxHour: Int = 24,
        hourHeight: Float = 50f,
        headerHeight: Float = 100f,
        dayWidth: Float = 200f,
        isSingleDay: Boolean = false,
        singleDayPadding: Int = 0,
        dateRangeSize: Int = 3,
    ) = SingleEventsScene(
        minHour = minHour,
        maxHour = maxHour,
        hourHeight = hourHeight,
        headerHeight = headerHeight,
        dayWidth = dayWidth,
        isSingleDay = isSingleDay,
        singleDayPadding = singleDayPadding,
        dateRangeSize = dateRangeSize,
    )

    companion object {
        private const val DAY = 86_400_000L
    }
}
