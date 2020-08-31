package me.proton.android.calendar.presentation

import android.content.Context
import android.graphics.drawable.VectorDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.Toolbar
import androidx.core.app.AlarmManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.widget.NestedScrollView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenResumed
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
import kotlinx.coroutines.*
import me.proton.android.calendar.common.ICalUtils
import me.proton.android.calendar.common.SYNC_EVENTS_REFRESH_MS
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.presentation.calendar.EventViewModel
import org.koin.android.ext.android.inject
import org.koin.android.viewmodel.compat.SharedViewModelCompat.sharedViewModel
import org.koin.android.viewmodel.ext.android.viewModel
import org.koin.core.KoinComponent
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalTime
import java.util.*
import kotlin.concurrent.fixedRateTimer


class MainActivity : AppCompatActivity(), KoinComponent {

    private lateinit var appBarConfiguration: AppBarConfiguration

    lateinit var drawerLayout: DrawerLayout

    private val valueStoreProvider: ValueStoreProvider by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_container_view) as NavHostFragment
        val navController = navHostFragment.navController

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

//        mainViewModel.syncServerEvents().observe(this, Observer {
//            it?.let { TimberLogger.d("local server event sync state: ${it}") } // TODO progress indicator
//        })
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