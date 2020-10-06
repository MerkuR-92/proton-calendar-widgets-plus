package me.proton.android.calendar.presentation

import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.DialogFragment
import kotlinx.android.synthetic.main.fragment_base_dialog.*
import me.proton.android.calendar.R
import me.proton.android.calendar.common.visibleOrInvisible

// TODO maybe remove DialogFragment whatsoever
abstract class BaseDialogFragment : DialogFragment() {

    // properties for subclasses to override
    abstract val TAG: String
    abstract val layoutResourceId: Int
    //    abstract val showToolbar: Boolean

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

    /**
     * Override this to customise action on "close/arrow back" click.
     */
    protected open fun onNavigationIconClicked(): Boolean = false
    // ^ properties for subclasses to override

    protected fun setProgressBarVisibility(visible: Boolean) {
        progress_bar?.visibleOrInvisible(visible)
    }

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
//        isCancelable = false

                val rootView = inflater.inflate(R.layout.fragment_base_dialog, container, false)
                rootView.findViewById<ViewGroup>(if (isScrollable) R.id.container_scrollable else R.id.container).addView(
                    inflater.inflate(layoutResourceId, container, false)
                )

                toolbar = rootView.findViewById<Toolbar>(R.id.toolbar)

                toolbar.apply {
                    setTitle("")
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

            //toolbar.setNavigationIcon(R.drawable.ic_good);
            //toolbar.setTitle("Title");
            //

            //(activity as AppCompatActivity?)!!.setSupportActionBar(toolbar)

//        val actionBar: ActionBar? = (activity as AppCompatActivity?)!!.supportActionBar
//        if (actionBar != null) {
//            actionBar.
//            actionBar.setDisplayHomeAsUpEnabled(true)
//            actionBar.setHomeButtonEnabled(true)
//            actionBar.setHomeAsUpIndicator(android.R.drawable.ic_menu_close_clear_cancel)
//        }
//        setHasOptionsMenu(true)

            return rootView
        }

        //    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
//        val dialog = super.onCreateDialog(savedInstanceState)
//        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
//        return super.onCreateDialog(savedInstanceState)
//    }

        fun setToolbarTitle(title: String) {
            toolbar.title = title
            // toolbar.setSubtitle("Sub");
            //        //toolbar.setLogo(R.drawable.ic_launcher);
        }
    }
