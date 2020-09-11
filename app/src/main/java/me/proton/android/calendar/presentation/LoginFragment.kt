package me.proton.android.calendar.presentation

import android.os.Bundle
import android.renderscript.Sampler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import me.proton.android.calendar.R
import me.proton.android.calendar.common.TimberLogger
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.usecase.BootstrapCalendarsUseCase
import me.proton.android.calendar.domain.usecase.LoginUserUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import kotlinx.android.synthetic.main.fragment_home_second.*
import kotlinx.android.synthetic.main.fragment_login_todo.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.common.Navigation
import me.proton.android.calendar.common.hideKeyboard
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.usecase.FetchEventsUseCase
import org.koin.android.ext.android.inject
import org.koin.android.viewmodel.ext.android.viewModel
import java.time.LocalDate
import java.util.*

// TODO PROTOTYPE, NUKE THIS
class LoginFragment : Fragment() {

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_login_todo, container, false)
    }

    private val fetchEventsUseCase: FetchEventsUseCase by inject()
    private val calendarsRepository: CalendarsRepository by inject()

    private val loginUserUseCase: LoginUserUseCase by inject()
    private val bootstrapUseCase: BootstrapCalendarsUseCase by inject()
    private val valueStoreProvider: ValueStoreProvider by inject()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        button.setOnClickListener {

            lifecycleScope.launch(Dispatchers.Main) {
                val login = withContext(Dispatchers.IO) {
                    loginUserUseCase.execute(et_username.text.toString(), et_password.text.toString().toByteArray())
                }

                // TODO UGLY HACK
                val TODOvalueStore = valueStoreProvider.provideValueStore("TODO LOGIN")
                if (TODOvalueStore.getString("USERID") != null && (login is UseCase.Result.Success)) {

                        Toast.makeText(requireContext(), "fetching calendars", Toast.LENGTH_SHORT).show()

                    val bootstrap = withContext(Dispatchers.IO) {
                        bootstrapUseCase.execute(TODOvalueStore.getString("USERID")!!)
                    }
                        if (bootstrap == UseCase.Result.Success) {

                            GlobalScope.launch { // TODO

                            withContext(Dispatchers.Default) {

                                val TODOvalueStore = valueStoreProvider.provideValueStore("TODO LOGIN")
                                val TODOuserID = TODOvalueStore.getString("USERID") // TODO
                                if (TODOuserID != null) {
                                    //val defaultCalendar = calendarsRepository.getDefaultCalendarId(TODOuserID)

                                    // TODO get only calendars selected in sidebar
                                    val selectedCalendarIds = calendarsRepository.getActiveCalendars(TODOuserID).map { it.id }.toList()

                                    val timeZoneId = calendarsRepository.selectUserSettings(TODOuserID)?.primaryTimezone!!

                                    // TODO this method never returns!!!
                                    calendarsRepository.init(selectedCalendarIds, LocalDate.now().plusMonths(10 /*TODO create more events when we switch between months*/), timeZoneId)

                                }

                            }

                            }

                            requireActivity().hideKeyboard()

                            findNavController().navigate(Navigation.Deeplink.toCalendar())
                            //Toast.makeText(requireContext(), "NOW CLICK (FETCH EVENTS)", Toast.LENGTH_LONG).show()

                        } else {
                                Toast.makeText(requireContext(), "ERROR: ${(bootstrap as UseCase.Result.Error).message}", Toast.LENGTH_LONG).show()

                        }
                } else {
                        Toast.makeText(requireContext(), "Login failed: ${(login as UseCase.Result.Error).message}", Toast.LENGTH_LONG).show()
                }
            }

        }

    }
}
