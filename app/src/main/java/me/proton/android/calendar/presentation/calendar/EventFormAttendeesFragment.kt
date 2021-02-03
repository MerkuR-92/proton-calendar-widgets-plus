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
import kotlinx.android.synthetic.main.fragment_base_dialog.*
import kotlinx.android.synthetic.main.fragment_event_form_attendees.*
import kotlinx.android.synthetic.main.toolbar_action_text.view.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.domain.model.Participant
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

    private lateinit var attendeeListAdapter: AddAttendeeListAdapter
    private lateinit var searchAttendeeListAdapter: AddAttendeeListAdapter

    private val regex = EMAIL_VALIDATION_PATTERN.toRegex(RegexOption.IGNORE_CASE)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dialog_appbar.visibleOrGone(false)
        nav_event_form_attendees_done.toolbar_action_text.text = getString(R.string.action_done)

        nav_event_form_attendees_done.toolbar_action_text.setOnSingleClickListener {
            // TODO Handle save
            findNavController().navigateUp()
        }

        nav_event_form_attendees_search_input.onTextChange { query ->
            nav_event_form_attendees_done.visibleOrInvisible(query.isEmpty())
            nav_event_form_attendees_search_clear.visibleOrGone(query.isNotEmpty())

            nav_event_form_attendees_list_layout.visibleOrGone(query.isEmpty())
            if (query.isEmpty()) nav_event_form_attendees_search_list.visibleOrGone(false)

            if (query.isNotEmpty()) {
                searchAttendeeListAdapter.setQuery(query.toString())
                val contacts = contactsList.value?.filter {
                    val match = it.email.contains(query, true) || it.commonName?.contains(query, true) == true
                    if (match) it.added = attendeeList.value?.contains(it) == true
                    match
                }

                val searchResult =
                    when {
                        contacts?.isNotEmpty() == true -> contacts
                        regex.matches(query) -> {
                            val participant = Participant(query.toString())
                            if (attendeeList.value?.firstOrNull { it.email.equals(query.toString(), true) } != null) {
                                participant.added = true
                            }
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

        // TODO Remove
//        setMockedContactsList()

        val userEmails = calendarViewModel.userEmails.value ?: listOf()

        val attendeesLayoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        nav_event_form_attendees_list.layoutManager = attendeesLayoutManager
        attendeeListAdapter = AddAttendeeListAdapter(false, userEmails) {
            val tmpList = ArrayList(attendeeList.value ?: listOf<Participant>())
            tmpList.remove(it)
            _attendeeList.postValue(tmpList)

            val contacts = contactsList.value ?: return@AddAttendeeListAdapter
            val index = contacts.indexOf(it)
            if (index == -1) return@AddAttendeeListAdapter
            contacts[index].added = false
            _contactsList.postValue(contacts)
        }
        (nav_event_form_attendees_list.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        nav_event_form_attendees_list.adapter = attendeeListAdapter

        val searchAttendeesLayoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        nav_event_form_attendees_search_list.layoutManager = searchAttendeesLayoutManager
        searchAttendeeListAdapter = AddAttendeeListAdapter(true, userEmails) {
            lifecycleScope.launch {
                // TODO Remove launch and delay
                delay(1000)

                val tmpAttendeeList = ArrayList(attendeeList.value ?: listOf<Participant>())
                tmpAttendeeList.add(it)
                _attendeeList.postValue(tmpAttendeeList)

                nav_event_form_attendees_search_input.text.clear()

                val contacts = contactsList.value ?: return@launch
                val index = contacts.indexOf(it)
                if (index == -1) return@launch
                contacts[index].added = true
                _contactsList.postValue(contacts)
            }
        }
        (nav_event_form_attendees_search_list.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        nav_event_form_attendees_search_list.adapter = searchAttendeeListAdapter

        contactsList.observe(viewLifecycleOwner, { contactsList ->

        })
        searchAttendeeList.observe(viewLifecycleOwner, { searchAttendeeList ->
            searchAttendeeListAdapter.submitList(searchAttendeeList.sortedBy { it.commonName })
            searchAttendeeListAdapter.notifyDataSetChanged()
        })
        attendeeList.observe(viewLifecycleOwner, { attendeeList ->
            attendeeListAdapter.submitList(attendeeList.sortedBy { it.commonName })
            attendeeListAdapter.notifyDataSetChanged()
            nav_event_form_attendees_list_header.visibleOrGone(!attendeeList.isNullOrEmpty())
            // Change visibility of list here if empty to avoid any delay between header and list visibility change
            if (attendeeList.isNullOrEmpty()) nav_event_form_attendees_list_layout.visibleOrGone(false)
        })
    }

    override fun onBackPressedCustom() {
        findNavController().navigateUp()
    }



    // TODO Remove mocks and move LiveData to VM

    private val _contactsList: MutableLiveData<List<Participant>> = MutableLiveData()
    private val contactsList: LiveData<List<Participant>> = _contactsList

    private val _attendeeList: MutableLiveData<List<Participant>> = MutableLiveData()
    private val attendeeList: LiveData<List<Participant>> = _attendeeList

    private val _searchAttendeeList: MutableLiveData<List<Participant>> = MutableLiveData()
    private val searchAttendeeList: LiveData<List<Participant>> = _searchAttendeeList

    private fun setMockedContactsList() {
        val contacts = arrayListOf<Participant>()
        contacts.add(Participant("john.doe@pm.me", "John Doe"))
        contacts.add(Participant("waterloo@pm.me", "Napoleon B."))
        contacts.add(Participant("hamlet@pm.me", "William Shakespeare"))
        contacts.add(Participant("potus@pm.me", "Abraham Lincoln"))
        contacts.add(Participant("moonboy@pm.me", "Neil Armstrong"))
        contacts.add(Participant("appletree@pm.me", "Isaac Newton"))
        contacts.add(Participant("veryveryveryveryveryveryveryverylongemailaddressveryveryveryveryveryveryveryverylongemailaddress@pm.me", "Annoying Case"))
        _contactsList.postValue(contacts)
    }
}
