package me.proton.android.calendar.domain.usecase

import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import io.mockk.CapturingSlot
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.common.logger.TestsLogger
import me.proton.android.calendar.common.worker.UseCaseWorker
import me.proton.android.calendar.test.shared.mocks.userId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

internal class ScheduleSyncAlarmsUseCaseTest {

    private val workManagerMock: WorkManager = mockk()
    private val testsLogger = TestsLogger

    private val workName = UseCaseWorker.UniqueWorkNames.SYNC_ALARMS
    private val workPolicy = ExistingWorkPolicy.REPLACE

    private fun getScheduleSyncAlarmsUseCase(): ScheduleSyncAlarmsUseCase {
        return ScheduleSyncAlarmsUseCase(
            testsLogger,
            workManagerMock
        )
    }

    @BeforeEach
    fun `before each`() {
        clearAllMocks()
        mockkStatic(Log::class)
        coEvery { Log.isLoggable(any(), any()) } returns true
    }

    @Test
    fun `ScheduleSyncAlarms with no params applies default values`() = runBlocking {
        val expectedDelay = Duration.ofSeconds(0)
        val expectedForce = false
        val expectedUseCaseId = UseCaseWorker.UseCaseId.SYNC_ALARMS
        val expectedUserId = userId

        every {
            workManagerMock.enqueueUniqueWork(
                any(),
                any(),
                any<OneTimeWorkRequest>()
            )
        } returns mockk(relaxed = true)

        getScheduleSyncAlarmsUseCase().execute(
            userId = userId
        )

        val workRequestSlot = CapturingSlot<OneTimeWorkRequest>()
        coVerify(exactly = 1) {
            workManagerMock.enqueueUniqueWork(
                workName,
                workPolicy,
                capture(workRequestSlot)
            )
        }

        assertEquals(
            expectedUseCaseId,
            workRequestSlot.captured.workSpec.input.getString(UseCaseWorker.INPUT_USE_CASE_ID)
        )
        assertEquals(expectedUserId.id, workRequestSlot.captured.workSpec.input.getString(UseCaseWorker.INPUT_USER_ID))
        assertEquals(
            expectedForce,
            workRequestSlot.captured.workSpec.input.getBoolean(UseCaseWorker.INPUT_FORCE_SYNC_ALARMS, !expectedForce)
        )
        assertEquals(expectedDelay.toMillis(), workRequestSlot.captured.workSpec.initialDelay)
    }

    @Test
    fun `ScheduleSyncAlarms applies passed values`() = runBlocking {
        val expectedDelay = Duration.ofSeconds(10)
        val expectedForce = true
        val expectedUseCaseId = UseCaseWorker.UseCaseId.SYNC_ALARMS
        val expectedUserId = userId

        every {
            workManagerMock.enqueueUniqueWork(
                any(),
                any(),
                any<OneTimeWorkRequest>()
            )
        } returns mockk(relaxed = true)

        getScheduleSyncAlarmsUseCase().execute(
            userId = userId,
            initialDelay = expectedDelay,
            force = expectedForce
        )

        val workRequestSlot = CapturingSlot<OneTimeWorkRequest>()
        coVerify(exactly = 1) {
            workManagerMock.enqueueUniqueWork(
                workName,
                workPolicy,
                capture(workRequestSlot)
            )
        }

        assertEquals(
            expectedUseCaseId,
            workRequestSlot.captured.workSpec.input.getString(UseCaseWorker.INPUT_USE_CASE_ID)
        )
        assertEquals(expectedUserId.id, workRequestSlot.captured.workSpec.input.getString(UseCaseWorker.INPUT_USER_ID))
        assertEquals(
            expectedForce,
            workRequestSlot.captured.workSpec.input.getBoolean(UseCaseWorker.INPUT_FORCE_SYNC_ALARMS, !expectedForce)
        )
        assertEquals(expectedDelay.toMillis(), workRequestSlot.captured.workSpec.initialDelay)
    }

}