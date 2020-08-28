package me.proton.android.calendar.presentation.calendar

import android.os.Bundle
import android.view.*
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.navigation.findNavController
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import kotlinx.android.synthetic.main.fragment_base_dialog.*
import me.proton.android.calendar.R
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.usecase.BootstrapCalendarsUseCase
import me.proton.android.calendar.domain.usecase.FetchEventsUseCase
import me.proton.android.calendar.domain.usecase.LoginUserUseCase
import kotlinx.android.synthetic.main.fragment_calendar.*
import me.proton.android.calendar.common.*
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.presentation.BaseDialogFragment
import org.koin.android.ext.android.inject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class CalendarFragment : BaseDialogFragment() {

    private val calendarViewModel: CalendarViewModel by inject()


    // TODO only for testing now vvvvvv
    private val loginUserUseCase: LoginUserUseCase by inject()
    private val bootstrapUseCase: BootstrapCalendarsUseCase by inject()
    // TODO ^^^^^
    private val fetchEventsUseCase: FetchEventsUseCase by inject()
    private val calendarsRepository: CalendarsRepository by inject()

    private val initialToday = LocalDate.now()
    private lateinit var agendaAdapter: CalendarAgendaAdapter

    private lateinit var toolbarTitle: TextView

private val valueStoreProvider: ValueStoreProvider by inject()
    override val TAG: String
        get() = "CalendarFragment"
    override val layoutResourceId: Int
        get() = R.layout.fragment_calendar

    override val isTopLevel = true
    override val isScrollable = false

    override fun onToolbarCreated(toolbar: Toolbar) {

        val buttonCreate = layoutInflater.inflate(R.layout.toolbar_action_primary, toolbar_content, false)
        with (buttonCreate) {
            (this as ImageButton).setImageDrawable(ContextCompat.getDrawable(this.context, R.drawable.ic_plus))
            setOnClickListener {
                // each item in the adapter is one day
                val currentDate = initialToday.plusDays((pager.currentItem - agendaAdapter.startingPosition).toLong())
                requireActivity().findNavController(R.id.nav_host_fragment_container_view).navigate(Navigation.Deeplink.toEventCreate(currentDate, ICalUtils.generateEventStartTime()))
            }
        }
        val buttonToday = layoutInflater.inflate(R.layout.toolbar_action_secondary, toolbar_content, false)
        with (buttonToday) {
            (this as ImageButton).setImageDrawable(ContextCompat.getDrawable(this.context, R.drawable.ic_calendar_today))
            (this as ImageButton).setColorFilter(0) // this image is not one color, so we remove default tinting
            setOnClickListener {
                val todayOffset = ChronoUnit.DAYS.between(initialToday, LocalDate.now()).toInt()
                pager.setCurrentItem(agendaAdapter.startingPosition + todayOffset, true)
            }
        }

        // TODO extract somewhere to remove boilerplate
        with(toolbar.findViewById<ViewGroup>(R.id.toolbar_content)) {
            addView(
                buttonToday, resources.getDimensionPixelSize(
                    R.dimen.icon_size
                ), resources.getDimensionPixelSize(R.dimen.icon_size)
            )
            addView(
                buttonCreate, resources.getDimensionPixelSize(
                    R.dimen.icon_size
                ), resources.getDimensionPixelSize(R.dimen.icon_size)
            )
        }

        toolbarTitle = toolbar.findViewById(R.id.toolbar_title)

    }

    //    private val args: AgendaFragmentArgs by navArgs()

    // Lazy injected MySimplePresenter
//    val firstPresenter: MySimplePresenter by inject()




//    private lateinit var

//    override fun onCreateView(
//            inflater: LayoutInflater, container: ViewGroup?,
//            savedInstanceState: Bundle?
//    ): View? {
        // Inflate the layout for this fragment
        //return inflater.inflate(R.layout.fragment_calendar, container, false)
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        setHasOptionsMenu(true)
    }

    private inner class CalendarAgendaAdapter(activity: FragmentActivity, val startingDate: LocalDate) : FragmentStateAdapter(activity) {

        val startingPosition = itemCount / 2

        override fun getItemCount(): Int {
            return Int.MAX_VALUE
        }

        override fun createFragment(position: Int): Fragment {
            return ItemCalendarAgendaFragment(calendarViewModel, position, startingDate.plusDays((position - startingPosition).toLong()))
        }

    }

    var pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            setToolbarTitle(position)
        }
    }

    private fun setToolbarTitle(position: Int) {
        toolbarTitle.text = agendaAdapter.startingDate.plusDays((position - agendaAdapter.startingPosition).toLong()).formatMonth()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pager.unregisterOnPageChangeCallback(pageChangeCallback)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        agendaAdapter = CalendarAgendaAdapter(requireActivity(), initialToday)

        pager.apply{
            adapter = agendaAdapter
            offscreenPageLimit = 1 // TODO
            setCurrentItem(agendaAdapter.startingPosition, false)
            setToolbarTitle(agendaAdapter.startingPosition)
        }

        pager.registerOnPageChangeCallback(pageChangeCallback)


        /*recyclerView.apply {
            //            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@CalendarFragment.context)
            adapter = EventAdapter {
                TimberLogger.d("event clicked: $it")

                findNavController().navigate(Navigation.Deeplink.toEventDetails(it.id))

                //findNavController().navigate(CalendarFragmentDirections.actionNavCalendarToNavEventDetails()) // TODO use deeplinking?

        //                val modalBottomSheet = EventDetailsFragment()
                // TODO pass arguments with userId and eventId
        //                modalBottomSheet.show(parentFragmentManager, "TODO TAG")

                // com.google.android.material.R.id.design_bottom_sheet


            }
            recyclerView.addItemDecoration(
                DividerItemDecoration(
                    this@CalendarFragment.context,
                    LinearLayoutManager.VERTICAL
                )
            )

//        view.findViewById<TextView>(R.id.textview_home_second).text =
//                getString(R.string.hello_home_second, args.myArg)
//
//        view.findViewById<Button>(R.id.button_home_second).setOnClickListener {
//            findNavController().navigate(R.id.action_HomeSecondFragment_to_HomeFragment)
        }*/

//        viewModel = ViewModelProvider(this, CalendarViewModel.ViewModelFactory(// TODO fix this!!!!
//            FakeCalendarRepositoryRepository(activity?.applicationContext!!), activity?.application!!)).get(CalendarViewModel::class.java)








    }


}
