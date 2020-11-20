package me.proton.android.calendar.presentation.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import kotlinx.android.synthetic.main.fragment_root.*
import me.proton.android.calendar.R
import me.proton.android.calendar.common.visibleOrGone
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.core.KoinComponent

class RootFragment : Fragment(), KoinComponent {

    private val calendarViewModel: CalendarViewModel by sharedViewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_root, container, false)

        calendarViewModel.fetchingEvents.observe(viewLifecycleOwner, Observer {
            root_progress_bar.visibleOrGone(it)
            root_fetching_events_text.visibleOrGone(it)
        })

        return rootView
    }
}
