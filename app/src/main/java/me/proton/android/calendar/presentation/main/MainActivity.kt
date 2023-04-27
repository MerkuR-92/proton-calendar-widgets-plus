package me.proton.android.calendar.presentation.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.Settings
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.GravityCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED
import androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_UNLOCKED
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.tasks.Task
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.activity_main.drawer_layout
import kotlinx.android.synthetic.main.activity_main.nav_view_main_content
import kotlinx.android.synthetic.main.dialog_checkbox.view.dialog_checkbox
import kotlinx.android.synthetic.main.dialog_checkbox.view.dialog_checkbox_header
import kotlinx.android.synthetic.main.dialog_checkbox.view.dialog_checkbox_press
import kotlinx.android.synthetic.main.nav_view_main.nav_view_calendars_create
import kotlinx.android.synthetic.main.nav_view_main.nav_view_calendars_list_add_layout
import kotlinx.android.synthetic.main.nav_view_main.nav_view_calendars_list_add_layout_press
import kotlinx.android.synthetic.main.nav_view_main.nav_view_switcher_agenda_press
import kotlinx.android.synthetic.main.nav_view_main.nav_view_switcher_day_press
import kotlinx.android.synthetic.main.nav_view_main.nav_view_switcher_month_layout
import kotlinx.android.synthetic.main.nav_view_main.nav_view_switcher_month_press
import kotlinx.android.synthetic.main.nav_view_main.nav_view_switcher_three_day_press
import kotlinx.android.synthetic.main.nav_view_main.nav_view_switcher_week_press
import kotlinx.android.synthetic.main.nav_view_main.nav_view_timezone
import kotlinx.android.synthetic.main.nav_view_main.nav_view_user_layout
import kotlinx.android.synthetic.main.nav_view_main.view.nav_view_calendars_list
import kotlinx.android.synthetic.main.nav_view_main.view.nav_view_more_bug_press
import kotlinx.android.synthetic.main.nav_view_main.view.nav_view_more_feedback_layout
import kotlinx.android.synthetic.main.nav_view_main.view.nav_view_more_feedback_press
import kotlinx.android.synthetic.main.nav_view_main.view.nav_view_more_login_layout
import kotlinx.android.synthetic.main.nav_view_main.view.nav_view_more_login_press
import kotlinx.android.synthetic.main.nav_view_main.view.nav_view_more_logout_layout
import kotlinx.android.synthetic.main.nav_view_main.view.nav_view_more_logout_press
import kotlinx.android.synthetic.main.nav_view_main.view.nav_view_more_settings_layout
import kotlinx.android.synthetic.main.nav_view_main.view.nav_view_more_settings_press
import kotlinx.android.synthetic.main.nav_view_main.view.nav_view_more_subscription_layout
import kotlinx.android.synthetic.main.nav_view_main.view.nav_view_more_subscription_press
import kotlinx.android.synthetic.main.nav_view_main.view.nav_view_subscribed_calendars
import kotlinx.android.synthetic.main.nav_view_main.view.nav_view_subscribed_calendars_list
import kotlinx.android.synthetic.main.nav_view_main.view.nav_view_switcher_agenda_icon
import kotlinx.android.synthetic.main.nav_view_main.view.nav_view_switcher_agenda_layout
import kotlinx.android.synthetic.main.nav_view_main.view.nav_view_switcher_day_icon
import kotlinx.android.synthetic.main.nav_view_main.view.nav_view_switcher_day_layout
import kotlinx.android.synthetic.main.nav_view_main.view.nav_view_switcher_month_icon
import kotlinx.android.synthetic.main.nav_view_main.view.nav_view_switcher_month_layout
import kotlinx.android.synthetic.main.nav_view_main.view.nav_view_switcher_three_day_icon
import kotlinx.android.synthetic.main.nav_view_main.view.nav_view_switcher_three_day_layout
import kotlinx.android.synthetic.main.nav_view_main.view.nav_view_switcher_week_icon
import kotlinx.android.synthetic.main.nav_view_main.view.nav_view_switcher_week_layout
import kotlinx.android.synthetic.main.nav_view_main.view.nav_view_user_layout
import kotlinx.android.synthetic.main.nav_view_main.view.nav_view_version
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.BuildConfig
import me.proton.android.calendar.R
import me.proton.android.calendar.WidgetRefresher
import me.proton.android.calendar.common.AppLinksAction.VIEW
import me.proton.android.calendar.common.AppLinksQueryParameters.ACTION
import me.proton.android.calendar.common.AppLinksQueryParameters.CALENDAR_ID
import me.proton.android.calendar.common.AppLinksQueryParameters.EVENT_ID
import me.proton.android.calendar.common.AppLinksQueryParameters.RECURRENCE_ID
import me.proton.android.calendar.common.EventEditDeleteOption
import me.proton.android.calendar.common.FeatureFlag
import me.proton.android.calendar.common.FeatureFlag.APP_LINKS
import me.proton.android.calendar.common.FeatureFlag.FEEDBACK
import me.proton.android.calendar.common.FeatureFlag.IMPORT_ASSISTANT
import me.proton.android.calendar.common.FeatureFlag.IMPORT_ICS
import me.proton.android.calendar.common.FeatureFlag.MONTH_VIEW
import me.proton.android.calendar.common.FeatureFlag.SUBSCRIPTION
import me.proton.android.calendar.common.FeatureFlag.THREE_DAYS_VIEW
import me.proton.android.calendar.common.FeatureFlag.WEEK_VIEW
import me.proton.android.calendar.common.GoogleSignInCodes
import me.proton.android.calendar.common.INVITE_ICS_MIME_TYPE
import me.proton.android.calendar.common.INVITE_PROTON_EXTRA_RECIPIENT_EMAIL
import me.proton.android.calendar.common.INVITE_PROTON_EXTRA_SENDER_EMAIL
import me.proton.android.calendar.common.INVITE_PROTON_INTENT_ACTION
import me.proton.android.calendar.common.IcsParsingValidation
import me.proton.android.calendar.common.Navigation
import me.proton.android.calendar.common.RC_CREATE_IMPORT_SIGN_IN
import me.proton.android.calendar.common.SYNC_CALENDARS_DELAY
import me.proton.android.calendar.common.SharedPreferencesKeys
import me.proton.android.calendar.common.UPDATE_PASSPHRASE_CALENDARS_DELAY
import me.proton.android.calendar.common.ViewMode
import me.proton.android.calendar.common.utils.AndroidUtils.displayCalendarListMaterialDialog
import me.proton.android.calendar.common.utils.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.utils.AndroidUtils.getColorFromAttr
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.utils.CustomLocale
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.fallbackTimeZone
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.common.utils.IcsSurgeryUtils
import me.proton.android.calendar.common.utils.IcsSurgeryUtils.HandleIcsResult.Error
import me.proton.android.calendar.common.utils.ProtonUtilsImpl.displayEventDecryptionErrorDialog
import me.proton.android.calendar.common.utils.ProtonUtilsImpl.displayFreeUserCalendarLimitReached
import me.proton.android.calendar.common.utils.ProtonUtilsImpl.displayPaidUserCalendarLimitReached
import me.proton.android.calendar.data.entity.CalendarSubscriptionEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.usecase.ShowNotificationUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.presentation.account.AccountViewModel
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import me.proton.android.calendar.presentation.calendar.viewModel.EventViewModel
import me.proton.android.calendar.presentation.forceUpdate.ForceUpdateViewModel
import me.proton.android.calendar.presentation.importAssistant.viewModel.ImportAssistantViewModel
import me.proton.android.calendar.presentation.main.adapter.CalendarListAdapter
import me.proton.android.calendar.presentation.main.viewModel.MainViewModel
import me.proton.android.calendar.presentation.subscription.PlansViewModel
import me.proton.core.accountmanager.presentation.viewmodel.AccountSwitcherViewModel
import me.proton.core.presentation.ui.view.ProtonInput
import me.proton.core.presentation.ui.view.ProtonProgressButton
import me.proton.core.util.kotlin.toBooleanOrFalse
import org.koin.core.KoinComponent
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), KoinComponent {

    private lateinit var appBarConfiguration: AppBarConfiguration

    private lateinit var navController: NavController

    @Inject
    lateinit var logger: Logger
    @Inject
    lateinit var widgetRefresher: WidgetRefresher

    @Inject
    lateinit var forceUpdateViewModel: ForceUpdateViewModel

    private val calendarViewModel: CalendarViewModel by viewModels()
    private val eventViewModel: EventViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()
    private val importAssistantViewModel: ImportAssistantViewModel by viewModels()
    private val accountViewModel: AccountViewModel by viewModels()
    private val accountSwitcherViewModel: AccountSwitcherViewModel by viewModels()
    private val plansViewModel: PlansViewModel by viewModels()
    private lateinit var userCalendarListAdapter: CalendarListAdapter
    private lateinit var subscribedCalendarListAdapter: CalendarListAdapter

    private var subscribedCalendars: List<Calendar>? = null
    private var calendarSubscriptions: List<CalendarSubscriptionEntity>? = null
    private val subscribedCalendarsMediator = MediatorLiveData<Pair<List<Calendar>, List<CalendarSubscriptionEntity>>>()

    // Save the current view mode so that we know if we are navigating to day view from the month view
    private var currentViewMode: ViewMode? = null
    // Lets us know whether we need to navigate back to month when triggering back action
    private var returnToView: ViewMode? = null

    private var googleSignInClient: GoogleSignInClient? = null

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
                                displaySplashScreen(true, resources.getString(R.string.splash_init))
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

                                safeFindNavController(R.id.nav_host_fragment_container_view).navigate(uri)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun restartActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
    }

    private fun restartApplication() {
        restartActivity()
        Runtime.getRuntime().exit(0)
    }

    fun changeAppLanguage(localeCode: String?) {
        CustomLocale.apply(this, localeCode)
        restartApplication()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let {
            if (mainViewModel.shouldHandleIntent(intent)) {
                mainViewModel.handleIntent(intent)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (mainViewModel.useDefaultViewMode()) {
            // If user selected a default view mode, force the app to resume on that view
            calendarViewModel.viewMode.value = mainViewModel.getLastViewMode()
        }
        with(accountViewModel) {
            val state = state.value
            if (mainViewModel.containsIntentToHandle()) {
                handleAccountState(this, state)
            } else if (state == AccountViewModel.State.Ready && navController.currentDestination?.id == R.id.rootFragment) {
                logger.i("MainActivity onResume force handleAccountState to get out of limbo")
                handleAccountState(this, state)
            }
        }
    }

    override fun onStart() {
        super.onStart()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkNotificationsPermissions(this)
        }
    }

    private fun safeFindNavController(@IdRes viewId: Int): NavController {
        return try {
            findNavController(viewId)
        } catch (e: IllegalStateException) {
            val navHostFragment =
                supportFragmentManager.findFragmentById(R.id.nav_host_fragment_container_view) as NavHostFragment
            navHostFragment.navController
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition {
            accountViewModel.state.value in listOf(
                AccountViewModel.State.Initial,
                AccountViewModel.State.StepNeeded,
            )
        }
        super.onCreate(savedInstanceState)

        // https://stackoverflow.com/questions/16283079/re-launch-of-activity-on-home-button-but-only-the-first-time/16447508#16447508
        if (!isTaskRoot &&
            !mainViewModel.shouldHandleIntent(intent)
        ) {
            // Android launched another instance of the root activity into an existing task
            //  so just quietly finish and go away, dropping the user back into the activity
            //  at the top of the stack (ie: the last state of this task)
            finish()
            return
        }

        widgetRefresher.broadcastRefresh()

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

        intent?.let {
            if (savedInstanceState == null && mainViewModel.shouldHandleIntent(intent)) mainViewModel.handleIntent(intent)
        }

        plansViewModel.register(this)

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
                    is UseCase.Error.Bootstrap -> {
                        when (errorReport) {
                            UseCase.Error.Bootstrap.NoCalendar -> {
                                dialogTitle = R.string.bootstrap_error_no_calendar_title
                                dialogMessage = R.string.bootstrap_error_no_calendar_message
                            }
                            UseCase.Error.Bootstrap.NoActiveCalendar -> {
                                dialogTitle = R.string.bootstrap_error_no_active_calendar_title
                                dialogMessage = R.string.bootstrap_error_no_active_calendar_message
                            }
                            UseCase.Error.Bootstrap.ResetNeeded -> {
                                dialogTitle = R.string.bootstrap_error_reset_needed_title
                                dialogMessage = R.string.bootstrap_error_reset_needed_message
                                dialogPositiveButton = R.string.bootstrap_error_continue_button
                            }
                            UseCase.Error.Bootstrap.UpdatePassphrase -> {
                                dialogTitle = R.string.bootstrap_error_update_passphrase_title
                                dialogMessage = R.string.bootstrap_error_update_passphrase_message
                                dialogPositiveButton = R.string.bootstrap_error_continue_button
                            }
                            is UseCase.Error.Bootstrap.SomeCalendarsFailedBootstrap -> {
                                dialogTitle = R.string.bootstrap_error_some_calendars_failed_title
                                dialogMessage = R.string.bootstrap_error_some_calendars_failed_message
                            }
                        }
                    }
                    else -> Unit // TODO maybe we should extract `HandleSave` and `Crypto` from UseCase.Error hierarchy?

                }

                // This can not happen
                if (dialogTitle == null || dialogMessage == null) {
                    clearError()
                    accountViewModel.logoutPrimary()
                    handleAccountState(this, state.value!!)
                    return@Observer
                }

                if (errorReport == UseCase.Error.Bootstrap.ResetNeeded || errorReport == UseCase.Error.Bootstrap.UpdatePassphrase) {
                    // Display dialog with list of calendars to fix
                    lifecycleScope.launch {
                        // If we fail to fetch calendars, we still display dialog without the calendar list
                        val userId = accountViewModel.getPrimaryUserId()
                        val calendars = if (userId != null) calendarViewModel.fetchCalendars(userId) ?: arrayListOf() else arrayListOf()
                        this@MainActivity.displayCalendarListMaterialDialog(
                            dialogTitle,
                            dialogMessage,
                            false,
                            if (errorReport == UseCase.Error.Bootstrap.ResetNeeded) calendars.filter { it.isResetNeeded }
                            else calendars.filter { it.hasUpdatePassphrase }
                        ) { _, _ ->
                            if (errorReport == UseCase.Error.Bootstrap.ResetNeeded) {
                                clearError()
                                userId?.let { accountViewModel.resetCalendarsKey(it) }
                            } else if (errorReport == UseCase.Error.Bootstrap.UpdatePassphrase) {
                                clearError()
                                userId?.let { accountViewModel.updatePassphrase(it) }
                            }
                        }
                    }
                } else if (errorReport is UseCase.Error.Bootstrap.SomeCalendarsFailedBootstrap) {
                    // Display dialog with list of calendars to fix
                    lifecycleScope.launch {
                        // If we fail to fetch calendars, we still display dialog without the calendar list
                        val userId = accountViewModel.getPrimaryUserId()
                        val calendars = if (userId != null) calendarViewModel.fetchCalendars(userId) ?: arrayListOf() else arrayListOf()
                        this@MainActivity.displayCalendarListMaterialDialog(
                            dialogTitle,
                            dialogMessage,
                            false,
                            calendars.filter { errorReport.failedCalendarIds.contains(it.id) }
                        ) { _, _ ->
                            clearError()
                            accountViewModel.logoutPrimary()
                            handleAccountState(accountViewModel, state.value!!)
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
                            accountViewModel.logoutPrimary()
                            handleAccountState(this, state.value!!)
                        }.show()
                    materialDialog.findViewById<TextView>(android.R.id.message)?.movementMethod =
                        LinkMovementMethod.getInstance()
                }
            })
        }
        calendarViewModel.viewMode.value = mainViewModel.getLastViewMode()

        nav_view_main_content.nav_view_version.text = getString(
            R.string.nav_view_version_name,
            BuildConfig.VERSION_NAME
        )

        initDrawerHeader()
        initDrawerListeners()
        initDrawerCalendarsList()

        calendarViewModel.initialised.observe(this@MainActivity, Observer { initialised ->
            if (initialised) {
                // Refresh drawer content now that we are logged in.
                initDrawerCalendarsListContent()
            }
        })

        // Set timezone visibility to gone by default
        nav_view_timezone.visibleOrGone(false)
    }

    private fun handleAccountState(accountViewModel: AccountViewModel, state: AccountViewModel.State) {
        if (forceUpdateViewModel.forceUpdate.value?.forceUpdate == true) {
            return
        }
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
                safeFindNavController(R.id.nav_host_fragment_container_view).popBackStack(R.id.rootFragment, false)
                accountViewModel.addAccount()
                ShowNotificationUseCase.cancelAllNotifications(this@MainActivity)
            }
            AccountViewModel.State.Ready -> {
                val eventDetailsIntent =
                    mainViewModel.consumeIntent(MainViewModel.INTENT_ACTION_SHOW_EVENT_DETAILS)

                val showDayIntent =
                    mainViewModel.consumeIntent(MainViewModel.INTENT_ACTION_SHOW_DAY)

                val newEventIntent =
                    mainViewModel.consumeIntent(MainViewModel.INTENT_ACTION_NEW_EVENT)

                if (eventDetailsIntent != null && eventDetailsIntent.data != null) {
                    logger.v("converting deeplink and navigating manually")

                    // convert "main" deeplink to "event details" deeplink and navigate manually
                    val eventId = eventDetailsIntent.data?.getQueryParameter("eventId")
                    val occurrenceNumber = eventDetailsIntent.data?.getQueryParameter("occurrenceNumber")
                    if (eventId != null && occurrenceNumber != null) {
                        val eventDetailsDeepLink = Navigation.Deeplink.toEventDetails(eventId, occurrenceNumber.toInt())
                        safeNavigateToDialogFragment(eventDetailsDeepLink)
                    } else {
                        logger.e("could not get eventId/occurrenceNumber from INTENT_ACTION_SHOW_EVENT_DETAILS")
                        safeNavigateToMonth()
                    }

                } else if (newEventIntent != null) {

                    safeNavigateToDialogFragment(Navigation.Deeplink.toEventCreate(LocalDate.now(), ICalUtilsImpl.generateEventStartTime(ZoneId.systemDefault())))

                } else if (showDayIntent != null && showDayIntent.data != null) {

                    val dayToShow = showDayIntent.data?.getQueryParameter("date")?.let { LocalDate.parse(it) }
                    safeNavigateToMonth(dayToShow)
                } else {
                    val openIcsIntent = mainViewModel.consumeIntent(INVITE_PROTON_INTENT_ACTION)
                    if (openIcsIntent != null && FeatureFlag.OPEN_INVITATION) {
                        handleIcsIntent(openIcsIntent)
                    } else if (openIcsIntent == null && IMPORT_ICS || APP_LINKS || IMPORT_ASSISTANT) {
                        val actionViewIntent = mainViewModel.consumeIntent(Intent.ACTION_VIEW)
                        val actionEditOrInsertIntent =
                            mainViewModel.consumeIntent(Intent.ACTION_INSERT) ?:
                            mainViewModel.consumeIntent(Intent.ACTION_EDIT)
                        if (actionViewIntent?.type == INVITE_ICS_MIME_TYPE && IMPORT_ICS) {
                            // Handle ics file
                            handleIcsIntent(actionViewIntent)
                        } else if (actionViewIntent != null && APP_LINKS) {
                            // Handle app link
                            val appLinkData: Uri? = actionViewIntent.data

                            // Handle open event details link
                            val eventId = appLinkData?.getQueryParameter(EVENT_ID)
                            val calendarId = appLinkData?.getQueryParameter(CALENDAR_ID)
                            val recurrenceId = appLinkData?.getQueryParameter(RECURRENCE_ID)
                            val action = appLinkData?.getQueryParameter(ACTION)

                            if (eventId != null && calendarId != null && recurrenceId != null && action == VIEW) {
                                handleEventDetailsAppLink(eventId, calendarId, recurrenceId)
                            } else {
                                this@MainActivity.displaySnackBar(getString(R.string.snack_app_link_invalid))
                                safeNavigateToMonth()
                            }
                        } else if (actionEditOrInsertIntent != null) {
                            // Handle extras
                            actionEditOrInsertIntent.extras?.let { intentExtras ->
                                lifecycleScope.launch {
                                    val hasActiveWritableCalendars = calendarViewModel.getActiveUserCalendars()?.any { it.allowEditEvents }
                                    if (hasActiveWritableCalendars != true) this@MainActivity.displaySnackBar(resources.getString(R.string.snack_create_event_no_active_calendar))
                                    val startMillis = run {
                                        val dtStart = intentExtras.getLong(CalendarContract.Events.DTSTART)
                                        if (dtStart == 0L) {
                                            intentExtras.getLong(CalendarContract.EXTRA_EVENT_BEGIN_TIME)
                                        } else dtStart
                                    }
                                    val endMillis = run {
                                        val dtEnd = intentExtras.getLong(CalendarContract.Events.DTEND)
                                        if (dtEnd == 0L) {
                                            intentExtras.getLong(CalendarContract.EXTRA_EVENT_END_TIME)
                                        } else dtEnd
                                    }
                                    val timeZoneId = fallbackTimeZone(
                                        intentExtras.getString(CalendarContract.Events.EVENT_TIMEZONE) ?: ZoneId.systemDefault().id,
                                        fallbackToDefault = true
                                    )
                                    val allDay = intentExtras.getInt(CalendarContract.Events.ALL_DAY).toBooleanOrFalse()
                                    val title = intentExtras.getString(CalendarContract.Events.TITLE) ?: "" // We open event form so no need to provide title placeholder
                                    val description = intentExtras.getString(CalendarContract.Events.DESCRIPTION) ?: ""
                                    val location = intentExtras.getString(CalendarContract.Events.EVENT_LOCATION) ?: ""
                                    val rRule = intentExtras.getString(CalendarContract.Events.RRULE) ?: ""
                                    safeNavigateToDialogFragment(
                                        Navigation.Deeplink.toEventCreatePrefill(
                                            startMillis,
                                            endMillis,
                                            Uri.encode(timeZoneId),
                                            allDay,
                                            Uri.encode(title),
                                            Uri.encode(description),
                                            Uri.encode(location),
                                            Uri.encode(rRule),
                                        )
                                    )
                                }
                            } ?: run {
                                this@MainActivity.displaySnackBar(
                                    getString(R.string.snack_ics_create_error),
                                    Snackbar.LENGTH_LONG
                                )
                                safeNavigateToMonth()
                            }
                        } else safeNavigateToMonth()
                    } else safeNavigateToMonth()
                }
            }
            AccountViewModel.State.Processing -> {
                displaySplashScreen(
                    display = true,
                    spinnerText = resources.getString(R.string.splash_after_login_init)
                )
            }
            AccountViewModel.State.Initial -> Unit
            AccountViewModel.State.StepNeeded -> Unit // handled by core
        }
    }

    private fun safeNavigateToMonth(dayToShow: LocalDate? = null) {
        if (calendarViewModel.initialised.value == true) {
            // If CalendarViewModel was initialised already, we try to pop backstack up to MonthFragment and just update the selected date
            if (safeFindNavController(R.id.nav_host_fragment_container_view).currentBackStackEntry?.destination?.id == R.id.nav_calendar ||
                safeFindNavController(R.id.nav_host_fragment_container_view).popBackStack(R.id.nav_calendar, false)) {
                if (dayToShow != null) calendarViewModel.handleDaySelected(dayToShow)
            } else {
                // If we failed to pop backstack up to MonthFragment then we go through navigateTo
                if (dayToShow != null) navigateTo(Navigation.Deeplink.toMonth(dayToShow))
                else navigateTo(Navigation.Deeplink.toMonth())
            }
        } else {
            // If CalendarViewModel was not initialised yet, we go through navigateTo in order to perform initForUser
            safeFindNavController(R.id.nav_host_fragment_container_view).popBackStack(R.id.nav_calendar, true)
            if (dayToShow != null) navigateTo(Navigation.Deeplink.toMonth(dayToShow))
            else navigateTo(Navigation.Deeplink.toMonth())
        }
    }

    private fun safeNavigateToDialogFragment(uri: Uri) {
        when (safeFindNavController(R.id.nav_host_fragment_container_view).currentBackStackEntry?.destination?.id) {
            R.id.nav_import_assistant,
            R.id.nav_settings,
            R.id.nav_general_settings,
            R.id.nav_calendar_form,
            R.id.nav_bug_report,
            R.id.nav_search,
            R.id.nav_event_details,
            R.id.nav_event_form,
            R.id.nav_event_form_attendees,
            R.id.nav_event_form_alarm,
            R.id.nav_event_form_recurrence -> {
                safeFindNavController(R.id.nav_host_fragment_container_view).popBackStack(R.id.nav_calendar, false)
            }
        }
        navigateTo(uri)
    }

    private fun handleIcsIntent(openIcsIntent: Intent) {
        safeNavigateToMonth() // Workaround for blank screen on starting app through ics intent if it wasn't in background
        val uri = openIcsIntent.data
        if (uri != null) {
            val senderEmail = openIcsIntent.getStringExtra(INVITE_PROTON_EXTRA_SENDER_EMAIL)
            val recipientEmail = openIcsIntent.getStringExtra(INVITE_PROTON_EXTRA_RECIPIENT_EMAIL)
            handleOpenIcsIntent(uri, senderEmail, recipientEmail)
        } else safeNavigateToMonth()
    }

    private fun handleEventDetailsAppLink(eventId: String, calendarId: String, recurrenceId: String) {
        lifecycleScope.launch {
            val userId = accountViewModel.getPrimaryUserId()
            if (userId == null) {
                safeNavigateToMonth()
                return@launch // TODO Display error ?
            }
            when (val handleEventLinkResult = eventViewModel.handleEventLink(userId, eventId, calendarId, recurrenceId)) {
                is EventViewModel.EventLinkResult.Success -> {
                    val eventDetailsDeepLink = Navigation.Deeplink.toEventDetails(eventId, handleEventLinkResult.occurrenceNumber)
                    safeNavigateToDialogFragment(eventDetailsDeepLink)
                }
                is EventViewModel.EventLinkResult.DecryptionFailed -> {
                    safeNavigateToMonth()
                    displayEventDecryptionErrorDialog(handleEventLinkResult.event.isRecurring()) { _, _ ->
                        lifecycleScope.launch { // TODO
                            val deleteResult = withContext(Dispatchers.Default) {
                                calendarViewModel.handleDeleteEvent(
                                    eventId,
                                    calendarId,
                                    EventEditDeleteOption.ALL_EVENTS
                                )
                            }
                            if (deleteResult is UseCase.Result.Success<*>) {
                                this@MainActivity.displaySnackBar(getString(R.string.snack_event_deleted))
                            } else {
                                var userErrorMessage: String? = null
                                if (deleteResult is UseCase.Result.Error) {
                                    logger.e("Error deleting event: ${deleteResult.message}")
                                    userErrorMessage = deleteResult.userErrorMessage
                                } else if (deleteResult is UseCase.Result.InvalidParams) {
                                    logger.e("InvalidParams deleting event: ${deleteResult.message}")
                                    userErrorMessage = deleteResult.userErrorMessage
                                }
                                this@MainActivity.displaySnackBar(
                                    if (userErrorMessage.isNullOrEmpty()) getString(R.string.snack_event_deleted_error)
                                    else userErrorMessage
                                )
                            }
                        }
                    }
                }
                is EventViewModel.EventLinkResult.EventDoesNotExist -> {
                    this@MainActivity.displaySnackBar(getString(R.string.snack_app_link_invalid))
                    safeNavigateToMonth()
                }
                is EventViewModel.EventLinkResult.OccurrenceDoesNotExist -> {
                    this@MainActivity.displaySnackBar(getString(R.string.error_occurrence_does_not_exist))
                    safeNavigateToMonth()
                }
                is EventViewModel.EventLinkResult.Error -> {
                    this@MainActivity.displaySnackBar(getString(R.string.snack_app_link_error))
                    safeNavigateToMonth()
                }
            }
        }
    }

    private fun handleOpenIcsIntent(uri: Uri, senderEmail: String?, recipientEmail: String?) {

        if (!IMPORT_ICS && (senderEmail == null || recipientEmail == null)) {
            this@MainActivity.displaySnackBar(getString(R.string.snack_ics_unsupported_publish_error), Snackbar.LENGTH_LONG)
            safeNavigateToMonth()
            return
        }

        // openInputStream blocks current thread and coroutine cannot be properly suspended so we call it before launch
        val bufferedReader = BufferedReader(InputStreamReader(this@MainActivity.contentResolver.openInputStream(uri)))
        val iCalString = bufferedReader.use { it.readText() }

        val isOpeningFromFileSystem = senderEmail == null || recipientEmail == null

        // Check if ics has more than MAX_VEVENT_COUNT VEvent
        val vEventRegex = Regex("BEGIN:VEVENT\\r?\\n")
        val vEventMatches = vEventRegex.findAll(iCalString).iterator()
        var eventCount = 0
        while (vEventMatches.hasNext()) {
            eventCount++
            if (eventCount > IcsParsingValidation.MAX_VEVENT_COUNT) {
                this@MainActivity.displaySnackBar(
                    getString(R.string.snack_ics_too_many_events_error),
                    Snackbar.LENGTH_LONG
                )
                return
            }
            vEventMatches.next()
        }

        // Check if ics has more than MAX_ICALENDAR_COUNT VCalendar
        val vCalendarRegex = Regex("BEGIN:VCALENDAR\\r?\\n")
        val vCalendarMatches = vCalendarRegex.findAll(iCalString).iterator()
        var calendarCount = 0
        while (vCalendarMatches.hasNext()) {
            calendarCount++
            if (calendarCount > IcsParsingValidation.MAX_VCALENDAR_COUNT) {
                this@MainActivity.displaySnackBar(
                    getString(R.string.snack_ics_too_many_events_error),
                    Snackbar.LENGTH_LONG
                )
                return
            }
            vCalendarMatches.next()
        }

        // If ics is an invitation and we are opening from file system, warn the user that attendees and organizers will be dropped
        val isInvitationRegex = Regex("METHOD:(REQUEST|REPLY|CANCEL|COUNTER|ADD|REFRESH|DECLINECOUNTER)\\r?\\n")
        if (isOpeningFromFileSystem && isInvitationRegex.containsMatchIn(iCalString)) {
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(R.string.import_event_disclaimer_dialog_title)
                .setMessage(R.string.import_event_disclaimer_dialog_description)
                .setPositiveButton(R.string.import_event_disclaimer_dialog_positive) { _, _ ->
                    handleIcsFile(iCalString, senderEmail, recipientEmail)
                }
                .setNegativeButton(R.string.dialog_button_cancel) { _, _ ->
                }
                .show()
        } else {
            handleIcsFile(iCalString, senderEmail, recipientEmail)
        }
    }

    private fun handleIcsFile(iCalString: String, senderEmail: String?, recipientEmail: String?) {
        lifecycleScope.launch {

            val snackBar = displaySnackBar(getString(R.string.snack_opening_ics), Snackbar.LENGTH_INDEFINITE)
            val handleIcsImportResult = mainViewModel.handleIcsFile(iCalString, senderEmail, recipientEmail)
            if (handleIcsImportResult is IcsSurgeryUtils.HandleIcsResult.Success) {
                snackBar.dismiss()
                when (handleIcsImportResult.action) {
                    // We use Toast because we do not have the EventDetails view required for SnackBar to be displayed
                    IcsSurgeryUtils.HandleIcsAction.CREATE_EVENT ->
                        Toast.makeText(this@MainActivity, getString(R.string.snack_event_created), Toast.LENGTH_LONG)
                            .show()
                    IcsSurgeryUtils.HandleIcsAction.UPDATE_EVENT -> {
                        when (handleIcsImportResult.newAttendeeStatus?.second) {
                            ParticipationStatus.ACCEPTED -> Toast.makeText(
                                this@MainActivity,
                                getString(
                                    R.string.snack_event_attendee_accepted_answer,
                                    handleIcsImportResult.newAttendeeStatus?.first
                                ),
                                Toast.LENGTH_LONG
                            ).show()
                            ParticipationStatus.TENTATIVE -> Toast.makeText(
                                this@MainActivity,
                                getString(
                                    R.string.snack_event_attendee_tentative_answer,
                                    handleIcsImportResult.newAttendeeStatus?.first
                                ),
                                Toast.LENGTH_LONG
                            ).show()
                            ParticipationStatus.DECLINED -> Toast.makeText(
                                this@MainActivity,
                                getString(
                                    R.string.snack_event_attendee_declined_answer,
                                    handleIcsImportResult.newAttendeeStatus?.first
                                ),
                                Toast.LENGTH_LONG
                            ).show()
                            else -> Toast.makeText(
                                this@MainActivity,
                                getString(R.string.snack_event_updated),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    IcsSurgeryUtils.HandleIcsAction.OPEN_EVENT -> Unit // TODO not yet implemented
                }

                val eventId = handleIcsImportResult.eventId
                val eventDetailsDeepLink =
                    Navigation.Deeplink.toEventDetails(eventId, if (handleIcsImportResult.isRecurring == true) 1 else 0)
                safeNavigateToDialogFragment(eventDetailsDeepLink)
            } else {
                snackBar.dismiss()
                var navigatedToDetails = false
                when (handleIcsImportResult) {
                    is Error.DefaultError -> this@MainActivity.displaySnackBar(
                        getString(R.string.snack_ics_default_error),
                        Snackbar.LENGTH_LONG
                    )
                    is Error.EventNotFound -> this@MainActivity.displaySnackBar(
                        getString(R.string.snack_ics_event_not_found_error),
                        Snackbar.LENGTH_LONG
                    )
                    is Error.NetworkError -> this@MainActivity.displaySnackBar(
                        getString(R.string.snack_network_error),
                        Snackbar.LENGTH_LONG
                    )
                    is Error.EditCreateEventError -> this@MainActivity.displaySnackBar(
                        getString(R.string.snack_ics_create_error),
                        Snackbar.LENGTH_LONG
                    )
                    is Error.ParsingFailed -> this@MainActivity.displaySnackBar(
                        getString(R.string.snack_ics_parsing_error),
                        Snackbar.LENGTH_LONG
                    )
                    is Error.Unsupported.Method -> this@MainActivity.displaySnackBar(
                        getString(R.string.snack_ics_unsupported_method_error),
                        Snackbar.LENGTH_LONG
                    )
                    is Error.Unsupported.Add -> this@MainActivity.displaySnackBar(
                        getString(R.string.snack_ics_unsupported_add_error),
                        Snackbar.LENGTH_LONG
                    )
                    is Error.Unsupported.Counter -> this@MainActivity.displaySnackBar(
                        getString(R.string.snack_ics_unsupported_counter_error),
                        Snackbar.LENGTH_LONG
                    )
                    is Error.Unsupported.Refresh -> this@MainActivity.displaySnackBar(
                        getString(R.string.snack_ics_unsupported_refresh_error),
                        Snackbar.LENGTH_LONG
                    )
                    is Error.Unsupported.Publish -> this@MainActivity.displaySnackBar(
                        getString(R.string.snack_ics_unsupported_publish_error),
                        Snackbar.LENGTH_LONG
                    )
                    is Error.Unsupported.SingleEditReply -> this@MainActivity.displaySnackBar(
                        getString(R.string.snack_ics_unsupported_single_edit_reply_error),
                        Snackbar.LENGTH_LONG
                    )
                    is Error.PartyCrasher -> this@MainActivity.displaySnackBar(
                        getString(R.string.snack_ics_party_crasher_error),
                        Snackbar.LENGTH_LONG
                    )
                    is Error.MissingUid -> this@MainActivity.displaySnackBar(
                        getString(R.string.snack_ics_missing_uid_error),
                        Snackbar.LENGTH_LONG
                    )
                    is Error.NoDefaultCalendarFound -> this@MainActivity.displaySnackBar(
                        getString(R.string.snack_ics_no_active_calendar_error),
                        Snackbar.LENGTH_LONG
                    )
                    is Error.NoDefaultPersonalCalendarFound -> this@MainActivity.displaySnackBar(
                        getString(R.string.snack_ics_no_active_personal_calendar_error),
                        Snackbar.LENGTH_LONG
                    )
                    is Error.DurationNotSupported -> this@MainActivity.displaySnackBar(
                        getString(R.string.snack_ics_unsupported_duration_error),
                        Snackbar.LENGTH_LONG
                    )
                    is Error.TooManyEvents -> this@MainActivity.displaySnackBar(
                        getString(R.string.snack_ics_too_many_events_error),
                        Snackbar.LENGTH_LONG
                    )
                    is Error.NoEvents -> this@MainActivity.displaySnackBar(
                        getString(R.string.snack_ics_no_events_error),
                        Snackbar.LENGTH_LONG
                    )
                    is Error.EventDeleted -> this@MainActivity.displaySnackBar(
                        getString(R.string.snack_ics_event_deleted_error),
                        Snackbar.LENGTH_LONG
                    )
                    is Error.Invalid -> this@MainActivity.displaySnackBar(
                        getString(R.string.snack_ics_invalid_error),
                        Snackbar.LENGTH_LONG
                    )
                    is Error.DisabledCalendar -> navigatedToDetails = displayErrorAndOpenDetails(
                        handleIcsImportResult.eventId,
                        getString(R.string.snack_ics_disabled_calendar_error)
                    )
                    is Error.ReplyPartyCrasher -> navigatedToDetails = displayErrorAndOpenDetails(
                        handleIcsImportResult.eventId,
                        getString(R.string.snack_ics_reply_party_crasher_error)
                    )
                    is Error.Method -> navigatedToDetails = displayErrorAndOpenDetails(
                        handleIcsImportResult.eventId,
                        getString(R.string.snack_ics_invalid_error)
                    )
                    is Error.DecryptionFailed -> {
                        if (handleIcsImportResult.eventId != null && handleIcsImportResult.calendarId != null) {
                            deleteFailedToDecryptEvent(
                                handleIcsImportResult.eventId,
                                handleIcsImportResult.calendarId,
                                handleIcsImportResult.isRecurring
                            )
                        } else this@MainActivity.displaySnackBar(
                            getString(R.string.event_decryption_error_dialog_title),
                            Snackbar.LENGTH_LONG
                        )
                    }
                    else -> this@MainActivity.displaySnackBar(
                        getString(R.string.snack_ics_default_error),
                        Snackbar.LENGTH_LONG
                    )
                }

                if (!navigatedToDetails) safeNavigateToMonth()
            }
        }
    }

    private fun deleteFailedToDecryptEvent(eventId: String, calendarId: String, isRecurring: Boolean?) {
        displayEventDecryptionErrorDialog(isRecurring == true) { _, _ ->
            lifecycleScope.launch { // TODO
                val deleteResult = withContext(Dispatchers.Default) {
                    calendarViewModel.handleDeleteEvent(
                        eventId,
                        calendarId,
                        EventEditDeleteOption.ALL_EVENTS
                    )
                }
                if (deleteResult is UseCase.Result.Success<*>) {
                    this@MainActivity.displaySnackBar(getString(R.string.snack_event_deleted))
                } else {
                    var userErrorMessage: String? = null
                    if (deleteResult is UseCase.Result.Error) {
                        logger.e("Error deleting event: ${deleteResult.message}")
                        userErrorMessage = deleteResult.userErrorMessage
                    } else if (deleteResult is UseCase.Result.InvalidParams) {
                        logger.e("InvalidParams deleting event: ${deleteResult.message}")
                        userErrorMessage = deleteResult.userErrorMessage
                    }
                    this@MainActivity.displaySnackBar(
                        if (userErrorMessage.isNullOrEmpty()) getString(R.string.snack_event_deleted_error)
                        else userErrorMessage
                    )
                }
            }
        }
    }

    private fun displayErrorAndOpenDetails(eventId: String?, errorMessage: String): Boolean {
        return if (eventId != null) {
            Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_LONG).show()
            val eventDetailsDeepLink = Navigation.Deeplink.toEventDetails(eventId)
            safeNavigateToDialogFragment(eventDetailsDeepLink)
            true
        } else {
            this@MainActivity.displaySnackBar(errorMessage, Snackbar.LENGTH_LONG)
            false
        }
    }

    fun displaySplashScreen(display: Boolean, spinnerText: String? = null) {
        // TODO Status bar and navigation bar colors are set to brand_norm on dark / light mode change because of activity recreation

        calendarViewModel.fetchingEvents.postValue(spinnerText)

        drawer_layout.setDrawerLockMode(if (display) LOCK_MODE_LOCKED_CLOSED else LOCK_MODE_UNLOCKED)

        val backgroundDrawable = if (display) R.drawable.splash_screen else R.color.background_norm
        val statusBarBackgroundColor = if (display) R.color.splash_screen_color else R.color.background_norm

        window.setBackgroundDrawableResource(backgroundDrawable)
        window.statusBarColor =  resources.getColor(statusBarBackgroundColor, null)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            val navigationBarBackgroundColor = if (display) R.color.splash_screen_color else R.color.background_norm
            window.navigationBarColor = resources.getColor(navigationBarBackgroundColor, null)
        } else {
            window.navigationBarColor =
                if (display) resources.getColor(R.color.splash_screen_color, null)
                else getColorFromAttr(R.attr.proton_background_norm)
        }
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

        nav_view_main_content.nav_view_more_feedback_layout.visibleOrGone(FEEDBACK)
        nav_view_main_content.nav_view_more_feedback_press.setOnSingleClickListener {
            showFeedbackDialog()
            drawer_layout.close()
        }

        nav_view_main_content.nav_view_more_subscription_layout.visibleOrGone(SUBSCRIPTION)
        nav_view_main_content.nav_view_more_subscription_press.setOnSingleClickListener {
            plansViewModel.onCurrentPlanClicked(this)
            drawer_layout.close()
        }

        // TODO Remove feature flag
        nav_view_main_content.nav_view_more_settings_layout.visibleOrGone(FeatureFlag.SETTINGS_DRAWER)
        nav_view_main_content.nav_view_more_settings_press.setOnSingleClickListener {
            navController.navigate(R.id.action_nav_calendar_to_nav_settings)
            drawer_layout.close()
        }
        accountViewModel.hasPrimary.observe(this@MainActivity, Observer { hasPrimary ->
            nav_view_main_content.nav_view_more_logout_layout.isVisible = hasPrimary
            nav_view_main_content.nav_view_more_login_layout.isGone = hasPrimary
        })
        nav_view_main_content.nav_view_more_logout_press.setOnSingleClickListener {
            accountViewModel.logoutPrimary()
            displaySplashScreen(true, spinnerText = "")
            safeFindNavController(R.id.nav_host_fragment_container_view).popBackStack(R.id.rootFragment, false)
            drawer_layout.close()
        }
        nav_view_main_content.nav_view_more_login_press.setOnSingleClickListener {
            accountViewModel.addAccount()
            drawer_layout.close()
        }

        calendarViewModel.viewMode.observe(this@MainActivity, Observer { viewMode ->
            returnToView =
                if (currentViewMode == ViewMode.MONTH && viewMode == ViewMode.DAY) ViewMode.MONTH
                else if (currentViewMode == ViewMode.WEEK && viewMode == ViewMode.DAY) ViewMode.WEEK
                else if (currentViewMode == ViewMode.THREE_DAY && viewMode == ViewMode.DAY) ViewMode.THREE_DAY
                else null
            currentViewMode = viewMode
        })

        nav_view_switcher_day_press.setOnSingleClickListener {
            calendarViewModel.viewMode.postValue(ViewMode.DAY)
            mainViewModel.setLastViewMode(ViewMode.DAY)
            drawer_layout.close()
        }

        nav_view_main_content.nav_view_switcher_three_day_layout.visibleOrGone(FeatureFlag.THREE_DAYS_VIEW)
        nav_view_switcher_three_day_press.setOnSingleClickListener {
            calendarViewModel.viewMode.postValue(ViewMode.THREE_DAY)
            mainViewModel.setLastViewMode(ViewMode.THREE_DAY)
            drawer_layout.close()
        }

        nav_view_main_content.nav_view_switcher_week_layout.visibleOrGone(FeatureFlag.WEEK_VIEW)
        nav_view_switcher_week_press.setOnSingleClickListener {
            calendarViewModel.viewMode.postValue(ViewMode.WEEK)
            mainViewModel.setLastViewMode(ViewMode.WEEK)
            drawer_layout.close()
        }

        nav_view_switcher_agenda_press.setOnSingleClickListener {
            calendarViewModel.viewMode.postValue(ViewMode.AGENDA)
            mainViewModel.setLastViewMode(ViewMode.AGENDA)
            drawer_layout.close()
        }

        nav_view_switcher_month_layout.visibleOrGone(MONTH_VIEW)
        nav_view_switcher_month_press.setOnSingleClickListener {
            calendarViewModel.viewMode.postValue(ViewMode.MONTH)
            mainViewModel.setLastViewMode(ViewMode.MONTH)
            drawer_layout.close()
        }

        nav_view_calendars_list_add_layout_press.setOnSingleClickListener {
            lifecycleScope.launch {
                if (calendarViewModel.displayImport()) showCalendarsCreateOrImportDialog()
                else onClickCreateCalendar()
            }
        }

        nav_view_calendars_create.setOnSingleClickListener {
            lifecycleScope.launch {
                if (calendarViewModel.displayImport()) showCalendarsCreateOrImportDialog()
                else onClickCreateCalendar()
            }
            drawer_layout.close()
        }

        calendarViewModel.viewMode.observe(this@MainActivity, Observer { viewMode ->
            viewMode ?: return@Observer

            // Set selected background
            nav_view_main_content.nav_view_switcher_agenda_layout.background =
                if (viewMode == ViewMode.AGENDA) ContextCompat.getDrawable(this, R.color.sidebar_interaction_pressed)
                else null
            nav_view_main_content.nav_view_switcher_day_layout.background =
                if (viewMode == ViewMode.DAY) ContextCompat.getDrawable(this, R.color.sidebar_interaction_pressed)
                else null
            nav_view_main_content.nav_view_switcher_three_day_layout.background =
                if (viewMode == ViewMode.THREE_DAY) ContextCompat.getDrawable(this, R.color.sidebar_interaction_pressed)
                else null
            nav_view_main_content.nav_view_switcher_week_layout.background =
                if (viewMode == ViewMode.WEEK) ContextCompat.getDrawable(this, R.color.sidebar_interaction_pressed)
                else null
            nav_view_main_content.nav_view_switcher_month_layout.background =
                if (viewMode == ViewMode.MONTH) ContextCompat.getDrawable(this, R.color.sidebar_interaction_pressed)
                else null

            // Set icon tint
            nav_view_main_content.nav_view_switcher_agenda_icon.imageTintList =
                if (viewMode == ViewMode.AGENDA) ColorStateList.valueOf(ContextCompat.getColor(this, R.color.sidebar_icon_norm))
                else ColorStateList.valueOf(ContextCompat.getColor(this, R.color.sidebar_icon_weak))
            nav_view_main_content.nav_view_switcher_day_icon.imageTintList =
                if (viewMode == ViewMode.DAY) ColorStateList.valueOf(ContextCompat.getColor(this, R.color.sidebar_icon_norm))
                else ColorStateList.valueOf(ContextCompat.getColor(this, R.color.sidebar_icon_weak))
            nav_view_main_content.nav_view_switcher_three_day_icon.imageTintList =
                if (viewMode == ViewMode.THREE_DAY) ColorStateList.valueOf(ContextCompat.getColor(this, R.color.sidebar_icon_norm))
                else ColorStateList.valueOf(ContextCompat.getColor(this, R.color.sidebar_icon_weak))
            nav_view_main_content.nav_view_switcher_week_icon.imageTintList =
                if (viewMode == ViewMode.WEEK) ColorStateList.valueOf(ContextCompat.getColor(this, R.color.sidebar_icon_norm))
                else ColorStateList.valueOf(ContextCompat.getColor(this, R.color.sidebar_icon_weak))
            nav_view_main_content.nav_view_switcher_month_icon.imageTintList =
                if (viewMode == ViewMode.MONTH) ColorStateList.valueOf(ContextCompat.getColor(this, R.color.sidebar_icon_norm))
                else ColorStateList.valueOf(ContextCompat.getColor(this, R.color.sidebar_icon_weak))
        })
    }

    private fun onClickCreateCalendar() {
        lifecycleScope.launch {
            // Check if calendar limit was reached
            when (calendarViewModel.isCalendarLimitReached()) {
                CalendarViewModel.CalendarLimit.ERROR -> {
                    this@MainActivity.displaySnackBar(this@MainActivity.getString(R.string.snack_create_calendar_error))
                }
                CalendarViewModel.CalendarLimit.NOT_REACHED -> {
                    // If limit has not been reached, open create calendar form
                    navController.navigate(R.id.action_nav_calendar_to_nav_calendar_form)
                    drawer_layout.close()
                }
                CalendarViewModel.CalendarLimit.FREE_REACHED -> {
                    // Display limit reached for free user dialog
                    this@MainActivity.displayFreeUserCalendarLimitReached() { _, _ ->
                        // Open calendar settings view
                        navController.navigate(R.id.action_nav_calendar_to_nav_settings)
                        drawer_layout.close()
                    }
                }
                CalendarViewModel.CalendarLimit.PAID_REACHED -> {
                    // Display limit reached for paid user dialog
                    this@MainActivity.displayPaidUserCalendarLimitReached() { _, _ ->
                        // Open calendar settings view
                        navController.navigate(R.id.action_nav_calendar_to_nav_settings)
                        drawer_layout.close()
                    }
                }
            }
        }
    }

    private fun showCalendarsCreateOrImportDialog() {
        val bottomSheetDialog = BottomSheetDialog(this)

        // Workaround to make sure we have the correct navigation bar color.
        // TODO update once we change splash screen and how we handle navigation bar colors
        val window = bottomSheetDialog.window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val navigationBarBackgroundColor = R.color.background_norm
            window?.navigationBarColor = resources.getColor(navigationBarBackgroundColor, null)
        } else {
            window?.navigationBarColor = this.getColorFromAttr(
                R.attr.proton_background_norm
            )
        }

        bottomSheetDialog.setContentView(R.layout.dialog_calendars_create_import)

        val createCalendarPress = bottomSheetDialog.findViewById<View>(R.id.dialog_calendars_create_press)
        val importFromGooglePress = bottomSheetDialog.findViewById<View>(R.id.dialog_calendars_import_press)

        createCalendarPress?.setOnSingleClickListener {
            onClickCreateCalendar()
            bottomSheetDialog.dismiss()
        }

        importFromGooglePress?.setOnSingleClickListener {
            if (!mainViewModel.isConnectedToNetwork) {
                bottomSheetDialog.dismiss()
                this@MainActivity.displaySnackBar(this.getString(R.string.snack_network_error), Snackbar.LENGTH_LONG)
                return@setOnSingleClickListener
            }

            showImportGoogleAuthDialog()
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    fun showImportGoogleAuthDialog() {
        if (!mainViewModel.isConnectedToNetwork) {
            val snackMessage = getString(R.string.snack_network_error)
            if (safeFindNavController(R.id.nav_host_fragment_container_view).currentBackStackEntry?.destination?.id == R.id.nav_import_assistant_guide) {
                importAssistantViewModel.importGuideSnackState.value =
                    ImportAssistantViewModel.ImportSnackState.DisplaySnack(snackMessage)
            } else {
                displaySnackBar(snackMessage)
            }
            return
        }

        val materialDialogBuilder = MaterialAlertDialogBuilder(this)
            .setCancelable(true)
            .setPositiveButton(R.string.dialog_button_continue) { dialog, _ ->
                showPreparingImportDialog()
            }
            .setNegativeButton(R.string.dialog_button_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .setOnDismissListener {
            }

        val view = LayoutInflater.from(this)
            .inflate(R.layout.dialog_google_auth, null, false)

        materialDialogBuilder.setView(view)
        materialDialogBuilder.show()
    }

    private fun showPreparingImportDialog() {
        // Display preparing import loader dialog
        val materialDialogBuilder = MaterialAlertDialogBuilder(this)
            .setCancelable(false)
            .setOnDismissListener {
            }

        val view = LayoutInflater.from(this)
            .inflate(R.layout.dialog_preparing_import, null, false)

        materialDialogBuilder.setView(view)
        val dialog = materialDialogBuilder.show()

        lifecycleScope.launch {
            // Start the google sign in process
            displayGoogleSignIn()
            dialog.dismiss()
        }
    }

    private suspend fun displayGoogleSignIn(): Boolean {
        // Get Google Sign In Options with Calendar scope
        importAssistantViewModel.getGoogleSignInOptions()?.let { googleSignInOptions ->

            // Get Google Sign In Client and store the value so we can disconnect user
            googleSignInClient = GoogleSignIn.getClient(this@MainActivity, googleSignInOptions)
            googleSignInClient?.let { googleSignInClient ->
                // Start Google Sign In
                startActivityForResult(googleSignInClient.signInIntent, RC_CREATE_IMPORT_SIGN_IN)
            } ?: run {
                val snackMessage = getString(R.string.import_assistant_prepare_import_error)
                if (safeFindNavController(R.id.nav_host_fragment_container_view).currentBackStackEntry?.destination?.id == R.id.nav_import_assistant_guide) {
                    importAssistantViewModel.importGuideSnackState.value =
                        ImportAssistantViewModel.ImportSnackState.DisplaySnack(snackMessage)
                } else {
                    displaySnackBar(snackMessage)
                }
                return false
            }
        } ?: run {
            val snackMessage = getString(R.string.import_assistant_prepare_import_error)
            if (safeFindNavController(R.id.nav_host_fragment_container_view).currentBackStackEntry?.destination?.id == R.id.nav_import_assistant_guide) {
                importAssistantViewModel.importGuideSnackState.value =
                    ImportAssistantViewModel.ImportSnackState.DisplaySnack(snackMessage)
            } else {
                displaySnackBar(snackMessage)
            }
            return false
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Result returned from launching the Intent from GoogleSignInClient.getSignInIntent(...);
        if (requestCode == RC_CREATE_IMPORT_SIGN_IN) {
            // The Task returned from this call is always completed, no need to attach
            // a listener.
            val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(data)

            try {
                val account: GoogleSignInAccount = task.getResult(ApiException::class.java)

                // Signed in successfully, show authenticated UI.
                googleSignInClient?.signOut()
                lifecycleScope.launch {
                    account.email?.let { accountEmail ->
                        if (importAssistantViewModel.isImportInProgress(accountEmail)) {
                            val snackMessage = getString(R.string.import_assistant_in_progress_error_snack)
                            if (safeFindNavController(R.id.nav_host_fragment_container_view).currentBackStackEntry?.destination?.id == R.id.nav_import_assistant_guide) {
                                importAssistantViewModel.importGuideSnackState.value =
                                    ImportAssistantViewModel.ImportSnackState.DisplaySnack(snackMessage)
                            } else {
                                displaySnackBar(snackMessage)
                            }
                            return@launch
                        }
                    }

                    account.serverAuthCode?.let { authCode ->
                        // Create import
                        val importAssistantDeepLink = Navigation.Deeplink.toImportAssistant(authCode)
                        safeNavigateToDialogFragment(importAssistantDeepLink)
                    } ?: run {
                        displaySnackBar(getString(R.string.import_assistant_prepare_import_error))
                    }
                }
            } catch (e: ApiException) {
                // The ApiException status code indicates the detailed failure reason.
                // Please refer to the GoogleSignInStatusCodes class reference for more information.
                logger.i("Create Import signInResult:failed code= ${e.statusCode}") // TODO This might flood Sentry ?
                val snackMessage = when (e.statusCode) {
                    GoogleSignInCodes.SIGN_IN_CANCELED -> getString(R.string.import_assistant_authentication_canceled)
                    GoogleSignInCodes.SIGN_IN_CURRENTLY_IN_PROGRESS -> getString(R.string.import_assistant_sign_in_in_progress)
                    CommonStatusCodes.NETWORK_ERROR -> getString(R.string.snack_network_error)
                    else -> getString(R.string.import_assistant_prepare_import_error)
                }
                if (safeFindNavController(R.id.nav_host_fragment_container_view).currentBackStackEntry?.destination?.id == R.id.nav_import_assistant_guide) {
                    importAssistantViewModel.importGuideSnackState.value =
                        ImportAssistantViewModel.ImportSnackState.DisplaySnack(snackMessage)
                } else {
                    displaySnackBar(snackMessage)
                }
                googleSignInClient?.signOut()
            }
        }
    }

    private fun showFeedbackDialog() {

        // toggles rating button styles and enables "submit" button
        fun onRatingClicked(bottomSheetDialog: BottomSheetDialog, button: View) {
            with (bottomSheetDialog) {
                listOf<ImageView?>(
                    findViewById(R.id.button_feedback_rating_1),
                    findViewById(R.id.button_feedback_rating_2),
                    findViewById(R.id.button_feedback_rating_3),
                    findViewById(R.id.button_feedback_rating_4),
                    findViewById(R.id.button_feedback_rating_5)
                ).forEach {
                    if (it?.id == button.id) {
                        it.setBackgroundResource(R.drawable.shape_feedback_rounded_bg_enabled)
                    } else {
                        it?.setBackgroundResource(R.drawable.shape_feedback_rounded_bg_disabled)
                    }
                }

                findViewById<ProtonProgressButton>(R.id.button_submit_feedback)?.isEnabled = true

                findViewById<ProtonInput>(R.id.input_feeedback_form)?.visibleOrGone(true)
            }
        }

        val bottomSheetDialog = BottomSheetDialog(this)

        // Workaround to make sure we have the correct navigation bar color.
        // TODO update once we change splash screen and how we handle navigation bar colors
        val window = bottomSheetDialog.window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val navigationBarBackgroundColor = R.color.background_norm
            window?.navigationBarColor = resources.getColor(navigationBarBackgroundColor, null)
        } else {
            window?.navigationBarColor = getColorFromAttr(
                R.attr.proton_background_norm
            )
        }

        var rating: Int? = null

        with (bottomSheetDialog) {

            setContentView(R.layout.dialog_feedback)

            findViewById<ImageButton>(R.id.dialog_calendar_feedback_close_icon)?.setOnSingleClickListener {
                bottomSheetDialog.dismiss()
            }

            findViewById<ImageView>(R.id.button_feedback_rating_1)?.setOnSingleClickListener {
                rating = 1
                onRatingClicked(this, it)
            }
            findViewById<ImageView>(R.id.button_feedback_rating_2)?.setOnSingleClickListener {
                rating = 2
                onRatingClicked(this, it)
            }
            findViewById<ImageView>(R.id.button_feedback_rating_3)?.setOnSingleClickListener {
                rating = 3
                onRatingClicked(this, it)
            }
            findViewById<ImageView>(R.id.button_feedback_rating_4)?.setOnSingleClickListener {
                rating = 4
                onRatingClicked(this, it)
            }
            findViewById<ImageView>(R.id.button_feedback_rating_5)?.setOnSingleClickListener {
                rating = 5
                onRatingClicked(this, it)
            }

            findViewById<ProtonProgressButton>(R.id.button_submit_feedback)?.let { progressButton ->

                progressButton.setIdle()

                progressButton.setOnSingleClickListener {

                    if (!mainViewModel.isConnectedToNetwork) {
                        displaySnackBar(progressButton.context.getString(R.string.snack_network_error))
                    } else {
                        lifecycleScope.launch {
                            (it as ProtonProgressButton).setLoading()

                            val userId = accountViewModel.getPrimaryUserId()
                            val feedback = findViewById<ProtonInput>(R.id.input_feeedback_form)?.text?.toString() ?: ""
                            if (userId != null && mainViewModel.handleFeedback(userId, logger, rating!!, feedback) is UseCase.Result.Success<*>) {
                                it.setIdle()
                                displaySnackBar(progressButton.context.getString(R.string.dialog_feedback_submit_thank_you))
                                bottomSheetDialog.dismiss()
                            } else {
                                it.setIdle()
                                displaySnackBar(progressButton.context.getString(R.string.snack_network_error))
                            }
                        }
                    }
                }
            }

        }

        bottomSheetDialog.show()
    }

    private fun initDrawerHeader() {
        nav_view_user_layout.setViewModel(accountSwitcherViewModel)
    }

    private fun initDrawerCalendarsList() {
        val userCalendarListView = nav_view_main_content.nav_view_calendars_list
        val userCalendarsLayoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        userCalendarListView.layoutManager = userCalendarsLayoutManager
        userCalendarListAdapter = CalendarListAdapter { calendar ->
            //On Calendar click event
            lifecycleScope.launch {
                calendarViewModel.updateCalendarVisibility(calendar.id, calendar.display)
                updateCalendarsDelayed()
            }
        }
        (userCalendarListView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        userCalendarListView.adapter = userCalendarListAdapter

        val subscribedCalendarListView = nav_view_main_content.nav_view_subscribed_calendars_list
        val subscribedCalendarLayoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        subscribedCalendarListView.layoutManager = subscribedCalendarLayoutManager
        subscribedCalendarListAdapter = CalendarListAdapter { calendar ->
            //On Calendar click event
            lifecycleScope.launch {
                calendarViewModel.updateCalendarVisibility(calendar.id, calendar.display)
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
        lifecycleScope.launch {
            calendarViewModel.selectCalendars()
        }

        calendarViewModel.userCalendars.observe(this@MainActivity, Observer { userCalendars ->
            userCalendars ?: return@Observer
            setUserCalendarsList(userCalendars)

            if (userCalendars.firstOrNull { it.hasUpdatePassphrase } != null && !calendarViewModel.updatingCalendarPassphrase) {
                // TODO Uncomment once calendar key reactivation has been fixed
                // handleUpdatePassphrase()

                lifecycleScope.launch {
                    // TODO Temporary workaround: Force logout user if we get key reactivation
                    accountViewModel.logoutPrimary()
                    calendarViewModel.shutdown()
                }
            }

            lifecycleScope.launch {
                userCalendars.forEach {
                    // TODO Temporary fix for MIGRATION_46_47 that caused some users Member.description field to have the value "0" locally
                    if (it.description == "0") calendarViewModel.refreshMember(calendarId = it.id)
                }
            }
        })

        calendarViewModel.defaultCalendarId.observe(this@MainActivity, Observer { defaultCalendarId ->

            lifecycleScope.launch {
                calendarViewModel.getUserCalendars()?.let { userCalendars ->
                    setUserCalendarsList(userCalendars, defaultCalendarId)
                }
            }
        })

        calendarViewModel.inactiveUserCalendars.observe(this@MainActivity, Observer { inactiveCalendars ->
            inactiveCalendars ?: return@Observer
        })

        subscribedCalendarsMediator.addSource(calendarViewModel.subscribedCalendars) { value ->
            subscribedCalendars = value

            lifecycleScope.launch {
                value.forEach {
                    // TODO Temporary fix for MIGRATION_46_47 that caused some users Member.description field to have the value "0" locally
                    if (it.description == "0") calendarViewModel.refreshMember(calendarId = it.id)
                }
            }

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
        subscribedCalendarsMediator.observe(this@MainActivity, Observer {
            it?.let {
                val subscribedCalendars = it.first
                val calendarSubscriptions = it.second
                val dataSetChanged = subscribedCalendarListAdapter.setCalendarSubscriptions(calendarSubscriptions)
                subscribedCalendarListAdapter.submitList(subscribedCalendars)
                if (dataSetChanged) subscribedCalendarListAdapter.notifyDataSetChanged()
                nav_view_main_content.nav_view_subscribed_calendars.visibleOrGone(subscribedCalendars.isNotEmpty())
            }
        })
    }

    private fun setUserCalendarsList(userCalendars: List<Calendar>, defaultCalendarId: String? = null) {
        // We only keep active and disabled calendars for the navigation drawer calendar list
        val filteredUserCalendars = userCalendars.filter { it.isActive || it.isDisabled }
        nav_view_calendars_list_add_layout.visibleOrGone(filteredUserCalendars.isEmpty())
        nav_view_calendars_create.visibleOrGone(filteredUserCalendars.isNotEmpty())
        lifecycleScope.launch {
            var tmpDefaultCalendarId = defaultCalendarId ?: calendarViewModel.getDefaultCalendarId()
            val defaultCalendar = userCalendars.firstOrNull { it.id == tmpDefaultCalendarId }
            if (defaultCalendar?.isActive == false) tmpDefaultCalendarId = userCalendars.firstOrNull { it.isActive }?.id
            userCalendarListAdapter.submitList(
                filteredUserCalendars.sortedBy {
                    it.isDisabled // Disabled will appear last
                }.sortedByDescending {
                    it.id == tmpDefaultCalendarId // Default will appear first
                }
            )
        }
    }

    private lateinit var inactiveCalendarsJob: Job
    private fun handleUpdatePassphrase() {
        if (this::inactiveCalendarsJob.isInitialized && inactiveCalendarsJob.isActive) {
            inactiveCalendarsJob.cancel()
        }
        inactiveCalendarsJob = lifecycleScope.launch {
            delay(UPDATE_PASSPHRASE_CALENDARS_DELAY.toMillis())
            val calendarsToUpdate = calendarViewModel.getInactiveUserCalendars()?.filter { it.hasUpdatePassphrase } ?: return@launch
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

    override fun onSupportNavigateUp(): Boolean {
        val navController = safeFindNavController(R.id.nav_host_fragment_container_view)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onBackPressed() {

        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
        } else if (returnToView == ViewMode.MONTH && MONTH_VIEW) {
            // Navigate back to month view
            calendarViewModel.monthViewDate?.let {
                calendarViewModel.handleDaySelected(it)
            }
            calendarViewModel.viewMode.postValue(ViewMode.MONTH)
            mainViewModel.setLastViewMode(ViewMode.MONTH)
        } else if (returnToView == ViewMode.WEEK && WEEK_VIEW) {
            // Navigate back to week view
            calendarViewModel.viewMode.postValue(ViewMode.WEEK)
            mainViewModel.setLastViewMode(ViewMode.WEEK)
        } else if (returnToView == ViewMode.THREE_DAY && THREE_DAYS_VIEW) {
            // Navigate back to 3 days view
            calendarViewModel.viewMode.postValue(ViewMode.THREE_DAY)
            mainViewModel.setLastViewMode(ViewMode.THREE_DAY)
        } else if (navController.currentDestination?.id == R.id.nav_calendar) {
            moveTaskToBack(true)
        } else {
            super.onBackPressed()
        }
    }

    /**
     * Checks if we have granted permissions to show notifications and displays appropriate dialogs if not.
     */
    private fun checkNotificationsPermissions(context: Context) {

        fun shouldShowNotificationsPermissionsDialog(context: Context) = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
            SharedPreferencesKeys.SHOW_NOTIFICATIONS_PERMISSIONS_DIALOG, true)

        fun displayRationale(context: Context) {

            val view = LayoutInflater.from(context).inflate(R.layout.dialog_checkbox, null, false)

            view.dialog_checkbox_header.text = getString(R.string.notifications_permission_dialog_message)
            view.dialog_checkbox_press.setOnClickListener {
                view.dialog_checkbox.performClick()
            }

            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.notifications_permission_dialog_title)
                .setView(view)
                .setPositiveButton(R.string.notifications_permission_dialog_open_settings) { _, _ ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    intent.data = Uri.fromParts("package", context.packageName, null)
                    startActivity(intent)
                }
                .setNegativeButton(R.string.notifications_permission_dialog_cancel) { _, _ -> }
                .setOnCancelListener { }
                .setOnDismissListener {
                    if (view.dialog_checkbox.isChecked) {
                        with (PreferenceManager.getDefaultSharedPreferences(context).edit()) {
                            putBoolean(SharedPreferencesKeys.SHOW_NOTIFICATIONS_PERMISSIONS_DIALOG, false)
                            apply()
                        }
                    }
                }
                .show()
        }

        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED -> {
                // permission granted, nothing to do
            }
            shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) && shouldShowNotificationsPermissionsDialog(context) -> {
                displayRationale(context)
            }
            shouldShowNotificationsPermissionsDialog(context) -> {
                registerForActivityResult(ActivityResultContracts.RequestPermission()) { }.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            else -> {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::updateCalendarsJob.isInitialized && updateCalendarsJob.isActive) {
            updateCalendarsJob.cancel()
        }
    }
}
