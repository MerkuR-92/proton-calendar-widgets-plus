package me.proton.android.calendar.presentation

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.Menu
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_root.*
import kotlinx.android.synthetic.main.nav_view_main.*
import kotlinx.android.synthetic.main.nav_view_main.view.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import me.proton.android.calendar.BuildConfig
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.ShowNotificationUseCase
import me.proton.android.calendar.presentation.account.AccountViewModel
import me.proton.android.calendar.presentation.calendar.CalendarViewModel
import me.proton.android.calendar.presentation.forceupdate.ForceUpdateViewModel
import me.proton.core.presentation.utils.showForceUpdate
import me.proton.core.util.kotlin.nullIfBlank
import org.koin.android.ext.android.inject
import org.koin.android.viewmodel.ext.android.viewModel
import org.koin.core.KoinComponent
import java.time.ZonedDateTime
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), KoinComponent {

    private lateinit var appBarConfiguration: AppBarConfiguration

    lateinit var drawerLayout: DrawerLayout
    private lateinit var navController: NavController
    private lateinit var navHostFragment: NavHostFragment

    private val logger: Logger by inject()

    @Inject
    lateinit var forceUpdateViewModel: ForceUpdateViewModel

    private val calendarViewModel: CalendarViewModel by viewModel()
    private val mainViewModel: MainViewModel by viewModel()
    private val accountViewModel: AccountViewModel by viewModel()
    private lateinit var activeCalendarListAdapter: CalendarListAdapter
    private lateinit var disabledCalendarListAdapter: CalendarListAdapter

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // https://stackoverflow.com/questions/16283079/re-launch-of-activity-on-home-button-but-only-the-first-time/16447508#16447508
        if (!isTaskRoot) {
            // Android launched another instance of the root activity into an existing task
            //  so just quietly finish and go away, dropping the user back into the activity
            //  at the top of the stack (ie: the last state of this task)
            finish()
            return
        }

        intent?.let { mainViewModel.handleIntent(intent) }

        with(forceUpdateViewModel) {
            forceUpdate.observe(this@MainActivity, Observer {
                if (it.forceUpdate) {
                    supportFragmentManager.showForceUpdate(it.apiErrorMessage)
                }
            })
        }

        with(accountViewModel) {
            init(this@MainActivity, savedInstanceState == null)

            state.observe(this@MainActivity, Observer { state ->
                if (errorReport.value == AccountViewModel.Error.NoError) {
                    handleAccountState(this, state)
                }
            })

            // Handle Bootstrap errors
            errorReport.observe(this@MainActivity, Observer { errorReport ->
                if (errorReport == AccountViewModel.Error.NoError) return@Observer
                val dialogTitle: Int
                val dialogMessage: Int
                when (errorReport) {
                    is AccountViewModel.Error.NoCalendar -> {
                        dialogTitle = R.string.bootstrap_error_no_calendar_title
                        dialogMessage = R.string.bootstrap_error_no_calendar_message
                    }
                    is AccountViewModel.Error.NoActiveCalendar -> {
                        dialogTitle = R.string.bootstrap_error_no_active_calendar_title
                        dialogMessage = R.string.bootstrap_error_no_active_calendar_message
                    }
                    is AccountViewModel.Error.FreeUser -> {
                        dialogTitle = R.string.bootstrap_error_free_user_title
                        dialogMessage = R.string.bootstrap_error_free_user_message
                    }
                    is AccountViewModel.Error.DelinquentUser -> {
                        dialogTitle = R.string.bootstrap_error_delinquent_user_title
                        dialogMessage = R.string.bootstrap_error_delinquent_user_message
                    }
                    is AccountViewModel.Error.StorageQuotaReached -> {
                        dialogTitle = R.string.bootstrap_error_store_quota_reached_title
                        dialogMessage = R.string.bootstrap_error_store_quota_reached_message
                    }
                    else -> {
                        // TODO default case should not exist
                        clearError()
                        handleAccountState(this, state.value!!)
                        return@Observer
                    }
                }
                val materialDialog = MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle(dialogTitle)
                    .setMessage(dialogMessage)
                    .setCancelable(false)
                    .setPositiveButton(R.string.bootstrap_error_default_confirm) { _, _ ->
                        clearError()
                        handleAccountState(this, state.value!!)
                    }.show()
                materialDialog.findViewById<TextView>(android.R.id.message)?.movementMethod =
                    LinkMovementMethod.getInstance()
            })
        }

        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)

        navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_container_view) as NavHostFragment
        navController = navHostFragment.navController

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_calendar//, R.id.nav_settings, R.id.nav_contacts, R.id.nav_feedback
            ), drawerLayout
        )
        navView.setupWithNavController(navController)

        nav_view_main_content.nav_view_version.text = getString(
            R.string.nav_view_version_name,
            BuildConfig.VERSION_NAME
        )

        initDrawerListeners()

        initDrawerCalendarsList()

        // Set timezone visibility to gone by default
        nav_view_timezone.visibleOrGone(false)

        calendarViewModel.timeZoneId.observe(this@MainActivity) { zoneId ->
            nav_view_timezone.visibleOrGone(true)
            nav_view_timezone_login_title.text =
                ICalUtils.formatTimeZoneId(zoneId.id, ZonedDateTime.now(zoneId).toInstant())
        }
    }

    private fun handleAccountState(accountViewModel: AccountViewModel, state: AccountViewModel.State) {
        when (state) {
            AccountViewModel.State.LoginNeeded -> {
                findNavController(R.id.nav_host_fragment_container_view).navigate(Navigation.Deeplink.toRoot())
                accountViewModel.startLoginWorkflow()
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
                    navigateTo(Navigation.Deeplink.toMonth())
                }
            }
            AccountViewModel.State.LoginInProgress,
            AccountViewModel.State.Processing -> {
                displaySplashScreen(true, true, resources.getString(R.string.splash_after_login_init))
            }
        }
    }

    fun displaySplashScreen(display: Boolean, spinner: Boolean = false, spinnerText: String? = null) {
        // TODO Status bar and navigation bar colors are set to brand_norm on dark / light mode change because of activity recreation

        if (spinner) {
            calendarViewModel.fetchingEvents.postValue(spinnerText)
        }

        drawerLayout.setDrawerLockMode(if (display) DrawerLayout.LOCK_MODE_LOCKED_CLOSED else DrawerLayout.LOCK_MODE_UNLOCKED)

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
            drawerLayout.close()
        }

        nav_view_main_content.nav_view_more_bug_press.setOnSingleClickListener {
            navController.navigate(R.id.action_nav_calendar_to_nav_bug_report)
            drawerLayout.close()
        }
        // TODO Remove feature flag
        nav_view_main_content.nav_view_more_settings_layout.visibleOrGone(FeatureFlag.SETTINGS_DRAWER)
        nav_view_main_content.nav_view_more_settings_press.setOnSingleClickListener {
            navController.navigate(R.id.action_nav_calendar_to_nav_settings)
            drawerLayout.close()
        }
        accountViewModel.hasPrimary.observe(this@MainActivity) { hasPrimary ->
            nav_view_main_content.nav_view_more_logout_layout.isVisible = hasPrimary
            nav_view_main_content.nav_view_more_login_layout.isGone = hasPrimary
        }
        nav_view_main_content.nav_view_more_logout_press.setOnSingleClickListener {
            accountViewModel.logoutPrimary()
            drawerLayout.close()
        }
        nav_view_main_content.nav_view_more_login_press.setOnSingleClickListener {
            accountViewModel.startLoginWorkflow()
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
        val activeCalendarListView = nav_view_main_content.nav_view_calendars_list
        val activeCalendarsLayoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        activeCalendarListView.layoutManager = activeCalendarsLayoutManager
        activeCalendarListAdapter = CalendarListAdapter() { calendarEntity ->
            //On Calendar click event
            lifecycleScope.launch {
                calendarViewModel.updateCalendarVisibility(calendarEntity.id, calendarEntity.display)
                updateCalendarsDelayed()
            }
        }
        (activeCalendarListView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        activeCalendarListView.adapter = activeCalendarListAdapter

        val disabledCalendarListView = nav_view_main_content.nav_view_disabled_calendars_list
        val disabledCalendarLayoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        disabledCalendarListView.layoutManager = disabledCalendarLayoutManager
        disabledCalendarListAdapter = CalendarListAdapter() { calendarEntity ->
            //On Calendar click event
            lifecycleScope.launch {
                calendarViewModel.updateCalendarVisibility(calendarEntity.id, calendarEntity.display)
                updateCalendarsDelayed()
            }
        }
        (disabledCalendarListView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        disabledCalendarListView.adapter = disabledCalendarListAdapter
    }

    private lateinit var job: Job
    private fun updateCalendarsDelayed() {
        if (this::job.isInitialized && job.isActive) {
            job.cancel()
        }
        job = lifecycleScope.launch {
            delay(SYNC_CALENDARS_DELAY.toMillis())
            calendarViewModel.updateServerCalendarListDisplay(activeCalendarListAdapter.currentList + disabledCalendarListAdapter.currentList)
        }
    }

    private fun initDrawerCalendarsListContent() {
        lifecycleScope.launch {
            calendarViewModel.selectCalendars()
            calendarViewModel.activeCalendars.observe(this@MainActivity) { activeCalendars ->
                activeCalendars ?: return@observe
                activeCalendarListAdapter.submitList(activeCalendars)
                nav_view_main_content.nav_view_calendars.visibleOrGone(activeCalendars.isNotEmpty())
            }
            calendarViewModel.disabledCalendars.observe(this@MainActivity) { disabledCalendars ->
                disabledCalendars ?: return@observe
                disabledCalendarListAdapter.submitList(disabledCalendars)
                nav_view_main_content.nav_view_disabled_calendars.visibleOrGone(disabledCalendars.isNotEmpty())
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

        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else if (navController.currentDestination?.id == R.id.nav_calendar) {
            moveTaskToBack(true)
        } else {
            super.onBackPressed()
        }
    }
}
