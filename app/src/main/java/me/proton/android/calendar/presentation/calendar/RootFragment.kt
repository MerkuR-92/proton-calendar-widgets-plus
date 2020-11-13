package me.proton.android.calendar.presentation.calendar

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import kotlinx.android.synthetic.main.fragment_root.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.R
import me.proton.android.calendar.common.TimberLogger
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.presentation.BaseDialogFragment
import me.proton.android.calendar.presentation.MainViewModel
import me.proton.android.calendar.presentation.MainViewModel.Companion.INTENT_ACTION_SHOW_EVENT_DETAILS
import org.koin.android.ext.android.inject
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.core.KoinComponent

class RootFragment : Fragment(), KoinComponent {

    private val calendarViewModel: CalendarViewModel by sharedViewModel()
    private val valueStoreProvider: ValueStoreProvider by inject()
    private val mainViewModel: MainViewModel by sharedViewModel()

    private fun navigateToNotification(notificationIntent: Intent) {
        TimberLogger.v("root fragment navigating with notification intent: ${notificationIntent.data.toString()}")
        findNavController().navigate(notificationIntent.data!!)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_root, container, false)
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        GlobalScope.launch(Dispatchers.Default) {// TODO move to viewmodel
            calendarViewModel.init(this)

            val userId = valueStoreProvider.provideValueStore("TODO LOGIN").getString("USERID")
            if (userId != null) {
                val notificationIntent = mainViewModel.consumeIntent(INTENT_ACTION_SHOW_EVENT_DETAILS)
                notificationIntent?.let { navigateToNotification(notificationIntent) }
            }
        }
    }
}
