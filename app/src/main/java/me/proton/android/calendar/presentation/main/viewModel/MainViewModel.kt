package me.proton.android.calendar.presentation.main.viewModel

import android.app.Application
import android.content.*
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.work.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.common.FeatureFlag.MONTH_VIEW
import me.proton.android.calendar.common.provider.DefaultSharedPreferencesProvider
import me.proton.android.calendar.common.utils.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.utils.IcsSurgeryUtils
import me.proton.android.calendar.common.worker.UseCaseWorker
import me.proton.android.calendar.domain.usecase.*
import me.proton.android.calendar.presentation.main.MainActivity
import me.proton.core.account.domain.repository.AccountRepository
import me.proton.core.domain.entity.UserId
import me.proton.core.network.domain.NetworkManager
import java.io.BufferedReader
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val accountRepository: AccountRepository,
    private val handleIcsUseCase: HandleIcsUseCase,
    private val networkManager: NetworkManager,
    private val defaultSharedPreferencesProvider: DefaultSharedPreferencesProvider
) : AndroidViewModel(application) {

    private val intents = mutableMapOf<String, Intent>()

    val isConnectedToNetwork get() = networkManager.isConnectedToNetwork()

    /**
     * Try to open maps with event location.
     */
    fun handleEventLocationShow(location: String): Boolean {
        return try {
            val googleMapsUri = Uri.parse("geo:0,0?q=$location")
            val googleMapsIntent = Intent(Intent.ACTION_VIEW, googleMapsUri)
            googleMapsIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            googleMapsIntent.setPackage("com.google.android.apps.maps")
            getApplication<Application>().startActivity(googleMapsIntent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    fun handleCopyToClipboard(content: String): Boolean {
        return try {
            val clipboard: ClipboardManager? = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
            if (clipboard != null) {
                val clip = ClipData.newPlainText("", content)
                clipboard.setPrimaryClip(clip)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    fun isAlternativeRoutingEnabled(): Boolean {
        return defaultSharedPreferencesProvider.sharedPreferences.getBoolean(SharedPreferencesKeys.ALTERNATIVE_ROUTING, true)
    }

    fun setAlternativeRoutingEnabled(enabled: Boolean) {
        val isAlternativeRoutingEnabled = isAlternativeRoutingEnabled()
        if (isAlternativeRoutingEnabled != enabled) {
            val editor = defaultSharedPreferencesProvider.sharedPreferences.edit()
            editor.putBoolean(SharedPreferencesKeys.ALTERNATIVE_ROUTING, enabled)
            editor.apply()
        }
    }

    fun setViewMode(viewMode: ViewMode) {
        val editor = defaultSharedPreferencesProvider.sharedPreferences.edit()
        editor.putInt(SharedPreferencesKeys.VIEW_MODE, viewMode.value)
        editor.apply()
    }

    fun getLastViewMode(): ViewMode {
        // By default we display the month view
        val lastViewMode = ViewMode.values()[defaultSharedPreferencesProvider.sharedPreferences.getInt(SharedPreferencesKeys.VIEW_MODE, ViewMode.MONTH.value)]
        return if (!MONTH_VIEW && lastViewMode == ViewMode.MONTH) ViewMode.AGENDA else lastViewMode
    }

    // TODO sync all "active" accounts
    fun syncServerEvents(userId: UserId) : LiveData<Operation.State> {

        val workState = kotlin.runCatching {
            WorkManager.getInstance(getApplication<Application>()).getWorkInfosForUniqueWork(UseCaseWorker.UniqueWorkNames.SYNC_SERVER_EVENTS).get(1, TimeUnit.SECONDS)
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
        return WorkManager.getInstance(getApplication<Application>()).enqueueUniqueWork(UseCaseWorker.UniqueWorkNames.SYNC_SERVER_EVENTS, existingWorkPolicy, work).state

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
        return WorkManager.getInstance(getApplication<Application>()).enqueueUniqueWork(UseCaseWorker.UniqueWorkNames.SYNC_ALARMS, ExistingWorkPolicy.REPLACE, work).state

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

    fun containsIntent() = intents.isNotEmpty()
    
    fun containsIntent(action: String) = intents.containsKey(action)

    /**
     * Returns and deletes intent with given [action], if it has been handled previously.
     */
    fun consumeIntent(action: String): Intent? {
        return intents.remove(action)
    }

    companion object {
        const val INTENT_ACTION_SHOW_EVENT_DETAILS = "INTENT_ACTION_SHOW_EVENT_DETAILS"
        const val INTENT_ACTION_SHOW_DAY = "INTENT_ACTION_SHOW_DAY"
        const val INTENT_ACTION_NEW_EVENT = "INTENT_ACTION_NEW_EVENT"

        fun createMainIntentToShowEventDetails(context: Context, eventId: String, occurrenceNumber: Int?): Intent {
            return Intent(context, MainActivity::class.java).apply {
                action = INTENT_ACTION_SHOW_EVENT_DETAILS
                data = Navigation.Deeplink.toMainActivityWithEventId(eventId, occurrenceNumber ?: 0)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        }
    }

    suspend fun handleIcsFile(bufferedReader: BufferedReader, senderEmail: String?, recipientEmail: String?): IcsSurgeryUtils.HandleIcsResult {
        val userId = accountRepository.getPrimaryUserId().firstOrNull() ?: return IcsSurgeryUtils.HandleIcsResult.Error.DefaultError
        val iCalString = bufferedReader.use { it.readText() }
        if (isConnectedToNetwork.not()) return IcsSurgeryUtils.HandleIcsResult.Error.NetworkError
        return handleIcsUseCase.execute(iCalString, userId, senderEmail, recipientEmail)
    }
}
