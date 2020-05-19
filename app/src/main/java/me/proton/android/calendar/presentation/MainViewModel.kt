package me.proton.android.calendar.presentation

import android.content.*
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.work.*
import me.proton.android.calendar.R
import me.proton.android.calendar.common.TimberLogger
import me.proton.android.calendar.common.UseCaseWorker
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.domain.CalendarsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.Exception


class MainViewModel(private val context: Context, calendarsRepository: CalendarsRepository) : ViewModel() {

    // TODO list of users for navbar
    // TODO list of calendars for navbar

    /**
     * Try to open maps with event location.
     */
    fun handleEventLocationShow(location: String) {
        try {
            val googleMapsUri = Uri.parse("geo:0,0?q=$location")
            val googleMapsIntent = Intent(Intent.ACTION_VIEW, googleMapsUri)
            googleMapsIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            googleMapsIntent.setPackage("com.google.android.apps.maps")
            context.startActivity(googleMapsIntent)
        } catch (e: ActivityNotFoundException) {
            TimberLogger.v("google maps activity not found")
        }
    }

    fun handleCopyToClipboard(content: String): Boolean {
        return try {
            val clipboard: ClipboardManager? = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
            if (clipboard != null) {
                val clip = ClipData.newPlainText("", content)
                clipboard.setPrimaryClip(clip)
                showToast(context.getString(R.string.toast_copied_to_clipboard))
                TimberLogger.v("copying to clipboard success")
                true
            } else false
        } catch (e: Exception) {
            TimberLogger.v("copying to clipboard failed", e)
            false
        }
    }

    fun showToast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    // TODO sync all "active" accounts
    fun syncServerEvents() : LiveData<Operation.State> {

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val work = OneTimeWorkRequestBuilder<UseCaseWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(
                UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.SYNC_SERVER_EVENTS,
                UseCaseWorker.INPUT_USER_ID to "IXFh2TE4LI11sd0GYf94r7fddHNMdZvicfoWMACCjPTS-oNjpBjeclhKlIs6N48-GB5w-zM6uqX_9HFgEnzhYQ=="
            ))
            .build()

        return WorkManager.getInstance(context).enqueueUniqueWork(UseCaseWorker.UniqueWorkNames.SYNC_SERVER_EVENTS, ExistingWorkPolicy.REPLACE, work).state

    }






























    private var viewModelJob = Job()
//    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)
//    private val ioScope = CoroutineScope(Dispatchers.IO + viewModelJob)

//    val calendars: LiveData<List<CalendarEntity>> = calendarsRepository.flowCalendars(/*TODO*/"IXFh2TE4LI11sd0GYf94r7fddHNMdZvicfoWMACCjPTS-oNjpBjeclhKlIs6N48-GB5w-zM6uqX_9HFgEnzhYQ==").asLiveData(Dispatchers.Default)
    // TODO events need calendarID as param
//    val events: LiveData<List<Event>> = calendarsRepository.eventsFlow("m-dPNuHcP8N4xfv6iapVg2wHifktAD1A1pFDU95qo5f14Vaw8I9gEHq-3GACk6ef3O12C3piRviy_D43Wh7xxQ==").asLiveData(Dispatchers.Default)

    init {

        // TODO UseCase needs that Result.retry() to run frequently
        // TODO this is just for fun, just for testing
//        SharedPreferencesProvider(getApplication()).provideSharedPreferencesFor("adamtst").edit().putString(
//            ValueKey.LAST_SERVER_EVENT_ID, "l_o7TdJpH3UCfYn-0xBWaJVlZ633baHNvjyZFvuX7TD6lFPaOzy-3YkSorWafW5nKivpxfZ1YaU66_d25R58-Q==").apply()

//        val runningAppSyncWorkRequest =
//            PeriodicWorkRequestBuilder<UseCaseWorker>(10 /*TODO*/, TimeUnit.SECONDS)
//                .setConstraints(
//                    Constraints.Builder()
//                        .setRequiredNetworkType(NetworkType.CONNECTED)
//                        .build()
//                )
//                .build()
        // .setBackoffCriteria(
        //                BackoffPolicy.LINEAR,
        //                OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
        //                TimeUnit.MILLISECONDS)

//        TimberLogger.d("starting unique periodic work ${runningAppSyncWorkRequest.id}")
//        WorkManager.getInstance(context).enqueueUniquePeriodicWork(UseCaseWorker.UNIQUE_NAME_SYNC, ExistingPeriodicWorkPolicy.REPLACE /*TODO I think this should be REPLACE in production, but it's not working for testing*/, runningAppSyncWorkRequest)





        // we are launching a coroutine in UI scope because it affects the UI
//        uiScope.launch {
//            _calendars.value = withContext(Dispatchers.IO) {
//
//                calendarsRepository.selectCalendars()
//            }
//        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()

//        TimberLogger.d("cancelling unique sync work") // TODO this will still be alive when we just leave the app
        //WorkManager.getInstance(context).cancelUniqueWork(UseCaseWorker.UNIQUE_NAME_SYNC)
    }

    fun start() {

    }


}