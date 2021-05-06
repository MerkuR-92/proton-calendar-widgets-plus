package me.proton.android.calendar.presentation

import android.content.*
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.work.*
import biweekly.ICalendar
import biweekly.parameter.ParticipationStatus
import biweekly.property.Method
import biweekly.property.Status
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.proton.android.calendar.common.*
import me.proton.android.calendar.common.ICalUtils.clone
import me.proton.android.calendar.common.IcsSurgeryUtils.cleanIcs
import me.proton.android.calendar.common.IcsSurgeryUtils.cleanRecurrenceId
import me.proton.android.calendar.data.api.valueOrNullAndLogErrors
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.UsersRepository
import me.proton.android.calendar.domain.model.Address
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.usecase.*
import me.proton.android.calendar.presentation.account.AccountViewModel
import me.proton.android.calendar.presentation.calendar.CalendarViewModel
import me.proton.core.domain.entity.UserId
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.HashMap


class MainViewModel(
    private val context: Context,
    private val accountViewModel: AccountViewModel,
    private val handleIcsUseCase: HandleIcsUseCase,
) : ViewModel() {

    private val intents = mutableMapOf<String, Intent>()

    /**
     * Try to open maps with event location.
     */
    fun handleEventLocationShow(location: String): Boolean {
        return try {
            val googleMapsUri = Uri.parse("geo:0,0?q=$location")
            val googleMapsIntent = Intent(Intent.ACTION_VIEW, googleMapsUri)
            googleMapsIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            googleMapsIntent.setPackage("com.google.android.apps.maps")
            context.startActivity(googleMapsIntent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    fun handleCopyToClipboard(content: String): Boolean {
        return try {
            val clipboard: ClipboardManager? = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
            if (clipboard != null) {
                val clip = ClipData.newPlainText("", content)
                clipboard.setPrimaryClip(clip)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    // TODO sync all "active" accounts
    fun syncServerEvents(userId: UserId) : LiveData<Operation.State> {

        val workState = kotlin.runCatching {
            WorkManager.getInstance(context).getWorkInfosForUniqueWork(UseCaseWorker.UniqueWorkNames.SYNC_SERVER_EVENTS).get(1, TimeUnit.SECONDS)
        }.getOrNull()?.firstOrNull()?.state

        val existingWorkPolicy = if (workState == WorkInfo.State.RUNNING) {
            ExistingWorkPolicy.KEEP
        } else {
            ExistingWorkPolicy.REPLACE
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val work = OneTimeWorkRequestBuilder<UseCaseWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(
                UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.SYNC_SERVER_EVENTS,
                UseCaseWorker.INPUT_USER_ID to userId.id
            ))
            .build()

        // TODO work is unique per user-id, make sure different inputdata => different unique work
        return WorkManager.getInstance(context).enqueueUniqueWork(UseCaseWorker.UniqueWorkNames.SYNC_SERVER_EVENTS, existingWorkPolicy, work).state

    }

    // TODO run only after bootstrap & successful "cold fetch" of events for the first required period
    fun syncAlarms(userId: UserId) : LiveData<Operation.State> {

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val work = OneTimeWorkRequestBuilder<UseCaseWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(
                UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.SYNC_ALARMS,
                UseCaseWorker.INPUT_USER_ID to userId.id
            ))
            .build()

        // TODO work is unique per user-id, make sure different inputdata => different unique work
        return WorkManager.getInstance(context).enqueueUniqueWork(UseCaseWorker.UniqueWorkNames.SYNC_ALARMS, ExistingWorkPolicy.REPLACE, work).state

    }

    private var viewModelJob = Job()

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }

    /**
     * Stores intent with given [action] in a map for clients to [consumeIntent] later.
     */
    fun handleIntent(intent: Intent) {
        intent.action?.let {
            intents.put(it, intent)
        }
    }

    fun containsIntent(action: String) = intents.containsKey(action)

    /**
     * Returns and deletes intent with given [action], if it has been handled previously.
     */
    fun consumeIntent(action: String): Intent? {
        return intents.remove(action)
    }

    companion object {
        const val INTENT_ACTION_SHOW_EVENT_DETAILS = "INTENT_ACTION_SHOW_EVENT_DETAILS"

        fun createMainIntentToShowEventDetails(context: Context, eventId: String, occurrenceNumber: Int?): Intent {
            return Intent(context, MainActivity::class.java).apply {
                action = INTENT_ACTION_SHOW_EVENT_DETAILS
                data = Navigation.Deeplink.toMainActivityWithEventId(eventId, occurrenceNumber ?: 0)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        }
    }

    suspend fun handleIcsFile(bufferedReader: BufferedReader): IcsSurgeryUtils.HandleIcsResult {
        val userId = accountViewModel.getPrimaryUserId() ?: return IcsSurgeryUtils.HandleIcsResult.Error.DefaultError
        val iCalString = bufferedReader.use { it.readText() }
        return handleIcsUseCase.execute(iCalString, userId)
    }
}
