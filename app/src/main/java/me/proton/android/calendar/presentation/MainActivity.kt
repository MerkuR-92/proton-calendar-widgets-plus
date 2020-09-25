package me.proton.android.calendar.presentation

import android.os.Bundle
import android.view.Menu
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenStarted
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView
import me.proton.android.calendar.R
import me.proton.android.calendar.common.Navigation
import me.proton.android.calendar.domain.ValueStoreProvider
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.nav_view_main.view.*
import kotlinx.coroutines.launch
import me.proton.android.calendar.BuildConfig
import org.koin.android.ext.android.inject
import org.koin.core.KoinComponent


class MainActivity : AppCompatActivity(), KoinComponent {

    private lateinit var appBarConfiguration: AppBarConfiguration

    lateinit var drawerLayout: DrawerLayout
    private lateinit var navController: NavController

    private val valueStoreProvider: ValueStoreProvider by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        nav_view_main_content.nav_view_version.text = getString(R.string.nav_view_version_name,
            BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)

        nav_view_main_content.nav_view_more_login_layout.setOnClickListener {
            findNavController(R.id.nav_host_fragment_container_view).navigate(Navigation.Deeplink.toLogin())
            drawerLayout.close()
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