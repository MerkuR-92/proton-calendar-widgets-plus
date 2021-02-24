package me.proton.android.calendar.presentation.calendar

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.provider.ContactsContract
import android.view.*
import android.view.inputmethod.EditorInfo
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import biweekly.property.Attendee
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_base_dialog.*
import kotlinx.android.synthetic.main.fragment_event_form_attendees.*
import kotlinx.android.synthetic.main.item_add_attendee.view.*
import kotlinx.android.synthetic.main.nav_view_main.view.*
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
import me.proton.core.util.kotlin.nullIfBlank
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.core.KoinComponent


class EventFormAttendeesFragment() : BaseDialogFragment(), KoinComponent, LoaderManager.LoaderCallbacks<Cursor> {

    override val TAG = "EventFormAttendeesFragment" // TODO
    override val layoutResourceId = R.layout.fragment_event_form_attendees
    override val isScrollable = false

    private val navigationArguments: EventFormFragmentArgs by navArgs()

    private val calendarViewModel: CalendarViewModel by sharedViewModel()
    private val eventViewModel: EventViewModel by sharedViewModel()

    private val _searchAttendeeList: MutableLiveData<List<Attendee>> = MutableLiveData()
    private val searchAttendeeList: LiveData<List<Attendee>> = _searchAttendeeList

    private lateinit var attendeeListAdapter: AddAttendeeListAdapter
    private lateinit var searchAttendeeListAdapter: AddAttendeeListAdapter

    private var contactsAccessGranted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        contactsAccessGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (contactsAccessGranted) {
            LoaderManager.getInstance(this).initLoader(0, null, this)
        }
    }

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
                    // Hide keyboard on scroll down
                    requireActivity().clearFocusAndHideKeyboard(view)
                }
            }
        })

        nav_event_form_attendees_search_list.addOnScrollListener(object: RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy > 0) {
                    // Hide keyboard on scroll down
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
                nav_event_form_attendees_search_list.visibleOrGone(true)
                searchAttendeeListAdapter.setQuery(query.toString())

                if (contactsAccessGranted) {
                    val args = Bundle()
                    args.putString(CONTACTS_SEARCH_QUERY, query.toString())
                    LoaderManager.getInstance(this).restartLoader(0, args, this)
                } else {
                    val searchResult =
                        when {
                            validateEmail(query) -> {
                                val participant = Attendee("", query.toString())
                                listOf(participant)
                            }
                            else -> listOf()
                        }

                    nav_event_form_attendees_search_list.visibleOrGone(searchResult.isNotEmpty())
                    _searchAttendeeList.postValue(searchResult)
                }
            }
        }

        nav_event_form_attendees_search_input.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE && validateEmail(nav_event_form_attendees_search_input.text)) {
                val query = nav_event_form_attendees_search_input.text.toString()
                val index = searchAttendeeListAdapter.currentList.indexOfFirst {
                    it.extractEmail().equals(query, true)
                }
                if (index != -1) {
                    val itemPress = nav_event_form_attendees_search_list.getChildAt(index).item_add_attendee_press
                    if (itemPress.isVisible) itemPress.performClick()
                }
            }
            false
        }

        nav_event_form_attendees_search_clear.setOnSingleClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            nav_event_form_attendees_search_input.text.clear()
        }

        val userEmails = calendarViewModel.userAddresses.value?.map { it.email } ?: listOf()

        val attendeesLayoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        nav_event_form_attendees_list.layoutManager = attendeesLayoutManager
        attendeeListAdapter = AddAttendeeListAdapter(false) {
            lifecycleScope.launch {
                eventViewModel.handleAttendee(it, addAttendee = false)
            }
        }
        (nav_event_form_attendees_list.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        nav_event_form_attendees_list.adapter = attendeeListAdapter

        val searchAttendeesLayoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        nav_event_form_attendees_search_list.layoutManager = searchAttendeesLayoutManager
        searchAttendeeListAdapter = AddAttendeeListAdapter(true) {
            addAttendee(it, userEmails)
        }
        (nav_event_form_attendees_search_list.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        nav_event_form_attendees_search_list.adapter = searchAttendeeListAdapter

        searchAttendeeList.observe(viewLifecycleOwner, { searchAttendeeList ->
            searchAttendeeListAdapter.submitList(searchAttendeeList.sortedBy { it.commonName })
            // TODO Try to find a way to refresh the highlighted text and icons visibility without calling notifyDataSetChanged
            searchAttendeeListAdapter.notifyDataSetChanged()
        })

        eventViewModel.eventLiveData.observe(viewLifecycleOwner, { event ->
            lifecycleScope.launch {
                val user = withContext(Dispatchers.Default) {
                    calendarViewModel.selectUser()
                }
                val organizerEmail = calendarViewModel.getCalendarDefaultEmail(event.calendar.id)
                val organizerName = user?.displayName ?: organizerEmail
                val organizer = Attendee(
                    organizerName,
                    organizerEmail
                )

                if (organizer.email != null) {
                    attendeeListAdapter.setOrganizerEmail(organizer.email)
                    searchAttendeeListAdapter.setOrganizerEmail(organizer.email)
                }
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

    private fun addAttendee(attendee: Attendee, userEmails: List<String>) {
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
                val canonicalEmail = calendarViewModel.getCanonicalEmails(listOf(email))?.get(email)
                if (canonicalEmail == null) {
                    view?.displaySnackBar(getString(R.string.snack_add_participant_invalid_email))
                    searchAttendeeListAdapter.notifyDataSetChanged() // Clear loading icon visibility
                    return@launch
                }

                if (userEmails.firstOrNull { it.equals(canonicalEmail, true) } != null) {
                    view?.displaySnackBar(getString(R.string.snack_add_self_as_participant))
                    searchAttendeeListAdapter.notifyDataSetChanged() // Clear loading icon visibility
                    return@launch
                }

                tmpAttendeeList.add(attendee)

                nav_event_form_attendees_search_input.text.clear()

                eventViewModel.handleAttendee(attendee, canonicalEmail)

                if (tmpAttendeeList.size >= ATTENDEE_MAX_ALLOWED) { // Warn the user once max is reached
                    view?.displaySnackBar(getString(R.string.snack_maximum_participants_reached))
                }

                searchAttendeeListAdapter.notifyDataSetChanged() // Clear loading icon visibility
            }
        }
    }

    override fun onBackPressedCustom() {
        findNavController().navigateUp()
    }

    // Loader and callbacks for contacts search

    companion object {
        private const val ANDROID_ORDER_BY = ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY + " ASC"
        private const val ANDROID_SELECTION = (
                ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY + " LIKE ?" + " OR " + ContactsContract.CommonDataKinds.Email.ADDRESS + " LIKE ?" + " OR "
                        + ContactsContract.CommonDataKinds.Email.DATA + " LIKE ?")
        private val ANDROID_PROJECTION = arrayOf(ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Email.ADDRESS,
            ContactsContract.CommonDataKinds.Email.DATA)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        val searchString = args?.getString(CONTACTS_SEARCH_QUERY) ?: ""
        val selectionArgs = arrayOf("%$searchString%", "%$searchString%", "%$searchString%")
        return CursorLoader(
            requireContext(),
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            ANDROID_PROJECTION,
            ANDROID_SELECTION,
            selectionArgs,
            ANDROID_ORDER_BY
        )
    }

    override fun onLoadFinished(loader: Loader<Cursor>, data: Cursor) {
        if(data.isBeforeFirst) {
            val attendees = data.getAttendeeList()
            if (attendees.isNotEmpty()) {
                _searchAttendeeList.postValue(attendees)
            } else {
                // If no results in contacts, suggest email
                val query = nav_event_form_attendees_search_input.text
                val searchResult =
                    when {
                        validateEmail(query) -> {
                            val participant = Attendee("", query.toString())
                            listOf(participant)
                        }
                        else -> listOf()
                    }
                _searchAttendeeList.postValue(searchResult)
            }
        }
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        _searchAttendeeList.postValue(emptyList())
    }

    private fun Cursor.extractAttendee(): Attendee {
        val name = getString(getColumnIndex(ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY))
        val email = getString(getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS))
        return Attendee(
            name,
            email
        )
    }

    private fun Cursor.getAttendeeList(): List<Attendee> {
        val contactsList = mutableListOf<Attendee>()
        this.apply {
            while(moveToNext()) {
                val contactItem = extractAttendee()
                contactsList.add(contactItem)
            }
        }
        return contactsList
    }
}
