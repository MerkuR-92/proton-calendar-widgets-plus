package me.proton.android.calendar.presentation

import android.app.Dialog
import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.DialogFragment
import me.proton.android.calendar.R

// TODO maybe remove DialogFragment whatsoever
abstract class BaseDialogFragment : DialogFragment() {

    // properties for subclasses to override
    abstract val TAG: String
    abstract val layoutResourceId: Int

    /**
     * Show navigation arrow or close button
     */
    protected open val navigateUp: Boolean = true

    /**
     * Show hamburger icon and open drawer on navigation click
     */
    protected open val isTopLevel: Boolean = false

    /**
     * Wrap this Fragment's layout in ScrollView
     */
    protected open val isScrollable: Boolean = true
    protected open val actionMenuResourceId: Int? = null
    protected open fun onMenuItemClicked(menuItem: MenuItem) {}
    protected open fun onToolbarCreated(toolbar: Toolbar) {}

    //TODO Remove once we get rid of DialogFragment
    protected open fun onBackPressedCustom() {}

    /**
     * Override this to customise action on "close/arrow back" click.
     */
    protected open fun onNavigationIconClicked(): Boolean = false
    // ^ properties for subclasses to override

    protected lateinit var toolbar: Toolbar

    override fun getTheme(): Int = R.style.AppFullscreenDialogTheme

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        // workaround for animations being ignored by <dialog> component in Navigation Graph
        dialog?.window?.attributes?.windowAnimations = theme
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val rootView = inflater.inflate(R.layout.fragment_base_dialog, container, false)
        rootView.findViewById<ViewGroup>(if (isScrollable) R.id.dialog_container_scrollable else R.id.dialog_container).addView(
            inflater.inflate(layoutResourceId, container, false)
        )

        toolbar = rootView.findViewById(R.id.dialog_toolbar)

        toolbar.apply {
            title = ""
            // navigation
            if (isTopLevel) {
                setNavigationIcon(R.drawable.ic_hamburger)
            } else {
                if (navigateUp) {
                    setNavigationIcon(R.drawable.ic_arrow_left)
                } else {
                    setNavigationIcon(R.drawable.ic_close)
                }
            }

            setNavigationOnClickListener { // TODO make sure we shouldn't clear this embedded dialog-stack
                if (isTopLevel) {
                    ((requireActivity().findViewById(R.id.drawer_layout) as DrawerLayout).openDrawer(
                        GravityCompat.START)) // TODO maybe move to activity
                } else {
                    if (!onNavigationIconClicked()) {
                        dismiss()
                    }
                }
            }
            onToolbarCreated(this)
        }

        return rootView
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return object : Dialog(requireActivity(), theme) {
            override fun onBackPressed() {
                onBackPressedCustom()
            }
        }
    }
}
