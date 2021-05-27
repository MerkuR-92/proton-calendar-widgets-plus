package me.proton.android.calendar.presentation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.Menu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.GravityCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED
import androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_UNLOCKED
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.*
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import biweekly.parameter.ParticipationStatus
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_root.*
import kotlinx.android.synthetic.main.nav_view_main.*
import kotlinx.android.synthetic.main.nav_view_main.view.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import me.proton.android.calendar.BuildConfig
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.common.AndroidUtils.displayCalendarListMaterialDialog
import me.proton.android.calendar.common.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.AndroidUtils.getInitials
import me.proton.android.calendar.common.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.AppLinksAction.VIEW
import me.proton.android.calendar.common.AppLinksQueryParameters.ACTION
import me.proton.android.calendar.common.AppLinksQueryParameters.CALENDAR_ID
import me.proton.android.calendar.common.AppLinksQueryParameters.EVENT_ID
import me.proton.android.calendar.common.AppLinksQueryParameters.RECURRENCE_ID
import me.proton.android.calendar.common.DateTimeUtilsImpl.formatTimeZoneId
import me.proton.android.calendar.common.FeatureFlag.APP_LINKS
import me.proton.android.calendar.common.FeatureFlag.OPEN_ICS_FILES
import me.proton.android.calendar.common.IcsSurgeryUtils.HandleIcsResult.Error
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.data.entity.CalendarSubscriptionEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.ShowNotificationUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.presentation.account.AccountViewModel
import me.proton.android.calendar.presentation.calendar.CalendarViewModel
import me.proton.android.calendar.presentation.calendar.EventEditDeleteOption
import me.proton.android.calendar.presentation.calendar.EventViewModel
import me.proton.android.calendar.presentation.forceupdate.ForceUpdateViewModel
import me.proton.core.presentation.utils.showForceUpdate
import me.proton.core.util.kotlin.nullIfBlank
import org.koin.android.ext.android.inject
import org.koin.android.viewmodel.ext.android.viewModel
import org.koin.core.KoinComponent
import java.io.*
import java.time.ZonedDateTime
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), KoinComponent {

    private lateinit var appBarConfiguration: AppBarConfiguration

    private lateinit var navController: NavController

    private val logger: Logger by inject()

    @Inject
    lateinit var forceUpdateViewModel: ForceUpdateViewModel

    private val calendarViewModel: CalendarViewModel by viewModel()
    private val eventViewModel: EventViewModel by viewModel()
    private val mainViewModel: MainViewModel by viewModel()
    private val accountViewModel: AccountViewModel by viewModel()
    private lateinit var userCalendarListAdapter: CalendarListAdapter
    private lateinit var subscribedCalendarListAdapter: CalendarListAdapter

    private var subscribedCalendars: List<CalendarEntity>? = null
    private var calendarSubscriptions: List<CalendarSubscriptionEntity>? = null
    private val subscribedCalendarsMediator = MediatorLiveData<Pair<List<CalendarEntity>, List<CalendarSubscriptionEntity>>>()

    private fun navigateTo(uri: Uri) {
        lifecycleScope.launch(Dispatchers.Default) {

            val userId = accountViewModel.getPrimaryUserId()
            if (userId == null) {

                logger.e("navigating from 'account ready' but userId is null")
                accountViewModel.logoutPrimary()
                calendarViewModel.shutdown()

            } else {

                calendarViewModel.initForUser(userId).collect {
                    when (it) {
                        CalendarsRepository.InitingState.Initing -> {
                            withContext(Dispatchers.Main) {
                                displaySplashScreen(true, true, resources.getString(R.string.splash_init))
                            }
                            logger.v("regular init, waiting in main activity")
                        }
                        CalendarsRepository.InitingState.Error -> {
                            withContext(Dispatchers.Main) {
                                displaySplashScreen(false)
                            }
                            logger.e("navigating from `account ready` but error initialising calendarViewModel")

                            accountViewModel.logoutPrimary()
                            calendarViewModel.shutdown()
                        }
                        CalendarsRepository.InitingState.Finished -> {
                            logger.v("regular init, got finished")
                            withContext(Dispatchers.Main) {
                                displaySplashScreen(false)
                            }

                            // Refresh drawer content now that we are logged in.
                            initDrawerHeader()
                            initDrawerCalendarsListContent()

                            withContext(Dispatchers.Main) {
                                findNavController(R.id.nav_host_fragment_container_view).navigate(uri)
                            }
                        }
                    }
                }
            }
        }
    }

    fun getAppTheme(): AppTheme {
        return AppTheme.values()[PreferenceManager.getDefaultSharedPreferences(this).getInt(SharedPreferencesKeys.THEME, AppTheme.SYSTEM_DEFAULT.value)]
    }

    fun changeAppTheme(theme: AppTheme) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        val editor = sharedPreferences.edit()
        editor.putInt(SharedPreferencesKeys.THEME, theme.value)
        editor.apply()

        handleAppTheme()
    }

    private fun handleAppTheme() {
        when (getAppTheme()) {
            AppTheme.LIGHT -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            AppTheme.DARK -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
            else -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        with(accountViewModel) {
            val state = state.value
            if (state == AccountViewModel.State.Ready && navController.currentDestination?.id == R.id.rootFragment) {
                logger.i("MainActivity onResume force handleAccountState to get out of limbo")
                handleAccountState(this, state)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        handleAppTheme()
        super.onCreate(savedInstanceState)

        // https://stackoverflow.com/questions/16283079/re-launch-of-activity-on-home-button-but-only-the-first-time/16447508#16447508
        if (!isTaskRoot && intent.action != INVITE_PROTON_INTENT_ACTION && intent.action != Intent.ACTION_VIEW && intent.type != INVITE_ICS_MIME_TYPE) {
            // Android launched another instance of the root activity into an existing task
            //  so just quietly finish and go away, dropping the user back into the activity
            //  at the top of the stack (ie: the last state of this task)
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        val navView: NavigationView = findViewById(R.id.nav_view)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_container_view) as NavHostFragment
        navController = navHostFragment.navController
        navView.setupWithNavController(navController)

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_calendar//, R.id.nav_settings, R.id.nav_contacts, R.id.nav_feedback
            ), drawer_layout
        )

        intent?.let { mainViewModel.handleIntent(intent) }

        with(forceUpdateViewModel) {
            forceUpdate.observe(this@MainActivity, Observer {
                if (it.forceUpdate) {
                    supportFragmentManager.showForceUpdate(it.apiErrorMessage)
                }
            })
        }

        with(accountViewModel) {
            init(this@MainActivity)

            // Close app if AddAccount screen has been closed.
            onAddAccountClosed { finish() }

            state.onEach { state ->
                if (errorReport.value == null) {
                    handleAccountState(this, state)
                }
            }.launchIn(lifecycleScope)

            // Handle Bootstrap errors
            errorReport.observe(this@MainActivity, Observer { errorReport ->
                errorReport ?: return@Observer
                var dialogTitle: Int? = null
                var dialogMessage: Int? = null
                var dialogPositiveButton = R.string.bootstrap_error_default_confirm
                when (errorReport) {
                    UseCase.Error.NO_CALENDAR -> {
                        dialogTitle = R.string.bootstrap_error_no_calendar_title
                        dialogMessage = R.string.bootstrap_error_no_calendar_message
                    }
                    UseCase.Error.NO_ACTIVE_CALENDAR -> {
                        dialogTitle = R.string.bootstrap_error_no_active_calendar_title
                        dialogMessage = R.string.bootstrap_error_no_active_calendar_message
                    }
                    UseCase.Error.RESET_NEEDED -> {
                        dialogTitle = R.string.bootstrap_error_reset_needed_title
                        dialogMessage = R.string.bootstrap_error_reset_needed_message
                        dialogPositiveButton = R.string.bootstrap_error_continue_button
                    }
                    UseCase.Error.UPDATE_PASSPHRASE -> {
                        dialogTitle = R.string.bootstrap_error_update_passphrase_title
                        dialogMessage = R.string.bootstrap_error_update_passphrase_message
                        dialogPositiveButton = R.string.bootstrap_error_continue_button
                    }
                }

                // This can not happen
                if (dialogTitle == null || dialogMessage == null) {
                    clearError()
                    handleAccountState(this, state.value!!)
                    return@Observer
                }

                if (errorReport == UseCase.Error.RESET_NEEDED || errorReport == UseCase.Error.UPDATE_PASSPHRASE) {
                    // Display dialog with list of calendars to fix
                    lifecycleScope.launch {
                        // If we fail to fetch calendars, we still display dialog without the calendar list
                        val userId = accountViewModel.getPrimaryUserId()
                        val calendars = if (userId != null) calendarViewModel.fetchCalendars(userId) ?: arrayListOf() else arrayListOf()
                        this@MainActivity.displayCalendarListMaterialDialog(
                            dialogTitle,
                            dialogMessage,
                            false,
                            if (errorReport == UseCase.Error.RESET_NEEDED) calendars.filter { it.isResetNeeded }
                            else calendars.filter { it.hasUpdatePassphrase }
                        ) { _, _ ->
                            if (errorReport == UseCase.Error.RESET_NEEDED) {
                                clearError()
                                userId?.let { accountViewModel.resetCalendarsKey(it) }
                            } else if (errorReport == UseCase.Error.UPDATE_PASSPHRASE) {
                                clearError()
                                userId?.let { accountViewModel.updatePassphrase(it) }
                            }
                        }
                    }
                } else {
                    // Display normal error dialogs
                    val materialDialog = MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle(dialogTitle)
                        .setMessage(dialogMessage)
                        .setCancelable(false)
                        .setPositiveButton(dialogPositiveButton) { _, _ ->
                            clearError()
                            handleAccountState(this, state.value!!)
                        }.show()
                    materialDialog.findViewById<TextView>(android.R.id.message)?.movementMethod =
                        LinkMovementMethod.getInstance()
                }
            })
        }

        nav_view_main_content.nav_view_version.text = getString(
            R.string.nav_view_version_name,
            BuildConfig.VERSION_NAME
        )

        initDrawerListeners()

        initDrawerCalendarsList()

        // Set timezone visibility to gone by default
        nav_view_timezone.visibleOrGone(false)
    }

    private fun handleAccountState(accountViewModel: AccountViewModel, state: AccountViewModel.State) {
        when (state) {
            AccountViewModel.State.LoginNeeded -> {
                var openIcsIntent = mainViewModel.consumeIntent(INVITE_PROTON_INTENT_ACTION)
                if (openIcsIntent != null) {
                    Toast.makeText(this, getString(R.string.snack_import_event_signed_out), Toast.LENGTH_LONG).show()
                } else {
                    openIcsIntent = mainViewModel.consumeIntent(Intent.ACTION_VIEW)
                    if (openIcsIntent != null) {
                        Toast.makeText(this, getString(R.string.snack_app_link_signed_out), Toast.LENGTH_LONG).show()
                    }
                }
                findNavController(R.id.nav_host_fragment_container_view).navigate(Navigation.Deeplink.toRoot())
                accountViewModel.addAccount()
                ShowNotificationUseCase.cancelAllNotifications(this@MainActivity)
            }
            AccountViewModel.State.Ready -> {
                // Default navigate to root
                navController.navigate(Navigation.Deeplink.toRoot())

                val eventDetailsIntent =
                    mainViewModel.consumeIntent(MainViewModel.INTENT_ACTION_SHOW_EVENT_DETAILS)

                if (eventDetailsIntent != null && eventDetailsIntent.data != null) {
                    logger.v("converting deeplink and navigating manually")

                    // convert "main" deeplink to "event details" deeplink and navigate manually
                    val eventId = eventDetailsIntent.data?.getQueryParameter("eventId")
                    val occurrenceNumber = eventDetailsIntent.data?.getQueryParameter("occurrenceNumber")
                    if (eventId != null && occurrenceNumber != null) {
                        val eventDetailsDeepLink = Navigation.Deeplink.toEventDetails(eventId, occurrenceNumber.toInt())
                        navigateTo(eventDetailsDeepLink)
                    } else {
                        logger.e("could not get eventId/occurrenceNumber from INTENT_ACTION_SHOW_EVENT_DETAILS")
                        navigateTo(Navigation.Deeplink.toMonth())
                    }

                } else {
                    val openIcsIntent = mainViewModel.consumeIntent(INVITE_PROTON_INTENT_ACTION)
                    if (openIcsIntent != null && FeatureFlag.OPEN_ICS) {
                        handleIcsIntent(openIcsIntent)
                    } else if (openIcsIntent == null && OPEN_ICS_FILES || APP_LINKS) {
                        val actionViewIntent = mainViewModel.consumeIntent(Intent.ACTION_VIEW)
                        if (actionViewIntent?.type == INVITE_ICS_MIME_TYPE && OPEN_ICS_FILES) {
                            // Handle ics file
                            handleIcsIntent(actionViewIntent)
                        } else if (actionViewIntent != null && APP_LINKS) {
                            // Handle app link
                            val appLinkData: Uri? = actionViewIntent.data
                            val eventId = appLinkData?.getQueryParameter(EVENT_ID)
                            val calendarId = appLinkData?.getQueryParameter(CALENDAR_ID)
                            val recurrenceId = appLinkData?.getQueryParameter(RECURRENCE_ID)
                            val action = appLinkData?.getQueryParameter(ACTION)
                            if (eventId != null && calendarId != null && recurrenceId != null && action == VIEW) {
                                handleAppLinkIntent(eventId, calendarId, recurrenceId)
                            } else {
                                this@MainActivity.displaySnackBar(getString(R.string.snack_app_link_invalid))
                                navigateTo(Navigation.Deeplink.toMonth())
                            }
                        } else navigateTo(Navigation.Deeplink.toMonth())
                    } else navigateTo(Navigation.Deeplink.toMonth())
                }
            }
            AccountViewModel.State.Processing -> {
                displaySplashScreen(
                    display = true,
                    spinner = true,
                    spinnerText = resources.getString(R.string.splash_after_login_init)
                )
            }
        }
    }

    private fun handleIcsIntent(openIcsIntent: Intent) {
        val uri = openIcsIntent.data
        if (uri != null) {
            val senderEmail = openIcsIntent.getStringExtra(INVITE_PROTON_EXTRA_SENDER_EMAIL)
            val recipientEmail = openIcsIntent.getStringExtra(INVITE_PROTON_EXTRA_RECIPIENT_EMAIL)
            handleOpenIcsIntent(uri, senderEmail, recipientEmail)
        } else navigateTo(Navigation.Deeplink.toMonth())
    }

    private fun handleAppLinkIntent(eventId: String, calendarId: String, recurrenceId: String) {
        lifecycleScope.launch {
            val userId = accountViewModel.getPrimaryUserId()
            if (userId == null) {
                navigateTo(Navigation.Deeplink.toMonth())
                return@launch // TODO Display error ?
            }
            when (val handleEventLinkResult = eventViewModel.handleEventLink(userId, eventId, calendarId, recurrenceId)) {
                is EventViewModel.EventLinkResult.Success -> {
                    val eventDetailsDeepLink = Navigation.Deeplink.toEventDetails(eventId, handleEventLinkResult.occurrenceNumber)
                    navigateTo(eventDetailsDeepLink)
                }
                is EventViewModel.EventLinkResult.DecryptionFailed -> {
                    navigateTo(Navigation.Deeplink.toMonth())
                    val confirmationMessage =
                        if (handleEventLinkResult.event.isRecurring()) R.string.event_decryption_error_dialog_confirmation_recurring
                        else R.string.event_decryption_error_dialog_confirmation
                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle(R.string.event_decryption_error_dialog_title)
                        .setMessage(R.string.event_decryption_error_dialog_message)
                        .setPositiveButton(confirmationMessage) { _, _ ->
                            lifecycleScope.launch { // TODO
                                val deleteResult = withContext(Dispatchers.Default) {
                                    calendarViewModel.handleDeleteEvent(
                                        eventId,
                                        EventEditDeleteOption.ALL_EVENTS
                                    )
                                }
                                if (deleteResult is UseCase.Result.Success<*>) {
                                    this@MainActivity.displaySnackBar(getString(R.string.snack_event_deleted))
                                } else {
                                    if (deleteResult is UseCase.Result.Error) {
                                        logger.e("Error deleting event: ${deleteResult.message}")
                                    } else if (deleteResult is UseCase.Result.InvalidParams) {
                                        logger.e("InvalidParams deleting event: ${deleteResult.message}")
                                    }
                                    this@MainActivity.displaySnackBar(getString(R.string.snack_event_deleted_error))
                                }
                            }
                        }
                        .setNegativeButton(R.string.event_decryption_error_dialog_close) { _, _ -> }
                        .show()
                }
                is EventViewModel.EventLinkResult.EventDoesNotExist -> {
                    this@MainActivity.displaySnackBar(getString(R.string.snack_app_link_invalid))
                    navigateTo(Navigation.Deeplink.toMonth())
                }
                is EventViewModel.EventLinkResult.OccurrenceDoesNotExist -> {
                    this@MainActivity.displaySnackBar(getString(R.string.error_occurrence_does_not_exist))
                    navigateTo(Navigation.Deeplink.toMonth())
                }
                is EventViewModel.EventLinkResult.Error -> {
                    this@MainActivity.displaySnackBar(getString(R.string.snack_app_link_error))
                    navigateTo(Navigation.Deeplink.toMonth())
                }
            }
        }
    }

    private fun handleOpenIcsIntent(uri: Uri, senderEmail: String?, recipientEmail: String?) {

        if (!OPEN_ICS_FILES) {
            if (senderEmail == null || recipientEmail == null) {
                this@MainActivity.displaySnackBar(getString(R.string.snack_ics_default_error), Snackbar.LENGTH_LONG)
                navigateTo(Navigation.Deeplink.toMonth())
                return
            }
        }

        // openInputStream blocks current thread and coroutine cannot be properly suspended so we call it before launch
        val bufferedReader = BufferedReader(InputStreamReader(this@MainActivity.contentResolver.openInputStream(uri)))
        lifecycleScope.launch {
            displaySplashScreen(
                display = true,
                spinner = true,
                spinnerText = resources.getString(R.string.splash_init)
            )

            val handleIcsImportResult = mainViewModel.handleIcsFile(bufferedReader, senderEmail, recipientEmail)
            if (handleIcsImportResult is IcsSurgeryUtils.HandleIcsResult.Success) {
                when (handleIcsImportResult.action) {
                    // We use Toast because we do not have the EventDetails view required for SnackBar to be displayed
                    IcsSurgeryUtils.HandleIcsAction.CREATE_EVENT ->
                        Toast.makeText(this@MainActivity, getString(R.string.snack_event_created), Toast.LENGTH_LONG)
                            .show()
                    IcsSurgeryUtils.HandleIcsAction.UPDATE_EVENT -> {
                        when (handleIcsImportResult.newAttendeeStatus?.second) {
                            ParticipationStatus.ACCEPTED -> Toast.makeText(this@MainActivity, getString(R.string.snack_event_attendee_accepted_answer, handleIcsImportResult.newAttendeeStatus?.first), Toast.LENGTH_LONG).show()
                            ParticipationStatus.TENTATIVE -> Toast.makeText(this@MainActivity, getString(R.string.snack_event_attendee_tentative_answer, handleIcsImportResult.newAttendeeStatus?.first), Toast.LENGTH_LONG).show()
                            ParticipationStatus.DECLINED -> Toast.makeText(this@MainActivity, getString(R.string.snack_event_attendee_declined_answer, handleIcsImportResult.newAttendeeStatus?.first), Toast.LENGTH_LONG).show()
                            else -> Toast.makeText(this@MainActivity, getString(R.string.snack_event_updated), Toast.LENGTH_LONG).show()
                        }
                    }
                    //IcsSurgeryUtils.HandleIcsAction.OPEN_EVENT -> TODO()
                }

                val eventId = handleIcsImportResult.eventId
                val eventDetailsDeepLink = Navigation.Deeplink.toEventDetails(eventId, if (handleIcsImportResult.isRecurring == true) 1 else 0)
                navigateTo(eventDetailsDeepLink)
            } else {
                var navigatedToDetails = false
                when (handleIcsImportResult) {
                    is Error.DefaultError -> this@MainActivity.displaySnackBar(getString(R.string.snack_ics_default_error), Snackbar.LENGTH_LONG)
                    is Error.EventNotFound -> this@MainActivity.displaySnackBar(getString(R.string.snack_ics_event_not_found_error), Snackbar.LENGTH_LONG)
                    is Error.NetworkError -> this@MainActivity.displaySnackBar(getString(R.string.snack_network_error), Snackbar.LENGTH_LONG)
                    is Error.EditCreateEventError -> this@MainActivity.displaySnackBar(getString(R.string.snack_ics_create_error), Snackbar.LENGTH_LONG)
                    is Error.ParsingFailed -> this@MainActivity.displaySnackBar(getString(R.string.snack_ics_parsing_error), Snackbar.LENGTH_LONG)
                    is Error.Unsupported.Method -> this@MainActivity.displaySnackBar(getString(R.string.snack_ics_unsupported_method_error), Snackbar.LENGTH_LONG)
                    is Error.Unsupported.Add -> this@MainActivity.displaySnackBar(getString(R.string.snack_ics_unsupported_add_error), Snackbar.LENGTH_LONG)
                    is Error.Unsupported.Counter -> this@MainActivity.displaySnackBar(getString(R.string.snack_ics_unsupported_counter_error), Snackbar.LENGTH_LONG)
                    is Error.Unsupported.Refresh -> this@MainActivity.displaySnackBar(getString(R.string.snack_ics_unsupported_refresh_error), Snackbar.LENGTH_LONG)
                    is Error.Unsupported.Publish -> this@MainActivity.displaySnackBar(getString(R.string.snack_ics_unsupported_publish_error), Snackbar.LENGTH_LONG)
                    is Error.Unsupported.SingleEditReply -> this@MainActivity.displaySnackBar(getString(R.string.snack_ics_unsupported_single_edit_reply_error), Snackbar.LENGTH_LONG)
                    is Error.PartyCrasher -> this@MainActivity.displaySnackBar(getString(R.string.snack_ics_party_crasher_error), Snackbar.LENGTH_LONG)
                    is Error.MissingUid -> this@MainActivity.displaySnackBar(getString(R.string.snack_ics_missing_uid_error), Snackbar.LENGTH_LONG)
                    is Error.NoDefaultCalendarFound -> this@MainActivity.displaySnackBar(getString(R.string.snack_ics_no_active_calendar_error), Snackbar.LENGTH_LONG)
                    is Error.DurationNotSupported -> this@MainActivity.displaySnackBar(getString(R.string.snack_ics_unsupported_duration_error), Snackbar.LENGTH_LONG)
                    is Error.TooManyEvents -> this@MainActivity.displaySnackBar(getString(R.string.snack_ics_too_many_events_error), Snackbar.LENGTH_LONG)
                    is Error.NoEvents -> this@MainActivity.displaySnackBar(getString(R.string.snack_ics_no_events_error), Snackbar.LENGTH_LONG)
                    is Error.EventDeleted -> this@MainActivity.displaySnackBar(getString(R.string.snack_ics_event_deleted_error), Snackbar.LENGTH_LONG)
                    is Error.Invalid -> this@MainActivity.displaySnackBar(getString(R.string.snack_ics_invalid_error), Snackbar.LENGTH_LONG)
                    is Error.DisabledCalendar -> navigatedToDetails = displayErrorAndOpenDetails(handleIcsImportResult.eventId, getString(R.string.snack_ics_disabled_calendar_error))
                    is Error.ReplyPartyCrasher -> navigatedToDetails = displayErrorAndOpenDetails(handleIcsImportResult.eventId, getString(R.string.snack_ics_reply_party_crasher_error))
                    is Error.Method -> navigatedToDetails = displayErrorAndOpenDetails(handleIcsImportResult.eventId, getString(R.string.snack_ics_invalid_error))
                    is Error.DecryptionFailed -> {
                        if (handleIcsImportResult.eventId != null) {
                            deleteFailedToDecryptEvent(handleIcsImportResult.eventId, handleIcsImportResult.isRecurring)
                        } else this@MainActivity.displaySnackBar(getString(R.string.event_decryption_error_dialog_title), Snackbar.LENGTH_LONG)
                    }
                    else -> this@MainActivity.displaySnackBar(getString(R.string.snack_ics_default_error), Snackbar.LENGTH_LONG)
                }

                if (!navigatedToDetails) navigateTo(Navigation.Deeplink.toMonth())
            }
        }
    }

    private fun deleteFailedToDecryptEvent(eventId: String, isRecurring: Boolean?) {
        val confirmationMessage =
            if (isRecurring == true) R.string.event_decryption_error_dialog_confirmation_recurring
            else R.string.event_decryption_error_dialog_confirmation
        MaterialAlertDialogBuilder(this@MainActivity)
            .setTitle(R.string.event_decryption_error_dialog_title)
            .setMessage(R.string.event_decryption_error_dialog_message)
            .setPositiveButton(confirmationMessage) { _, _ ->
                lifecycleScope.launch { // TODO
                    val deleteResult = withContext(Dispatchers.Default) {
                        calendarViewModel.handleDeleteEvent(
                            eventId,
                            EventEditDeleteOption.ALL_EVENTS
                        )
                    }
                    if (deleteResult is UseCase.Result.Success<*>) {
                        this@MainActivity.displaySnackBar(getString(R.string.snack_event_deleted))
                    } else {
                        if (deleteResult is UseCase.Result.Error) {
                            logger.e("Error deleting event: ${deleteResult.message}")
                        } else if (deleteResult is UseCase.Result.InvalidParams) {
                            logger.e("InvalidParams deleting event: ${deleteResult.message}")
                        }
                        this@MainActivity.displaySnackBar(getString(R.string.snack_event_deleted_error))
                    }
                }
            }
            .setNegativeButton(R.string.event_decryption_error_dialog_close) { _, _ -> }
            .show()
    }

    private fun displayErrorAndOpenDetails(eventId: String?, errorMessage: String): Boolean {
        return if (eventId != null) {
            Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_LONG).show()
            val eventDetailsDeepLink = Navigation.Deeplink.toEventDetails(eventId)
            navigateTo(eventDetailsDeepLink)
            true
        } else {
            this@MainActivity.displaySnackBar(errorMessage, Snackbar.LENGTH_LONG)
            false
        }
    }

    fun displaySplashScreen(display: Boolean, spinner: Boolean = false, spinnerText: String? = null) {
        // TODO Status bar and navigation bar colors are set to brand_norm on dark / light mode change because of activity recreation

        if (spinner) {
            calendarViewModel.fetchingEvents.postValue(spinnerText)
        }

        drawer_layout.setDrawerLockMode(if (display) LOCK_MODE_LOCKED_CLOSED else LOCK_MODE_UNLOCKED)

        val backgroundDrawable = if (display) R.drawable.splash_screen else R.color.background_norm
        val statusBarBackgroundColor = if (display) R.color.brand_norm else R.color.background_norm

        window.setBackgroundDrawableResource(backgroundDrawable)
        window.statusBarColor =  resources.getColor(statusBarBackgroundColor, null)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            val navigationBarBackgroundColor = if (display) R.color.brand_norm else R.color.background_norm
            window.navigationBarColor = resources.getColor(navigationBarBackgroundColor, null)
        } else {
            val navigationBarBackgroundColor = if (display) R.color.brand_norm else R.color.background_navigation_bar
            window.navigationBarColor = resources.getColor(navigationBarBackgroundColor, null)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { mainViewModel.handleIntent(intent) }
    }

    private fun initDrawerListeners() {
        //Navigation drawer items on click listeners
        nav_view_main_content.nav_view_user_layout.setOnSingleClickListener {
            drawer_layout.close()
        }

        nav_view_main_content.nav_view_more_bug_press.setOnSingleClickListener {
            navController.navigate(R.id.action_nav_calendar_to_nav_bug_report)
            drawer_layout.close()
        }
        // TODO Remove feature flag
        nav_view_main_content.nav_view_more_settings_layout.visibleOrGone(FeatureFlag.SETTINGS_DRAWER)
        nav_view_main_content.nav_view_more_settings_press.setOnSingleClickListener {
            navController.navigate(R.id.action_nav_calendar_to_nav_settings)
            drawer_layout.close()
        }
        accountViewModel.hasPrimary.observe(this@MainActivity) { hasPrimary ->
            nav_view_main_content.nav_view_more_logout_layout.isVisible = hasPrimary
            nav_view_main_content.nav_view_more_login_layout.isGone = hasPrimary
        }
        nav_view_main_content.nav_view_more_logout_press.setOnSingleClickListener {
            accountViewModel.logoutPrimary()
            drawer_layout.close()
        }
        nav_view_main_content.nav_view_more_login_press.setOnSingleClickListener {
            accountViewModel.addAccount()
            drawer_layout.close()
        }

        nav_view_switcher_day_press.setOnSingleClickListener {
            calendarViewModel.agendaView.postValue(false)
            drawerLayout.close()
        }

        nav_view_switcher_agenda_press.setOnSingleClickListener {
            calendarViewModel.agendaView.postValue(true)
            drawerLayout.close()
        }
    }

    private fun initDrawerHeader() {
        lifecycleScope.launch {
            val user = withContext(Dispatchers.Default) {
                calendarViewModel.selectUser()
            }
            if (user != null) {
                nav_view_main_content.nav_view_user_name.text = user.displayName?.nullIfBlank() ?: resources.getString(R.string.default_user_display_name)
                nav_view_main_content.nav_view_user_mail.text = user.email?.nullIfBlank() ?: resources.getString(R.string.default_user_email)
                val initials: String = getInitials(user.displayName ?: " ")
                nav_view_main_content.nav_view_user_initials.text = initials
            }
        }
    }

    private fun initDrawerCalendarsList() {
        val userCalendarListView = nav_view_main_content.nav_view_calendars_list
        val userCalendarsLayoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        userCalendarListView.layoutManager = userCalendarsLayoutManager
        userCalendarListAdapter = CalendarListAdapter(calendarViewModel) { calendarEntity ->
            //On Calendar click event
            lifecycleScope.launch {
                calendarViewModel.updateCalendarVisibility(calendarEntity.id, calendarEntity.display)
                updateCalendarsDelayed()
            }
        }
        (userCalendarListView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        userCalendarListView.adapter = userCalendarListAdapter

        val subscribedCalendarListView = nav_view_main_content.nav_view_subscribed_calendars_list
        val subscribedCalendarLayoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        subscribedCalendarListView.layoutManager = subscribedCalendarLayoutManager
        subscribedCalendarListAdapter = CalendarListAdapter(calendarViewModel) { calendarEntity ->
            //On Calendar click event
            lifecycleScope.launch {
                calendarViewModel.updateCalendarVisibility(calendarEntity.id, calendarEntity.display)
                updateCalendarsDelayed()
            }
        }
        (subscribedCalendarListView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        subscribedCalendarListView.adapter = subscribedCalendarListAdapter
    }

    private lateinit var updateCalendarsJob: Job
    private fun updateCalendarsDelayed() {
        if (this::updateCalendarsJob.isInitialized && updateCalendarsJob.isActive) {
            updateCalendarsJob.cancel()
        }
        updateCalendarsJob = lifecycleScope.launch {
            delay(SYNC_CALENDARS_DELAY.toMillis())
            calendarViewModel.updateServerCalendarListDisplay()
        }
    }

    private fun initDrawerCalendarsListContent() {

        lifecycleScope.launch(Dispatchers.Main) {
            calendarViewModel.timeZoneId.observe(this@MainActivity) { zoneId ->
                nav_view_timezone.visibleOrGone(true)
                nav_view_timezone_login_title.text =
                    formatTimeZoneId(zoneId.id, ZonedDateTime.now(zoneId).toInstant())
            }
        }

        lifecycleScope.launch {
            calendarViewModel.selectCalendars()
            calendarViewModel.userCalendars.observe(this@MainActivity) { userCalendars ->
                userCalendars ?: return@observe
                userCalendarListAdapter.submitList(
                    userCalendars.sortedBy {
                        it.isDisabled
                    }
                )
                nav_view_main_content.nav_view_calendars.visibleOrGone(userCalendars.isNotEmpty())
            }


            subscribedCalendarsMediator.addSource(calendarViewModel.subscribedCalendars) { value ->
                subscribedCalendars = value

                if (subscribedCalendars != null && calendarSubscriptions != null) {
                    subscribedCalendarsMediator.value = Pair(subscribedCalendars!!, calendarSubscriptions!!)
                }
            }
            subscribedCalendarsMediator.addSource(calendarViewModel.calendarSubscriptions) { value ->
                calendarSubscriptions = value

                if (subscribedCalendars != null && calendarSubscriptions != null) {
                    subscribedCalendarsMediator.value = Pair(subscribedCalendars!!, calendarSubscriptions!!)
                }
            }
            subscribedCalendarsMediator.observe(this@MainActivity) {
                it?.let {
                    val subscribedCalendars = it.first
                    val calendarSubscriptions = it.second
                    val dataSetChanged = subscribedCalendarListAdapter.setCalendarSubscriptions(calendarSubscriptions)
                    subscribedCalendarListAdapter.submitList(subscribedCalendars)
                    if (dataSetChanged) subscribedCalendarListAdapter.notifyDataSetChanged()
                    nav_view_main_content.nav_view_subscribed_calendars.visibleOrGone(subscribedCalendars.isNotEmpty())
                }
            }

            calendarViewModel.inactiveUserCalendars.observe(this@MainActivity) { inactiveCalendars ->
                inactiveCalendars ?: return@observe

                // TODO Uncomment once calendar key reactivation has been fixed
//                if (inactiveCalendars.firstOrNull { it.hasUpdatePassphrase } != null && !calendarViewModel.updatingCalendarPassphrase) {
//                    handleUpdatePassphrase()
//                }
            }
        }
    }

    private lateinit var inactiveCalendarsJob: Job
    private fun handleUpdatePassphrase() {
        if (this::inactiveCalendarsJob.isInitialized && inactiveCalendarsJob.isActive) {
            inactiveCalendarsJob.cancel()
        }
        inactiveCalendarsJob = lifecycleScope.launch {
            delay(UPDATE_PASSPHRASE_CALENDARS_DELAY.toMillis())
            val calendarsToUpdate = calendarViewModel.inactiveUserCalendars.value?.filter { it.hasUpdatePassphrase } ?: return@launch
            calendarViewModel.updatingCalendarPassphrase = true
            this@MainActivity.displayCalendarListMaterialDialog(
                R.string.bootstrap_error_update_passphrase_title,
                R.string.bootstrap_error_update_passphrase_message,
                false,
                calendarsToUpdate) { _, _ ->
                lifecycleScope.launch {
                    calendarViewModel.updateInactiveCalendarsPassphrase()
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
//        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_container_view)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onBackPressed() {

        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
        } else if (navController.currentDestination?.id == R.id.nav_calendar) {
            moveTaskToBack(true)
        } else {
            super.onBackPressed()
        }
    }
}
