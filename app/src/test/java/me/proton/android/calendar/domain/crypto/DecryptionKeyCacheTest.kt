package me.proton.android.calendar.domain.crypto

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import me.proton.android.calendar.common.logger.TestsLogger
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.Crypto
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.user.domain.UserAddressManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class DecryptionKeyCacheTest {

    private val crypto: Crypto = mockk(relaxed = true)
    private val database: AppDatabase = mockk(relaxed = true)
    private val valueStoreProvider: ValueStoreProvider = mockk(relaxed = true)
    private val userAddressManager: UserAddressManager = mockk(relaxed = true)
    private val cryptoContext: CryptoContext = mockk(relaxed = true)

    private fun cache() = DecryptionKeyCache(
        database = database,
        json = Json,
        valueStoreProvider = valueStoreProvider,
        userAddressManager = userAddressManager,
        cryptoContext = cryptoContext,
        crypto = crypto,
        logger = TestsLogger,
    )

    @BeforeEach
    fun beforeEach() {
        clearAllMocks()
    }

    @Test
    fun `withActiveDecryption returns the block result`() = runTest {
        val sut = cache().also { it.bgScope = this }
        assertEquals(42, sut.withActiveDecryption { 42 })
    }

    @Test
    fun `keys are wiped once decryptions stay idle, not before`() = runTest {
        val sut = cache().also { it.bgScope = this }

        sut.withActiveDecryption { }
        verify(exactly = 0) { crypto.clearKeyRingCache() } // timer armed, delay not yet elapsed

        advanceUntilIdle()
        verify(exactly = 1) { crypto.clearKeyRingCache() } // wiped after the idle delay
    }

    @Test
    fun `back-to-back decryptions share one unlock and wipe only once`() = runTest {
        val sut = cache().also { it.bgScope = this }

        sut.withActiveDecryption { }
        sut.withActiveDecryption { }
        sut.withActiveDecryption { }

        advanceUntilIdle()
        verify(exactly = 1) { crypto.clearKeyRingCache() }
    }

    @Test
    fun `a decryption starting before the timer fires keeps keys until it finishes`() = runTest {
        val sut = cache().also { it.bgScope = this }
        val gate = CompletableDeferred<Unit>()

        sut.withActiveDecryption { } // starts the idle timer
        val job = launch { sut.withActiveDecryption { gate.await() } } // a slow decryption starts
        runCurrent() // let it begin (still running)

        advanceUntilIdle()  // timer fires but a decryption is active
        verify(exactly = 0) { crypto.clearKeyRingCache() }

        gate.complete(Unit)  // slow decryption finishes -> goes idle
        advanceUntilIdle()
        verify(exactly = 1) { crypto.clearKeyRingCache() }
        job.join()
    }

    @Test
    fun `userAddresses is fetched once and memoized within a pass`() = runTest {
        coEvery { userAddressManager.getAddresses(any(), any()) } returns emptyList()
        val sut = cache().also { it.bgScope = this }

        sut.withActiveDecryption {
            sut.userAddresses("user-1")
            sut.userAddresses("user-1")
            sut.userAddresses("user-1")
        }

        coVerify(exactly = 1) { userAddressManager.getAddresses(any(), any()) }
    }

    @Test
    fun `addresses are refetched in a new pass after the idle wipe`() = runTest {
        coEvery { userAddressManager.getAddresses(any(), any()) } returns emptyList()
        val sut = cache().also { it.bgScope = this }

        sut.withActiveDecryption { sut.userAddresses("user-1") }
        advanceUntilIdle() // idle wipe clears the memoized addresses
        sut.withActiveDecryption { sut.userAddresses("user-1") }

        coVerify(exactly = 2) { userAddressManager.getAddresses(any(), any()) }
    }
}
