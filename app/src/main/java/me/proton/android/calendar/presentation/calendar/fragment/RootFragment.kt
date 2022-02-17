package me.proton.android.calendar.presentation.calendar.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import kotlinx.android.synthetic.main.fragment_root.root_progress_bar
import kotlinx.android.synthetic.main.fragment_root.root_progress_text
import me.proton.android.calendar.R
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import org.koin.core.KoinComponent

class RootFragment : Fragment(), KoinComponent {

    private val calendarViewModel: CalendarViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_root, container, false)

        calendarViewModel.fetchingEvents.observe(viewLifecycleOwner, Observer {
            if (it == null) {
                root_progress_bar.visibleOrGone(false)
                root_progress_text.visibleOrGone(false)
                root_progress_text.text = ""
            } else {
                root_progress_bar.visibleOrGone(true)
                root_progress_text.visibleOrGone(true)
                root_progress_text.text = it
            }

        })

        return rootView
    }
}
