package me.proton.android.calendar.presentation.calendar

import android.os.Bundle
import android.view.*
import android.widget.AbsListView
import androidx.databinding.adapters.AbsListViewBindingAdapter
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import biweekly.property.Attendee
import kotlinx.android.synthetic.main.fragment_base_dialog.*
import kotlinx.android.synthetic.main.fragment_event_form_attendees.*
import kotlinx.android.synthetic.main.item_add_attendee.view.*
import kotlinx.android.synthetic.main.toolbar_action_text.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.common.FormValidation.ATTENDEE_MAX_ALLOWED
import me.proton.android.calendar.presentation.BaseDialogFragment
import me.proton.core.presentation.utils.InputValidationResult.Companion.EMAIL_VALIDATION_PATTERN
import me.proton.core.presentation.utils.onTextChange
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.core.KoinComponent


class EventFormAttendeesFragment() : BaseDialogFragment(), KoinComponent {

    override val TAG = "EventFormAttendeesFragment" // TODO
    override val layoutResourceId = R.layout.fragment_event_form_attendees
    override val isScrollable = false

    private val navigationArguments: EventFormFragmentArgs by navArgs()

    private val calendarViewModel: CalendarViewModel by sharedViewModel()
    private val eventViewModel: EventViewModel by sharedViewModel()

    private lateinit var attendeeListAdapter: AddAttendeeListAdapter
    private lateinit var searchAttendeeListAdapter: AddAttendeeListAdapter

    private val regex = EMAIL_VALIDATION_PATTERN.toRegex(RegexOption.IGNORE_CASE)

    // TODO Move to VM ?
    private val _searchAttendeeList: MutableLiveData<List<Attendee>> = MutableLiveData()
    private val searchAttendeeList: LiveData<List<Attendee>> = _searchAttendeeList

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dialog_appbar.visibleOrGone(false)
        nav_event_form_attendees_done.toolbar_action_text.text = getString(R.string.action_done)
        nav_event_form_attendees_list_header.text = getString(R.string.event_text_participants, 0, ATTENDEE_MAX_ALLOWED)

        nav_event_form_attendees_done.toolbar_action_text.setOnSingleClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            findNavController().navigateUp()
        }

        nav_event_form_attendees_list.addOnScrollListener(object: RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy > 0) {
                    requireActivity().clearFocusAndHideKeyboard(view)
                }
            }
        })

        nav_event_form_attendees_search_list.addOnScrollListener(object: RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy > 0) {
                    requireActivity().clearFocusAndHideKeyboard(view)
                }
            }
        })

        nav_event_form_attendees_search_input.onTextChange { query ->
            nav_event_form_attendees_done.visibleOrInvisible(query.isEmpty())
            nav_event_form_attendees_search_clear.visibleOrGone(query.isNotEmpty())

            val attendeeList = eventViewModel.eventLiveData.value?.iCalEvent?.attendees
            nav_event_form_attendees_list_layout.visibleOrGone(query.isEmpty() && !attendeeList.isNullOrEmpty())
            if (query.isEmpty()) nav_event_form_attendees_search_list.visibleOrGone(false)

            if (query.isNotEmpty()) {
                searchAttendeeListAdapter.setQuery(query.toString())

                val searchResult =
                    when {
                        regex.matches(query) -> {
                            val participant = Attendee("", query.toString())
                            listOf(participant)
                        }
                        else -> listOf()
                    }

                nav_event_form_attendees_search_list.visibleOrGone(searchResult.isNotEmpty())
                _searchAttendeeList.postValue(searchResult)
            }
        }

        nav_event_form_attendees_search_clear.setOnSingleClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            nav_event_form_attendees_search_input.text.clear()
        }

        val userEmails = calendarViewModel.userEmails.value ?: listOf()

        val attendeesLayoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        nav_event_form_attendees_list.layoutManager = attendeesLayoutManager
        attendeeListAdapter = AddAttendeeListAdapter(false, userEmails) {
            eventViewModel.handleAttendee(it, false)
        }
        (nav_event_form_attendees_list.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        nav_event_form_attendees_list.adapter = attendeeListAdapter

        val searchAttendeesLayoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        nav_event_form_attendees_search_list.layoutManager = searchAttendeesLayoutManager
        searchAttendeeListAdapter = AddAttendeeListAdapter(true, userEmails) {
            addAttendee(it)
        }
        (nav_event_form_attendees_search_list.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        nav_event_form_attendees_search_list.adapter = searchAttendeeListAdapter

        searchAttendeeList.observe(viewLifecycleOwner, { searchAttendeeList ->
            searchAttendeeListAdapter.submitList(searchAttendeeList.sortedBy { it.commonName })
            searchAttendeeListAdapter.notifyDataSetChanged()
        })

        eventViewModel.eventLiveData.observe(viewLifecycleOwner, { event ->
            lifecycleScope.launch {
                val organizer = Attendee(
                    getString(R.string.event_current_user_organizer),
                    calendarViewModel.getCalendarDefaultEmail(event.calendar.id)
                )
                if (organizer.email != null) attendeeListAdapter.setOrganizerEmail(organizer.email)
                val attendeeList = ArrayList(event.iCalEvent.attendees.reversed()) // Last added at the top, first at the bottom
                if (!attendeeList.isNullOrEmpty() && organizer.email != null && !attendeeList.contains(organizer)) {
                    attendeeList.add(organizer)
                }

                searchAttendeeListAdapter.setAttendeeList(attendeeList)

                val scrollUp = attendeeList.size > attendeeListAdapter.currentList.size

                attendeeListAdapter.submitList(attendeeList) {
                    if (scrollUp) nav_event_form_attendees_list.smoothScrollToPosition(0) // Scroll up top to new attendee
                }

                nav_event_form_attendees_list_header.visibleOrGone(attendeeList.isNotEmpty())
                val attendeeCount = if (attendeeList.size > 0) attendeeList.size - 1 else 0 // Subtract organizer that was added to bottom of list
                nav_event_form_attendees_list_header.text = getString(R.string.event_text_participants, attendeeCount, ATTENDEE_MAX_ALLOWED)
                nav_event_form_attendees_list_layout.visibleOrGone(attendeeList.isNotEmpty())
            }
        })

        nav_event_form_attendees_search_input.requestFocus()
        requireContext().showKeyboard()
    }

    private fun addAttendee(attendee: Attendee) {
        lifecycleScope.launch {
            val tmpAttendeeList = ArrayList(eventViewModel.eventLiveData.value?.iCalEvent?.attendees ?: listOf<Attendee>())

            if (tmpAttendeeList.size >= ATTENDEE_MAX_ALLOWED) {
                view?.displaySnackBar(getString(R.string.snack_maximum_participants_reached))
                searchAttendeeListAdapter.notifyDataSetChanged() // Clear loading icon visibility
                return@launch
            }

            val email = attendee.extractEmail()
            email?.let {
                // TODO Use get canonical route for second validation ? What are the actual error cases ?
                val canonicalEmail = calendarViewModel.getCanonicalEmails(listOf(email))?.first()?.second
            }

            tmpAttendeeList.add(attendee)

            nav_event_form_attendees_search_input.text.clear()

            eventViewModel.handleAttendee(attendee)

            if (tmpAttendeeList.size >= ATTENDEE_MAX_ALLOWED) { // Warn the user once max is reached
                view?.displaySnackBar(getString(R.string.snack_maximum_participants_reached))
            }
        }
    }

    override fun onBackPressedCustom() {
        findNavController().navigateUp()
    }
}
