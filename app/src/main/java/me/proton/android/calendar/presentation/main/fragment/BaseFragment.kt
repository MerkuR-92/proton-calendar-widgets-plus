package me.proton.android.calendar.presentation.main.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import me.proton.android.calendar.R
import me.proton.android.calendar.presentation.main.MainActivity

abstract class BaseFragment : Fragment() {

    abstract val TAG: String
    abstract val layoutResourceId: Int

    protected open fun onToolbarCreated(toolbar: Toolbar) {}

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val rootView = inflater.inflate(R.layout.fragment_base, container, false)
        rootView.findViewById<ViewGroup>(R.id.fragment_container).addView(
            inflater.inflate(layoutResourceId, container, false)
        )

        // Hide splash screen
        (requireActivity() as? MainActivity)?.displaySplashScreen(false)

        val toolbar = rootView.findViewById(R.id.fragment_toolbar) as Toolbar
        toolbar.apply {
            setNavigationIcon(R.drawable.ic_hamburger)

            setNavigationOnClickListener { // TODO make sure we shouldn't clear this embedded dialog-stack
                ((requireActivity().findViewById(R.id.drawer_layout) as DrawerLayout).openDrawer(
                    GravityCompat.START)) // TODO maybe move to activity
            }
            onToolbarCreated(this)
        }

        return rootView
    }
}
