package me.proton.android.calendar.presentation.calendar

import android.os.Bundle
import android.view.*
import androidx.navigation.fragment.findNavController
import me.proton.android.calendar.R
import me.proton.android.calendar.domain.ValueStoreProvider
import kotlinx.android.synthetic.main.fragment_root.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.common.*
import me.proton.android.calendar.presentation.BaseDialogFragment
import me.proton.android.calendar.presentation.MainViewModel
import me.proton.android.calendar.presentation.MainViewModel.Companion.INTENT_ACTION_SHOW_EVENT_DETAILS
import me.proton.android.calendar.presentation.MainViewModel.Companion.INTENT_EXTRA_EVENT_ID
import me.proton.android.calendar.presentation.MainViewModel.Companion.INTENT_EXTRA_EVENT_OCCURRENCE
import org.koin.android.ext.android.inject
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.android.viewmodel.ext.android.viewModel
import org.koin.core.KoinComponent

class RootFragment : BaseDialogFragment(), KoinComponent {

    private val calendarViewModel: CalendarViewModel by sharedViewModel()
    private val valueStoreProvider: ValueStoreProvider by inject()
    private val mainViewModel: MainViewModel by sharedViewModel()

    override val TAG: String
        get() = "RootFragment"
    override val layoutResourceId: Int
        get() = R.layout.fragment_root

    override val isTopLevel = true
    override val isScrollable = false


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        GlobalScope.launch(Dispatchers.Default) {// TODO move to viewmodel
            calendarViewModel.init(this)

            val userId = valueStoreProvider.provideValueStore("TODO LOGIN").getString("USERID")
            if (userId != null) {

                val notificationIntent = mainViewModel.consumeIntent(INTENT_ACTION_SHOW_EVENT_DETAILS)
                if (notificationIntent != null) {

                    TimberLogger.v("root fragment navigating with notification intent")
                    findNavController().navigate(Navigation.Deeplink.toEventDetails(notificationIntent.getStringExtra(INTENT_EXTRA_EVENT_ID)!!, notificationIntent.getIntExtra(
                        INTENT_EXTRA_EVENT_OCCURRENCE, 0)))
                    
                } else {

                    TimberLogger.v("root fragment navigating to month")
                    findNavController().navigate(Navigation.Deeplink.toMonth())

                }

            } else {

                withContext(Dispatchers.Main) {
                    text_home.text = "PLEASE LOGIN"
                }

            }
        }

    }

}
