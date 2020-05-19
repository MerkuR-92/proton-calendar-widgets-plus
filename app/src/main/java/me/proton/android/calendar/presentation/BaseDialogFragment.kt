package me.proton.android.calendar.presentation

import android.app.Dialog
import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.DialogFragment
import me.proton.android.calendar.R


abstract class BaseDialogFragment : DialogFragment() {

    // properties for subclasses to override
    abstract val TAG: String
    abstract val layoutResourceId: Int
    //    abstract val showToolbar: Boolean

    protected open val navigateUp: Boolean = true
    protected open val actionMenuResourceId: Int? = null
    protected open fun onMenuItemClicked(menuItem: MenuItem) {}
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
//        isCancelable = false



                val rootView = inflater.inflate(R.layout.fragment_base_dialog, container, false)
                rootView.findViewById<ViewGroup>(R.id.base_fragment_container).addView(
                    inflater.inflate(layoutResourceId, container, false)
                )

                toolbar = rootView.findViewById<Toolbar>(R.id.base_fragment_toolbar)

                toolbar.apply {
                    setTitle("")
                    // navigation
                    if (navigateUp) {
//                val icon = resources.getDrawable(R.drawable.ic_arrow_left)
//                icon.setTint(resources.getColor(R.color.iconTint)) // doesn't work
                        setNavigationIcon(R.drawable.ic_arrow_left)
                    } else {
                        setNavigationIcon(R.drawable.ic_times)
                    }
                    setNavigationOnClickListener { // TODO make sure we shouldn't clear this embedded dialog-stack
                        dismiss()
                        // TODO navigate up or close
                    }
                    // action menu
                    actionMenuResourceId?.let {
                        inflateMenu(it)
                        setOnMenuItemClickListener {
                            onMenuItemClicked(it)
                            return@setOnMenuItemClickListener true
                        }
                    }
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
