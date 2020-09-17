package me.proton.android.calendar.presentation.calendar

import android.os.Bundle
import android.view.*
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import me.proton.android.calendar.R
import me.proton.android.calendar.domain.ValueStoreProvider
import kotlinx.android.synthetic.main.fragment_month.*
import kotlinx.android.synthetic.main.fragment_root.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.common.*
import me.proton.android.calendar.presentation.BaseDialogFragment
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

        // initialization routine
        lifecycleScope.launch(Dispatchers.Default) {

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

}
