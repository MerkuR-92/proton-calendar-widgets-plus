package me.proton.android.calendar.presentation

import android.content.Context
import android.graphics.drawable.VectorDrawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.widget.NestedScrollView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenStarted
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import me.proton.android.calendar.R
import me.proton.android.calendar.common.Navigation
import me.proton.android.calendar.common.TimberLogger
import me.proton.android.calendar.domain.ValueKey
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.usecase.BootstrapCalendarsUseCase
import me.proton.android.calendar.domain.usecase.FetchEventsUseCase
import me.proton.android.calendar.domain.usecase.LoginUserUseCase
import me.proton.android.calendar.presentation.calendar.CalendarViewModel
import me.proton.android.calendar.presentation.calendar.EventDetailsFragment
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.common.ICalUtils
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.usecase.UseCase
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalTime
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration

    private val mainViewModel: MainViewModel by inject()

    val calTest: CalendarViewModel by inject()

    private val loginUserUseCase: LoginUserUseCase by inject()
    private val bootstrapUseCase: BootstrapCalendarsUseCase by inject()
    private val valueStoreProvider: ValueStoreProvider by inject()
    private val calendarsRepository: CalendarsRepository by inject()

    lateinit var bottomSheetBehavior: BottomSheetBehavior<View>


    private val fetchEventsUseCase: FetchEventsUseCase by inject()

    lateinit var drawerLayout: DrawerLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

//        val toolbar: Toolbar = findViewById(R.id.toolbar)
//        toolbar.apply {
//            setSupportActionBar(this)
//            title = ""
//        }


//        bottomSheetBehavior = BottomSheetBehavior.from(standardBottomSheet);
//        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

//        fab.setOnClickListener { view ->

//            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);


//            val view = layoutInflater.inflate(R.layout.fragment_event_details, null)
//            val dialog = BottomSheetDialog(this)
//            dialog.setContentView(view)
//            dialog.show()

//            val dialogFragment = FullScreenDialog()
//            dialogFragment.show(supportFragmentManager, "TODO")

            // show fragment as dialog



//            val n = NestedScrollView(this)
//            n.addView(layoutInflater.inflate(R.layout.fragment_event_create_edit, null))
//
//            val dialog = BottomSheetDialog(this)
//            dialog.setContentView(n)
//            dialog.show()

//            BottomSheetBehavior.from(standardBottomSheet).state = BottomSheetBehavior.STATE_EXPANDED

//            viewModel.start()
//            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                    .setAction("Action", null).show()
//        }
        drawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_container_view) as NavHostFragment
        val navController = navHostFragment.navController


//        navController.navigate(R.id.nav_event_create_edit)

//val navController = findNavController(R.id.nav_host_fragment_container_view)

//        val navController = findNavController(R.id.nav_host_fragment_container_view)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(setOf(
            R.id.nav_calendar//, R.id.nav_settings, R.id.nav_contacts, R.id.nav_feedback
        ), drawerLayout)
        //setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)



//        calTest.calendars.observe(this) {
//            TimberLogger.d("injected view observer got scoped caldnears:")
//            it.forEach {
//                TimberLogger.d("${it.id}")
//            }
//        }

        // TODO EXAMPLES OF LIFECYCLE COROUTINE SCOPES

        this.lifecycleScope.launch {
//            val params = TextViewCompat.getTextMetricsParams(textView)
//            val precomputedText = withContext(Dispatchers.Default) {
//                PrecomputedTextCompat.create(longTextContent, params)
//            }
//            TextViewCompat.setPrecomputedText(textView, precomputedText)
        }

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

//        mainViewModel.syncServerEvents().observe(this, Observer {
//            it?.let { TimberLogger.d("local server event sync state: ${it}") } // TODO progress indicator
//        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
//        menuInflater.inflate(R.menu.main, menu)
        return true
    }

//    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {

//        val d = ContextCompat.getDrawable(this, R.drawable.ic_calendar_today)
//        (d as VectorDrawable)background = ContextCompat.getDrawable(this, R.drawable.ripple_action_primary).?

//        menu?.findItem(R.id.action_test)?.setIcon(d)
//        return super.onPrepareOptionsMenu(menu)
//    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_container_view)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean { // TODO move handling from activity to fragment?

        when (item.itemId) {

            R.id.action_create_event -> {
            }

            R.id.action_test -> {
//


//                GlobalScope.launch {
//
//                    TimberLogger.v("LOGIN USECASE: ${loginUserUseCase.execute("adamtst", "123".toByteArray())}")
//                    TimberLogger.v("BOOTSTRAP USECASE: ${bootstrapUseCase.execute("IXFh2TE4LI11sd0GYf94r7fddHNMdZvicfoWMACCjPTS-oNjpBjeclhKlIs6N48-GB5w-zM6uqX_9HFgEnzhYQ==")}")
//
//
//                }
//
////                mainViewModel.syncServerEvents().observe(this, Observer {
////                    it?.let { TimberLogger.d("local server event sync state: ${it}") } // TODO progress indicator
////                })
            }
//            R.id.action_create_event -> {
//                findNavController(R.id.nav_host_fragment).navigate(Uri.parse("${DEEPLINK_PATH_EVENT_CREATE_EDIT}"))
//            }
        }
        return super.onOptionsItemSelected(item)
    }

    //    private fun showDialogFragmentBottomSheet() {
//        val dialogView: View = layoutInflater.inflate(R.layout.fragment_bottom_sheet, null)
//        val dialog = BottomSheetDialog(this)
//        dialog.setContentView(dialogView)
//        dialog.show()
//    }
//
//
    private fun test() {



//        viewModel = ViewModelProvider(this, MainViewModel.ViewModelFactory(
//            CalendarsRepositoryImpl(AppDatabase(applicationContext)), application)).get(MainViewModel::class.java)
//
//        viewModel.calendars.observe(this) {
//             adapter.submitList(plants) // TODO ADAPTER FOR EVENT LIST
//            TimberLogger.d("view observer got scoped caldnears:")
//            it.forEach {
//                TimberLogger.d("${it.id}")
//            }
//        }
//
//        viewModel.events.observe(this) {
//             adapter.submitList(plants) // TODO ADAPTER FOR EVENT LIST
//            TimberLogger.d("view observer got events:")
//            it.forEach {
//                TimberLogger.d("${it.id}")
//            }
//        }
















//        viewModel.calendarsLiveData.observe(this) {
//            TimberLogger.d("got calendars in main activity livedata callback: ")
//            it.forEachIndexed { index, calendar ->
//                TimberLogger.d("$index: ${calendar.name}, ${calendar.description}")
//            }
//        }


//        fakeRepository.observeCalendars().observe(this) {
//            TimberLogger.d("OBSERVE FROM FLOW: got calendars from database live data: ")
//            it.forEachIndexed { index, calendar ->
//                TimberLogger.d("$index: ${calendar.name}, ${calendar.description}")
//            }
//        }
//
//        viewModel.liveDataWithoutMutableLD.observe(this) {
//            TimberLogger.d("got calendars in WITHOUT MUTABLE: ")
//            it.forEachIndexed { index, calendar ->
//                TimberLogger.d("$index: ${calendar.name}, ${calendar.description}")
//            }
//        }
//
//        viewModel.observeFlowSingleItem.observe(this) {
//            TimberLogger.d("got calendars in SINGLE ITEM FLOW: ")
//            it.forEachIndexed { index, calendar ->
//                TimberLogger.d("$index: ${calendar.name}, ${calendar.description}")
//            }
//        }

    }

}