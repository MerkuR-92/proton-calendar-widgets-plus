package me.proton.android.calendar.presentation.holidays.fragment

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
import me.proton.android.calendar.domain.model.Holidays
import me.proton.android.calendar.presentation.holidays.adapter.HolidaysListAdapter
import me.proton.android.calendar.presentation.holidays.viewModel.HolidaysViewModel
import me.proton.android.calendar.presentation.main.fragment.BaseDialogFragment
import org.koin.core.KoinComponent

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

    private var mockupHolidaysList = arrayListOf(
        HolidaysListAdapter.HolidaysItem.Header("S", true),
        HolidaysListAdapter.HolidaysItem.Value(Holidays("id", "Switzerland", arrayListOf("Europe/Zurich"), R.drawable.flag_ch)),
        HolidaysListAdapter.HolidaysItem.Header("F", false),
        HolidaysListAdapter.HolidaysItem.Value(Holidays("id", "France", arrayListOf("Europe/Paris"), R.drawable.flag_fr)),
        HolidaysListAdapter.HolidaysItem.Header("G", false),
        HolidaysListAdapter.HolidaysItem.Value(Holidays("id", "Greece", arrayListOf("Europe/Athens"), R.drawable.flag_gr)),
        HolidaysListAdapter.HolidaysItem.Header("I", false),
        HolidaysListAdapter.HolidaysItem.Value(Holidays("id", "Italia", arrayListOf("Europe/Rome"), R.drawable.flag_it)),
        HolidaysListAdapter.HolidaysItem.Header("J", false),
        HolidaysListAdapter.HolidaysItem.Value(Holidays("id", "Japan", arrayListOf("Asia/Tokyo"), R.drawable.flag_jp)),
        HolidaysListAdapter.HolidaysItem.Header("L", false),
        HolidaysListAdapter.HolidaysItem.Value(Holidays("id", "Lithuania", arrayListOf("Europe/Vilnius"), R.drawable.flag_lt)),
        HolidaysListAdapter.HolidaysItem.Header("N", false),
        HolidaysListAdapter.HolidaysItem.Value(Holidays("id", "Netherlands", arrayListOf("Europe/Amsterdam"), R.drawable.flag_nl)),
        HolidaysListAdapter.HolidaysItem.Header("S", false),
        HolidaysListAdapter.HolidaysItem.Value(Holidays("id", "Spain", arrayListOf("Europe/Madrid"), R.drawable.flag_es)),
        HolidaysListAdapter.HolidaysItem.Value(Holidays("id", "Switzerland", arrayListOf("Europe/Zurich"), R.drawable.flag_ch)),
        HolidaysListAdapter.HolidaysItem.Header("U", false),
        HolidaysListAdapter.HolidaysItem.Value(Holidays("id", "United Kingdom", arrayListOf("Europe/London"), R.drawable.flag_gb)),
    )

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
                holidaysListAdapter.submitList(mockupHolidaysList) // TODO Remove test data set
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
            holidaysViewModel.handleCountry(it.countryName)
            requireActivity().clearFocusAndHideKeyboard(view)
            findNavController().navigateUp()
        }
        holidays_country_list.adapter = holidaysListAdapter
        holidaysListAdapter.submitList(mockupHolidaysList) // TODO Remove test data set

        setupSearch()
    }

    private fun setupSearch() {
        holidays_search_input.onTextChange { rawQuery ->

            holidays_search_clear.visibleOrGone(rawQuery.isNotBlank())

            val query = rawQuery.trim().toString()

            if (query.isNotBlank() && query.length >= HOLIDAYS_SEARCH_MIN_QUERY_LENGTH) {
                holidaysListAdapter.setSearchQuery(query)

                val values = mockupHolidaysList.filter { it.type == HolidaysListAdapter.HolidaysItemType.Value } // TODO Remove test data set
                val queriedValues = values.filter { (it as HolidaysListAdapter.HolidaysItem.Value).holidays.countryName.contains(query, ignoreCase = true) }
                val sortedQueriedValues = queriedValues.sortedBy { (it as HolidaysListAdapter.HolidaysItem.Value).holidays.countryName }
                val resultList = arrayListOf<HolidaysListAdapter.HolidaysItem>()
                sortedQueriedValues.groupBy { (it as HolidaysListAdapter.HolidaysItem.Value).holidays.countryName[0] }.forEach {
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
