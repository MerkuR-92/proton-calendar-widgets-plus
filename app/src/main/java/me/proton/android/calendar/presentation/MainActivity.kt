package me.proton.android.calendar.presentation

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import androidx.lifecycle.whenStarted
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.navigation.NavigationView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.nav_view_main.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.BuildConfig
import me.proton.android.calendar.R
import me.proton.android.calendar.common.Navigation
import me.proton.android.calendar.common.TimberLogger
import me.proton.android.calendar.common.getInitials
import me.proton.android.calendar.common.visibleOrGone
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.presentation.account.AccountViewModel
import me.proton.android.calendar.presentation.calendar.CalendarViewModel
import org.koin.android.ext.android.inject
import org.koin.android.viewmodel.ext.android.viewModel
import org.koin.core.KoinComponent

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), KoinComponent {

    private lateinit var appBarConfiguration: AppBarConfiguration

    lateinit var drawerLayout: DrawerLayout
    private lateinit var navController: NavController

    private val valueStoreProvider: ValueStoreProvider by inject()

    private val calendarViewModel: CalendarViewModel by viewModel()
    private val mainViewModel: MainViewModel by viewModel()
    private val accountViewModel: AccountViewModel by viewModel()
    private lateinit var activeCalendarListAdapter: CalendarListAdapter
    private lateinit var disabledCalendarListAdapter: CalendarListAdapter

    // TODO move to MainViewModel once we have proper user management
    private lateinit var userEmail: String

    private fun navigateToMonth() {
        lifecycleScope.launch(Dispatchers.Default) {
            calendarViewModel.init(this)

            // Refresh drawer content now that we are logged in.
            initDrawerHeader()
            initDrawerCalendarsListContent()

            findNavController(R.id.nav_host_fragment_container_view)
                .navigate(Navigation.Deeplink.toMonth())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        with(accountViewModel) {
            init(this@MainActivity)
            state.observe(this@MainActivity, Observer { state ->
                when (state) {
                    is AccountViewModel.State.LoginNeeded -> startLoginWorkflow()
                    is AccountViewModel.State.Ready -> navigateToMonth()
                }
            })
        }

        intent?.let { mainViewModel.handleIntent(intent) }

        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_container_view) as NavHostFragment
        navController = navHostFragment.navController

        appBarConfiguration = AppBarConfiguration(setOf(
            R.id.nav_calendar//, R.id.nav_settings, R.id.nav_contacts, R.id.nav_feedback
        ), drawerLayout)
        //setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        this.lifecycleScope.launch {
//            val params = TextViewCompat.getTextMetricsParams(textView)
//            val precomputedText = withContext(Dispatchers.Default) {
//                PrecomputedTextCompat.create(longTextContent, params)
//            }
//            TextViewCompat.setPrecomputedText(textView, precomputedText)
        }

//fixedRateTimer().




        //CountDownTimer().

//        AlarmManagerCompat.setAlarmClock()



        lifecycleScope.launch {
            whenStarted {
                // The block inside will run only when Lifecycle is at least STARTED.
                // It will start executing when fragment is started and
                // can call other suspend methods.
//                loadingView.visibility = View.VISIBLE
//                val canAccess = withContext(Dispatchers.IO) {
//                    checkUserAccess()
//                }

                // When checkUserAccess returns, the next line is automatically
                // suspended if the Lifecycle is not *at least* STARTED.
                // We could safely run fragment transactions because we know the
                // code won't run unless the lifecycle is at least STARTED.
//                loadingView.visibility = View.GONE
//                if (canAccess == false) {
//                    findNavController().popBackStack()
//                } else {
//                    showContent()
//                }
            }

            // This line runs only after the whenStarted block above has completed.

        }

        //TODO Remove once login module implemented
        nav_view_main_content.nav_view_user_name.text = "Proton Calendar Rocks"
        nav_view_main_content.nav_view_user_mail.text = "protoncalendarrocks@pm.me"
        nav_view_main_content.nav_view_user_initials.text = "PC"

        nav_view_main_content.nav_view_version.text = getString(R.string.nav_view_version_name,
            BuildConfig.VERSION_NAME)

        initDrawerListeners()

        initDrawerHeader()

        initDrawerCalendarsList()

//        mainViewModel.syncServerEvents().observe(this, Observer {
//            it?.let { TimberLogger.d("local server event sync state: ${it}") } // TODO progress indicator
//        })
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { mainViewModel.handleIntent(intent) }
    }

    private fun initDrawerListeners() {
        //Navigation drawer items on click listeners
        nav_view_main_content.nav_view_user_layout.setOnClickListener {
            drawerLayout.close()
        }
        nav_view_main_content.nav_view_more_bug_press.setOnClickListener {
            drawerLayout.close()
        }
        accountViewModel.hasPrimary {
            nav_view_main_content.nav_view_more_logout_layout.isVisible = it
            nav_view_main_content.nav_view_more_login_layout.isGone = it
        }
        nav_view_main_content.nav_view_more_logout_press.setOnClickListener {
            accountViewModel.logoutPrimary()
            drawerLayout.close()
        }
        nav_view_main_content.nav_view_more_login_press.setOnClickListener {
            accountViewModel.startLoginWorkflow()
            drawerLayout.close()
        }
    }

    fun initDrawerHeader() {
        lifecycleScope.launch {
            val user = withContext(Dispatchers.Default) {
                calendarViewModel.selectUser()
            }
            if (user != null) {
                userEmail = user.email
                nav_view_main_content.nav_view_user_name.text = user.displayName
                nav_view_main_content.nav_view_user_mail.text = user.email
                var initials: String = getInitials(user.displayName)
                nav_view_main_content.nav_view_user_initials.text = initials?: ""
            }
        }
    }

    private fun initDrawerCalendarsList() {
        val activeCalendarListView = nav_view_main_content.nav_view_calendars_list
        val activeCalendarsLayoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        activeCalendarListView.layoutManager = activeCalendarsLayoutManager
        activeCalendarListAdapter = CalendarListAdapter() { calendarEntity, display ->
            //On Calendar click event
            calendarViewModel.updateServerCalendar(calendarEntity.id, display = display)
        }
        (activeCalendarListView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        activeCalendarListView.adapter = activeCalendarListAdapter

        val disabledCalendarListView = nav_view_main_content.nav_view_disabled_calendars_list
        val disabledCalendarLayoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        disabledCalendarListView.layoutManager = disabledCalendarLayoutManager
        disabledCalendarListAdapter = CalendarListAdapter() { calendarEntity, display ->
            //On Calendar click event
            calendarViewModel.updateServerCalendar(calendarEntity.id, display = display)
        }
        (disabledCalendarListView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        disabledCalendarListView.adapter = disabledCalendarListAdapter

        //Populate calendars list
        initDrawerCalendarsListContent()
    }

    fun initDrawerCalendarsListContent() {
        lifecycleScope.launch {
            calendarViewModel.selectActiveCalendars()?.observe(this@MainActivity) { activeCalendars ->
                activeCalendarListAdapter.submitList(activeCalendars)
                nav_view_main_content.nav_view_calendars.visibleOrGone(!activeCalendars.isEmpty())
            }

            calendarViewModel.selectDisabledCalendars()?.observe(this@MainActivity) { disabledCalendars ->
                disabledCalendarListAdapter.submitList(disabledCalendars)
                nav_view_main_content.nav_view_disabled_calendars.visibleOrGone(!disabledCalendars.isEmpty())
            }
        }
    }

    fun getUserEmail(): String? {
        return if (this::userEmail.isInitialized) userEmail else ""
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



    //    private fun showDialogFragmentBottomSheet() {
//        val dialogView: View = layoutInflater.inflate(R.layout.fragment_bottom_sheet, null)
//        val dialog = BottomSheetDialog(this)
//        dialog.setContentView(dialogView)
//        dialog.show()
//    }
//
//

}
