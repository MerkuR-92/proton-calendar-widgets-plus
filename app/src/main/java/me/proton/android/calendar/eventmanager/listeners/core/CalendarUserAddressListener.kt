package me.proton.android.calendar.eventmanager.listeners.core

import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.workDataOf
import me.proton.android.calendar.common.utils.WorkerUtils.enqueueWorkHelper
import me.proton.android.calendar.common.worker.UseCaseWorker
import me.proton.android.calendar.domain.Logger
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.key.data.api.response.AddressResponse
import me.proton.core.user.data.UserAddressEventListener
import me.proton.core.user.data.db.AddressDatabase
import me.proton.core.user.domain.repository.UserAddressRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarUserAddressListener @Inject constructor(
    db: AddressDatabase,
    private val userAddressRepository: UserAddressRepository,
    private val logger: Logger,
    private val workManager: WorkManager
) : UserAddressEventListener(db, userAddressRepository) {

    override val type = Type.Core
    override val order = 2

    private var refreshMembersFlags: Boolean = false

    override suspend fun onUpdate(config: EventManagerConfig, entities: List<AddressResponse>) {
        super.onUpdate(config, entities)

        refreshMembersFlags = true
    }

    override suspend fun onSuccess(config: EventManagerConfig) {
        super.onSuccess(config)

        if (refreshMembersFlags) {
            // Launch worker to refresh members flags
            workManager.enqueueWorkHelper(
                workDataOf(
                    UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.REFRESH_MEMBERS_FLAGS,
                    UseCaseWorker.INPUT_USER_ID to config.userId.id
                ),
                UseCaseWorker.UniqueWorkNames.REFRESH_MEMBERS_FLAGS,
                ExistingWorkPolicy.REPLACE,
                NetworkType.CONNECTED
            )

            refreshMembersFlags = false
        }
    }

    override suspend fun onResetAll(config: EventManagerConfig) {
        super.onResetAll(config)
        // Nothing to do, this is handled by core
    }
}
