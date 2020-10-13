package me.proton.android.calendar.presentation.calendar

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import me.proton.android.calendar.R
import me.proton.android.calendar.domain.ValueStoreProvider
import kotlinx.android.synthetic.main.fragment_month.*
import kotlinx.android.synthetic.main.fragment_root.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.common.*
import me.proton.android.calendar.presentation.BaseDialogFragment
import me.proton.android.calendar.presentation.MainActivity
import me.proton.android.calendar.presentation.MainViewModel
import org.koin.android.ext.android.inject
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.android.viewmodel.ext.android.viewModel
import org.koin.core.KoinComponent

class RootFragment : BaseDialogFragment(), KoinComponent {

    private val calendarViewModel: CalendarViewModel by sharedViewModel()
    private val valueStoreProvider: ValueStoreProvider by inject()
    private val mainViewModel: MainViewModel by viewModel()

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
                findNavController().navigate(Navigation.Deeplink.toMonth())
            } else {

                withContext(Dispatchers.Main) {
                    text_home.text = "PLEASE LOGIN"
                }

            }
        }

    }

    override fun onStart() {
        super.onStart()

        val intent = activity?.intent

        // if MainActivity was started from clicking on system notification, handle that and show Event details
        if (intent != null && intent.action == MainActivity.INTENT_ACTION_SHOW_EVENT_DETAILS) {

            TimberLogger.v("handling intent for event details ${intent.getStringExtra(MainActivity.INTENT_EXTRA_EVENT_ID)}, ${intent.getIntExtra(
                MainActivity.INTENT_EXTRA_EVENT_OCCURRENCE, 0)}")

            findNavController().navigate(Navigation.Deeplink.toEventDetails(intent.getStringExtra(MainActivity.INTENT_EXTRA_EVENT_ID)!!, intent.getIntExtra(
                MainActivity.INTENT_EXTRA_EVENT_OCCURRENCE, 0)))

        }
    }
}
