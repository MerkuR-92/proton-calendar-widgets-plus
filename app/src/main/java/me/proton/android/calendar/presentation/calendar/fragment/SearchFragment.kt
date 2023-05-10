package me.proton.android.calendar.presentation.calendar.fragment

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.LinkMovementMethod
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.fragment_base.fragment_progress_bar
import kotlinx.android.synthetic.main.fragment_base_dialog.dialog_appbar
import kotlinx.android.synthetic.main.fragment_search.include_search_onboarding
import kotlinx.android.synthetic.main.fragment_search.ll_search_no_results
import kotlinx.android.synthetic.main.fragment_search.pb_search_onboarding_action
import kotlinx.android.synthetic.main.fragment_search.search_clear
import kotlinx.android.synthetic.main.fragment_search.search_icon_back
import kotlinx.android.synthetic.main.fragment_search.search_input
import kotlinx.android.synthetic.main.fragment_search.search_result_list
import kotlinx.android.synthetic.main.fragment_search.search_separator
import kotlinx.android.synthetic.main.layout_search_onboarding.iv_search_onboarding_illustration
import kotlinx.android.synthetic.main.layout_search_onboarding.pb_search_onboarding
import kotlinx.android.synthetic.main.layout_search_onboarding.tv_search_onboarding_progress_header
import kotlinx.android.synthetic.main.layout_search_onboarding.tv_search_onboarding_progress_percentage
import kotlinx.android.synthetic.main.layout_search_onboarding.tv_search_onboarding_progress_text
import kotlinx.android.synthetic.main.layout_search_onboarding.tv_search_onboarding_text
import kotlinx.android.synthetic.main.toolbar_action_button.view.imageButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import me.proton.android.calendar.R
import me.proton.android.calendar.common.Navigation
import me.proton.android.calendar.common.SEARCH_MIN_QUERY_LENGTH
import me.proton.android.calendar.common.SEARCH_QUERY_DEBOUNCE_MS
import me.proton.android.calendar.common.SEARCH_RESULTS_RANGE
import me.proton.android.calendar.common.utils.AndroidUtils.clearFocusAndHideKeyboard
import me.proton.android.calendar.common.utils.AndroidUtils.getColorFromAttr
import me.proton.android.calendar.common.utils.AndroidUtils.onTextChange
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.showKeyboard
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrInvisible
import me.proton.android.calendar.presentation.calendar.adapter.TimelineEventAdapter
import me.proton.android.calendar.presentation.calendar.adapter.findIndexToScrollTo
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import me.proton.android.calendar.presentation.calendar.viewModel.SearchViewModel
import me.proton.android.calendar.presentation.main.fragment.BaseDialogFragment
import org.koin.core.KoinComponent
import java.time.ZoneId

class SearchFragment() : BaseDialogFragment(), KoinComponent {

    override val TAG = "SearchFragment"
    override val layoutResourceId = R.layout.fragment_search
    override val isScrollable = false

    private val calendarViewModel: CalendarViewModel by activityViewModels()
    private val searchViewModel: SearchViewModel by activityViewModels()

    private lateinit var timelineEventAdapter: TimelineEventAdapter

    private var searchJob: Job? = null

    @OptIn(FlowPreview::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dialog_appbar.visibleOrGone(false)

        with(search_icon_back.imageButton) {
            setImageResource(R.drawable.ic_proton_arrow_left)
            setOnSingleClickListener {
                requireActivity().clearFocusAndHideKeyboard(view)
                findNavController().navigateUp()
            }
        }

        search_result_list.addOnScrollListener(object: RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy > 0) {
                    // Hide keyboard on scroll down
                    requireActivity().clearFocusAndHideKeyboard(view)
                }
            }
        })

        search_input.onTextChange().debounce(SEARCH_QUERY_DEBOUNCE_MS).onEach { rawQuery ->

            search_clear.visibleOrGone(rawQuery.isNotBlank())

            val query = rawQuery.trim().toString()

            val (userEmails, userId) = withContext(Dispatchers.Default) {
                (calendarViewModel.getUserEmails() ?: emptyList()) to (calendarViewModel.userId.value)
            }

            if (query.isNotBlank() && query.length >= SEARCH_MIN_QUERY_LENGTH && userEmails.isNotEmpty() && userId != null) {
                fragment_progress_bar?.visibleOrInvisible(true)

                searchJob?.cancel()
                searchJob = calendarViewModel.getTimelineEvents(
                    userId.id,
                    query,
                    userEmails,
                    calendarViewModel.timeFormatIs24Hour(requireContext()),
                    calendarViewModel.getCalendarUserSettingsPrimaryTimezone() ?: ZoneId.systemDefault().id).onEach {

                    if (it?.isNotEmpty() == true) {
                        ll_search_no_results.visibleOrGone(false)
                        fragment_progress_bar?.visibleOrInvisible(false)

                        // truncate the list from both ends and center on "today"

                        val range = SEARCH_RESULTS_RANGE // how many events to show before and after "today"
                        val indexToScrollTo = it.findIndexToScrollTo(calendarViewModel.getTimeZoneId() ?: ZoneId.systemDefault())

                        val leftIndex = maxOf(0, indexToScrollTo - range)
                        val rightIndex = minOf(it.size, indexToScrollTo + range)

                        val truncatedList = if (it[leftIndex] !is TimelineEventAdapter.TimelineItem.Header) {
                            // we truncated the header from beginning of the list, we need to put it back
                            val headerIndex = it.subList(0, leftIndex).indexOfLast { it is TimelineEventAdapter.TimelineItem.Header }

                            listOf(it[headerIndex]) + it.subList(leftIndex + 1, rightIndex)
                        } else it.subList(leftIndex, rightIndex)

                        timelineEventAdapter.submitList(truncatedList) {
                            val adjustedIndexToScrollTo = if (indexToScrollTo < range) indexToScrollTo else range
                            search_result_list.scrollToPosition(adjustedIndexToScrollTo)
                        }
                    } else {
                        timelineEventAdapter.submitList(emptyList())
                        fragment_progress_bar?.visibleOrInvisible(false)
                        Handler(Looper.getMainLooper()).postDelayed({
                            ll_search_no_results?.visibleOrGone(true)
                        }, 500)
                    }
                }.launchIn(lifecycleScope)

            } else {
                searchJob?.cancel()
                fragment_progress_bar?.visibleOrInvisible(false)
                ll_search_no_results.visibleOrGone(false)
                timelineEventAdapter.submitList(emptyList())
            }

        }.launchIn(lifecycleScope)

        with(search_clear.imageButton) {
            setImageResource(R.drawable.ic_proton_cross)
            setOnSingleClickListener {
                search_input.text.clear()
                timelineEventAdapter.submitList(emptyList())
            }
        }

        search_result_list.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        timelineEventAdapter = TimelineEventAdapter {
            requireActivity().clearFocusAndHideKeyboard(view)
            findNavController().navigate(
                Navigation.Deeplink.toEventDetails(it.id, it.occurrenceNumber)
            )
        }
        search_result_list.adapter = timelineEventAdapter

        pb_search_onboarding_action.setOnSingleClickListener {
            searchViewModel.actionButtonClicked()
        }

        searchViewModel.startObservingWorkerState(viewLifecycleOwner)

        searchViewModel.downloadingState.asLiveData().observe(viewLifecycleOwner) {

            when (it) {
                SearchViewModel.DownloadingState.NONE -> {
                    lifecycleScope.launchWhenResumed {
                        if (searchViewModel.isCalendarDownloadEnabled()) {
                            showSearchInterface()
                        } else {
                            showSearchOnboarding()
                        }
                    }
                }
                is SearchViewModel.DownloadingState.ONGOING -> { // pause downloading
                    showSearchOnboarding()

                    showProgressOngoing(it.progressPercentage, it.progressText)
                }
                SearchViewModel.DownloadingState.PAUSED -> { // resume downloading
                    showSearchOnboarding()

                    showProgressPaused()
                }
                SearchViewModel.DownloadingState.FINISHED -> { // shouldn't really happen, button should be invisible

                    showProgressFinished()

                    Handler(Looper.getMainLooper()).postDelayed({
                        if (isResumed) showSearchInterface()
                        // reset the state so we don't get into FINISHED each time we get back to search
                        //  after we just finished downloading
                        searchViewModel.clearDownloadingState()
                    }, 2000)
                }
                SearchViewModel.DownloadingState.ERROR -> {
                    showSearchOnboarding()

                    showProgressError()
                }
            }

        }

    }

    override fun onBackPressedCustom() {
        activity?.clearFocusAndHideKeyboard(view)
        findNavController().navigateUp()
    }

    private fun showSearchInterface() {

        pb_search_onboarding_action.visibleOrGone(false)

        include_search_onboarding.visibleOrGone(false)

        search_input.visibleOrInvisible(true)
        search_separator.visibleOrInvisible(true)

        search_input.requestFocus()
        requireContext().showKeyboard()

    }

    private fun showSearchOnboarding() {

        pb_search_onboarding_action.visibleOrGone(true)

        search_input.visibleOrInvisible(false)
        search_separator.visibleOrInvisible(false)

        requireActivity().clearFocusAndHideKeyboard(view)

        include_search_onboarding.visibleOrGone(true)

        tv_search_onboarding_text.movementMethod = LinkMovementMethod.getInstance()

    }

    private fun showProgressOngoing(progressPercentage: Int, progressText: String) {
        iv_search_onboarding_illustration.visibleOrGone(false)

        pb_search_onboarding.visibleOrGone(true)
        tv_search_onboarding_progress_header.visibleOrGone(true)
        tv_search_onboarding_progress_text.visibleOrGone(true)
        tv_search_onboarding_progress_percentage.visibleOrGone(true)

        tv_search_onboarding_progress_header.text = resources.getString(R.string.search_onboarding_progress_header_ongoing)
        tv_search_onboarding_progress_header.setTextColor(requireContext().getColorFromAttr(R.attr.proton_text_norm))
        pb_search_onboarding.progressDrawable = AppCompatResources.getDrawable(requireContext(), R.drawable.custom_progress_background)
        tv_search_onboarding_progress_text.visibleOrInvisible(true)
        tv_search_onboarding_progress_text.text = progressText
        pb_search_onboarding.progress = progressPercentage
        tv_search_onboarding_progress_percentage.text = "${progressPercentage}%"

        pb_search_onboarding_action.text = resources.getString(R.string.search_onboarding_action_pause)
    }

    private fun showProgressPaused() {
        tv_search_onboarding_progress_header.text = resources.getString(R.string.search_onboarding_progress_header_paused)
        tv_search_onboarding_progress_header.setTextColor(requireContext().getColorFromAttr(R.attr.proton_notification_error))
        tv_search_onboarding_progress_text.visibleOrInvisible(false)
        pb_search_onboarding.progressDrawable = AppCompatResources.getDrawable(requireContext(), R.drawable.custom_progress_background_paused)

        pb_search_onboarding_action.text = resources.getString(R.string.search_onboarding_action_resume)
    }

    private fun showProgressError() {
        tv_search_onboarding_progress_header.text = resources.getString(R.string.search_onboarding_progress_header_error)
        tv_search_onboarding_progress_header.setTextColor(requireContext().getColorFromAttr(R.attr.proton_notification_error))
        tv_search_onboarding_progress_text.visibleOrInvisible(false)
        pb_search_onboarding.progressDrawable = AppCompatResources.getDrawable(requireContext(), R.drawable.custom_progress_background_paused)

        pb_search_onboarding_action.text = resources.getString(R.string.search_onboarding_action_retry)
    }

    private fun showProgressFinished() {
        tv_search_onboarding_progress_header.text = resources.getString(R.string.search_onboarding_progress_header_completed)
        tv_search_onboarding_progress_header.setTextColor(requireContext().getColorFromAttr(R.attr.proton_text_norm))
        tv_search_onboarding_progress_text.visibleOrInvisible(false)
        pb_search_onboarding.progressDrawable = AppCompatResources.getDrawable(requireContext(), R.drawable.custom_progress_background_finished)
        pb_search_onboarding.progress = 100
        tv_search_onboarding_progress_percentage.text = "${100}%"

        pb_search_onboarding_action.visibleOrGone(false)
    }

}
