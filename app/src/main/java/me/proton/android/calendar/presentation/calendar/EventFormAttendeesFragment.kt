package me.proton.android.calendar.presentation.calendar

import android.os.Bundle
import android.view.*
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
import kotlinx.coroutines.launch
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
            findNavController().navigateUp()
        }

        nav_event_form_attendees_search_input.onTextChange { query ->
            nav_event_form_attendees_done.visibleOrInvisible(query.isEmpty())
            nav_event_form_attendees_search_clear.visibleOrGone(query.isNotEmpty())

            val attendeeList = eventViewModel.eventLiveData.value?.iCalEvent?.attendees
            nav_event_form_attendees_list_layout.visibleOrGone(query.isEmpty() && !attendeeList.isNullOrEmpty())
            nav_event_form_attendees_organizer.visibleOrGone(query.isEmpty() && !attendeeList.isNullOrEmpty())
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
            lifecycleScope.launch {
                val tmpAttendeeList = ArrayList(eventViewModel.eventLiveData.value?.iCalEvent?.attendees ?: listOf<Attendee>())

                if (tmpAttendeeList.size >= ATTENDEE_MAX_ALLOWED) {
                    view.displaySnackBar(getString(R.string.snack_maximum_participants_reached))
                    searchAttendeeListAdapter.notifyDataSetChanged() // Clear loading icon visibility
                    return@launch
                }

                val email = it.extractEmail()
                email?.let {
                    // TODO Use get canonical route for second validation ? What are the actual error cases ?
                    val canonicalEmail = calendarViewModel.getCanonicalEmails(listOf(email))?.first()?.second
                }

                tmpAttendeeList.add(it)

                nav_event_form_attendees_search_input.text.clear()

                eventViewModel.handleAttendee(it)

                if (tmpAttendeeList.size >= ATTENDEE_MAX_ALLOWED) { // Warn the user once max is reached
                    view.displaySnackBar(getString(R.string.snack_maximum_participants_reached))
                }
            }
        }
        (nav_event_form_attendees_search_list.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        nav_event_form_attendees_search_list.adapter = searchAttendeeListAdapter

        searchAttendeeList.observe(viewLifecycleOwner, { searchAttendeeList ->
            searchAttendeeListAdapter.submitList(searchAttendeeList.sortedBy { it.commonName })
            searchAttendeeListAdapter.notifyDataSetChanged()
        })
        eventViewModel.eventLiveData.observe(viewLifecycleOwner, { event ->
            val attendeeList = event.iCalEvent.attendees
            searchAttendeeListAdapter.setAttendeeList(attendeeList)
            attendeeListAdapter.submitList(attendeeList.reversed()) // Last added at the top, first at the bottom
            attendeeListAdapter.notifyDataSetChanged()
            nav_event_form_attendees_list_header.visibleOrGone(attendeeList.isNotEmpty())
            nav_event_form_attendees_list_header.text = getString(R.string.event_text_participants, attendeeList.size, ATTENDEE_MAX_ALLOWED)
            nav_event_form_attendees_list_layout.visibleOrGone(attendeeList.isNotEmpty())
            nav_event_form_attendees_organizer.visibleOrGone(attendeeList.isNotEmpty())

            lifecycleScope.launch {
                val organizerEmail = calendarViewModel.getCalendarDefaultEmail(event.calendar.id)
                organizerEmail?.let {
                    nav_event_form_attendees_organizer.item_add_attendee_title.text = getString(R.string.event_current_user_organizer)
                    nav_event_form_attendees_organizer.item_add_attendee_press.visibleOrGone(false)
                    nav_event_form_attendees_organizer.item_add_attendee_description.visibleOrGone(true)
                    nav_event_form_attendees_organizer.item_add_attendee_description.text = organizerEmail
                    nav_event_form_attendees_organizer.item_add_attendee_initials.text = getInitials(organizerEmail)
                }
            }
        })
    }

    override fun onBackPressedCustom() {
        findNavController().navigateUp()
    }
}
