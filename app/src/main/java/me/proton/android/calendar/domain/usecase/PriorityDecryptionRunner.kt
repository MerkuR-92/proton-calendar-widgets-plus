package me.proton.android.calendar.domain.usecase

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

enum class DecryptionPriority {
    Offscreen,
    Visible,
}

@Singleton
class PriorityDecryptionRunner @Inject constructor() {

    private data class DecryptionJob(val priority: DecryptionPriority, val run: suspend () -> Unit)

    private val pending = mutableListOf<DecryptionJob>()
    private val pendingMutex = Mutex()
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            // just orchestrate the work signals, no work done here
            while (true) {
                signal.receive()
                while (true) {
                    val next = pickHighest() ?: break
                    try {
                        next.run()
                    } catch (_: Throwable) {
                        // keep worker alive
                    }
                }
            }
        }
    }

    private suspend fun pickHighest(): DecryptionJob? = pendingMutex.withLock {
        val idx = pending.indices.maxByOrNull { pending[it].priority } ?: return@withLock null
        pending.removeAt(idx)
    }

    suspend fun <T> submit(priority: DecryptionPriority, block: suspend () -> T): T {
        if (priority < DecryptionPriority.Visible) delay(STAGGER_MS)

        val turn = CompletableDeferred<Unit>()
        val done = CompletableDeferred<Unit>()
        val job = DecryptionJob(priority) {
            turn.complete(Unit) // it's our turn
            done.await() // await for actual work
        }

        pendingMutex.withLock { pending.add(job) }
        signal.trySend(Unit)
        // await for our turn
        try {
            turn.await()
        } catch (e: CancellationException) {
            pendingMutex.withLock { pending.remove(job) }
            done.complete(Unit) // unblock worker if it had already picked us
            throw e
        }

        return try {
            block()
        } finally {
            // actual work is done
            done.complete(Unit)
        }
    }

    companion object {
        // Delay offscreen submissions so a concurrent visible caller reaches the queue first and gets picked first.
        private const val STAGGER_MS = 1000L
    }
}
