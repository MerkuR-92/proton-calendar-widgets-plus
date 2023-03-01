package me.proton.android.calendar.presentation.holidays.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.fragment_base_dialog.dialog_appbar
import kotlinx.android.synthetic.main.fragment_holidays_search.holidays_country_list
import kotlinx.android.synthetic.main.fragment_holidays_search.holidays_search_clear
import kotlinx.android.synthetic.main.fragment_holidays_search.holidays_search_close
import kotlinx.android.synthetic.main.fragment_holidays_search.holidays_search_input
import kotlinx.android.synthetic.main.toolbar_action_button.view.imageButton
import kotlinx.android.synthetic.main.toolbar_action_text.view.toolbar_action_text
import me.proton.android.calendar.ProtonCalendarApplication
import me.proton.android.calendar.R
import me.proton.android.calendar.common.HOLIDAYS_SEARCH_MIN_QUERY_LENGTH
import me.proton.android.calendar.common.utils.AndroidUtils.clearFocusAndHideKeyboard
import me.proton.android.calendar.common.utils.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.utils.AndroidUtils.onTextChange
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.data.entity.HolidaysCalendarEntity
import me.proton.android.calendar.domain.model.Holidays
import me.proton.android.calendar.presentation.holidays.adapter.HolidaysListAdapter
import me.proton.android.calendar.presentation.holidays.viewModel.HolidaysViewModel
import me.proton.android.calendar.presentation.main.fragment.BaseDialogFragment
import me.proton.core.presentation.utils.currentLocale
import org.koin.core.KoinComponent
import java.time.ZoneId

@AndroidEntryPoint
class HolidaysSearchFragment : BaseDialogFragment(), KoinComponent {

    override val TAG: String
        get() = "HolidaysFragment"
    override val layoutResourceId: Int
        get() = R.layout.fragment_holidays_search

    override val navigateUp = true
    override val isScrollable = false

    private val holidaysViewModel: HolidaysViewModel by activityViewModels()
    private val application: ProtonCalendarApplication by lazy {
        requireContext().applicationContext as ProtonCalendarApplication
    }

    private lateinit var holidaysListAdapter: HolidaysListAdapter
    private var holidaysCalendarList = arrayListOf<HolidaysListAdapter.HolidaysItem>()

    override fun onBackPressedCustom() {
        findNavController().navigateUp()
    }

    override fun onNavigationIconClicked(): Boolean {
        onBackPressedCustom()
        return true
    }

    override fun onToolbarCreated(toolbar: Toolbar) {
        toolbar.findViewById<TextView>(R.id.dialog_toolbar_title).text = "" // No Title
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Hide app bar
        dialog_appbar.visibleOrGone(false)

        holidays_search_close.toolbar_action_text.text = getString(R.string.dialog_button_close)
        holidays_search_close.toolbar_action_text.setOnSingleClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            findNavController().navigateUp()
        }

        with(holidays_search_clear.imageButton) {
            setImageResource(R.drawable.ic_proton_cross)
            setOnSingleClickListener {
                requireActivity().clearFocusAndHideKeyboard(view)
                holidays_search_input.text.clear()
                holidaysListAdapter.setSearchQuery("")
                holidaysListAdapter.submitList(holidaysCalendarList)
            }
        }

        holidays_country_list.addOnScrollListener(object: RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy > 0) {
                    // Hide keyboard on scroll down
                    requireActivity().clearFocusAndHideKeyboard(view)
                }
            }
        })

        val countryListLayoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        holidays_country_list.layoutManager = countryListLayoutManager
        holidaysListAdapter = HolidaysListAdapter {
            holidaysViewModel.handleCountry(it.country, requireContext().resources.configuration.currentLocale().language.lowercase())
            requireActivity().clearFocusAndHideKeyboard(view)
            findNavController().navigateUp()
        }
        holidays_country_list.adapter = holidaysListAdapter

        setupSearch()

        holidaysViewModel.holidaysCalendars.observe(viewLifecycleOwner) { holidaysCalendars ->
            holidaysCalendars ?: return@observe

            holidaysCalendarList.clear()
            holidaysCalendarList.addAll(holidaysCalendars.toHolidaysItems())
            holidaysListAdapter.submitList(holidaysCalendarList)
        }
    }

    @SuppressLint("DiscouragedApi") // Suppress annotation due to getting flag drawables by identifier name
    fun List<HolidaysCalendarEntity>.toHolidaysItems(): List<HolidaysListAdapter.HolidaysItem> {
        val holidays = arrayListOf<HolidaysListAdapter.HolidaysItem>()
        this.sortedBy { it.country }.groupBy { it.country }.forEach {
            val header = HolidaysListAdapter.HolidaysItem.Header(
                it.key.first().uppercase(),
                it.value.first().timezones.contains(ZoneId.systemDefault().id)
            )
            if (header.locationDefault) {
                if (!holidays.contains(header)) holidays.add(0, header)
                val countryCode = it.value.first().countryCode
                holidays.add(1,
                    HolidaysListAdapter.HolidaysItem.Value(
                        Holidays(
                            it.key,
                            resources.getIdentifier(
                                "${requireContext().packageName}:drawable/flag_$countryCode",
                                "drawable",
                                requireContext().packageName
                            )
                        )
                    )
                )
            } else {
                if (!holidays.contains(header)) holidays.add(header)
                val countryCode = it.value.first().countryCode
                holidays.add(
                    HolidaysListAdapter.HolidaysItem.Value(
                        Holidays(
                            it.key,
                            resources.getIdentifier(
                                "${requireContext().packageName}:drawable/flag_$countryCode",
                                "drawable",
                                requireContext().packageName
                            )
                        )
                    )
                )
            }
        }
        return holidays
    }

    private fun setupSearch() {
        holidays_search_input.onTextChange { rawQuery ->

            holidays_search_clear.visibleOrGone(rawQuery.isNotBlank())

            val query = rawQuery.trim().toString()

            if (query.isNotBlank() && query.length >= HOLIDAYS_SEARCH_MIN_QUERY_LENGTH) {
                holidaysListAdapter.setSearchQuery(query)

                val values = holidaysCalendarList.filter { it.type == HolidaysListAdapter.HolidaysItemType.Value }
                val queriedValues = values.filter { (it as HolidaysListAdapter.HolidaysItem.Value).holidays.country.contains(query, ignoreCase = true) }
                val sortedQueriedValues = queriedValues.sortedBy { (it as HolidaysListAdapter.HolidaysItem.Value).holidays.country }
                val resultList = arrayListOf<HolidaysListAdapter.HolidaysItem>()
                sortedQueriedValues.groupBy { (it as HolidaysListAdapter.HolidaysItem.Value).holidays.country[0] }.forEach {
                    resultList.add(HolidaysListAdapter.HolidaysItem.Header(it.key.toString(), false))
                    resultList.addAll(it.value)
                }
                holidaysListAdapter.submitList(resultList.distinct())
            } else {
            }
        }
    }

    private fun displayNetworkError() {
        view?.displaySnackBar(getString(R.string.snack_network_error))
    }
}
