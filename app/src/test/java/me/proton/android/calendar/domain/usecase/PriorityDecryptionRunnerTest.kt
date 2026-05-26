package me.proton.android.calendar.domain.usecase

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class PriorityDecryptionRunnerTest {

    @Test
    fun `single-threaded execution - concurrent submits never overlap`() = runBlocking {
        val runner = PriorityDecryptionRunner()
        val executing = AtomicInteger(0)
        val maxObserved = AtomicInteger(0)

        (1..5).map {
            async {
                runner.submit(DecryptionPriority.Visible) {
                    val n = executing.incrementAndGet()
                    maxObserved.updateAndGet { existing -> existing.coerceAtLeast(n) }
                    delay(20)
                    executing.decrementAndGet()
                }
            }
        }.awaitAll()

        assertEquals(1, maxObserved.get(), "should never run more than 1 job concurrently")
    }

    @Test
    fun `priority cutting - visible runs before queued offscreen`() = runBlocking {
        val runner = PriorityDecryptionRunner()
        val gateGo = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()

        // gate holds the worker so subsequent submissions queue up
        val gateJob = async {
            runner.submit(DecryptionPriority.Visible) {
                gateGo.await()
                synchronized(order) { order.add("gate") }
            }
        }
        delay(50) // ensure gate is running

        // 3 offscreens — each pays the 1s stagger before reaching pending
        val offscreens = (1..3).map { i ->
            async {
                runner.submit(DecryptionPriority.Offscreen) {
                    synchronized(order) { order.add("offscreen$i") }
                }
            }
        }
        delay(1100) // wait for offscreens to land in pending

        // visible — no stagger, lands in pending behind the offscreens
        val visible = async {
            runner.submit(DecryptionPriority.Visible) {
                synchronized(order) { order.add("visible") }
            }
        }
        delay(50) // ensure visible has been added

        gateGo.complete(Unit)
        gateJob.await()
        visible.await()
        offscreens.awaitAll()

        val snapshot = synchronized(order) { order.toList() }
        assertEquals(5, snapshot.size)
        assertEquals("gate", snapshot[0])
        assertEquals("visible", snapshot[1], "visible should cut in front of queued offscreens")
        assertEquals(setOf("offscreen1", "offscreen2", "offscreen3"), snapshot.drop(2).toSet())
    }

    @Test
    fun `cancellation before pickup - queued job never runs`() = runBlocking {
        val runner = PriorityDecryptionRunner()
        val gateGo = CompletableDeferred<Unit>()
        val ranCount = AtomicInteger(0)

        val gateJob = launch {
            runner.submit(DecryptionPriority.Visible) { gateGo.await() }
        }
        delay(50)

        val queuedJob = launch {
            runner.submit(DecryptionPriority.Visible) { ranCount.incrementAndGet() }
        }
        delay(50) // ensure queued has reached pending

        queuedJob.cancel()
        queuedJob.join()

        gateGo.complete(Unit)
        gateJob.join()
        delay(50) // give worker a moment to settle

        assertEquals(0, ranCount.get(), "cancelled queued job must not run")
    }

    @Test
    fun `cancellation mid-flight - block is interrupted, worker proceeds`() = runBlocking {
        val runner = PriorityDecryptionRunner()
        val started = CompletableDeferred<Unit>()
        var blockCompletedNormally = false

        val cancellingJob = launch {
            try {
                runner.submit(DecryptionPriority.Visible) {
                    started.complete(Unit)
                    delay(60_000) // long suspension; cancellation hits here
                    blockCompletedNormally = true
                }
            } catch (_: CancellationException) {
                // expected
            }
        }
        started.await()

        cancellingJob.cancel()
        cancellingJob.join()

        // worker should still process subsequent jobs
        val ranAfter = AtomicInteger(0)
        runner.submit(DecryptionPriority.Visible) { ranAfter.incrementAndGet() }

        assertFalse(blockCompletedNormally, "block must be cancelled, not run to completion")
        assertEquals(1, ranAfter.get(), "follow-up job must run after a cancelled job")
    }

    @Test
    fun `worker survives an exception thrown from block`() = runBlocking {
        val runner = PriorityDecryptionRunner()

        val ex = assertFailsWith<IllegalStateException> {
            runner.submit(DecryptionPriority.Visible) { error("intentional failure") }
        }
        assertEquals("intentional failure", ex.message)

        val ranAfter = AtomicInteger(0)
        runner.submit(DecryptionPriority.Visible) { ranAfter.incrementAndGet() }

        assertEquals(1, ranAfter.get(), "follow-up job must run after a thrown exception")
    }
}
