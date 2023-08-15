package me.proton.android.calendar.common.utils

import androidx.lifecycle.LiveData
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import me.proton.android.calendar.common.worker.UseCaseWorker

object WorkerUtils {

    fun WorkManager.enqueueWorkHelper(
        workData: Data,
        uniqueWorkName: String,
        workPolicy: ExistingWorkPolicy,
        networkType: NetworkType
    ): LiveData<Operation.State> {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .build()

        val work = OneTimeWorkRequestBuilder<UseCaseWorker>()
            .setConstraints(constraints)
            .setInputData(workData)
            .build()

        return this.enqueueUniqueWork(uniqueWorkName, workPolicy, work).state
    }
}