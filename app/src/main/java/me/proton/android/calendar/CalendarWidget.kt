package me.proton.android.calendar

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import dagger.hilt.android.qualifiers.ApplicationContext
import me.proton.android.calendar.common.Navigation
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.formatDayOfWeekMedium
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.presentation.main.viewModel.MainViewModel
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

interface WidgetRefresher {
    /**
     * Sends a broadcast to force-refresh all the app-widgets.
     */
    fun broadcastRefresh()

    /**
     * A special broadcast to let the widget know to observe post-login events for a short while.
     */
    fun broadcastPostLoginRefresh()

    /**
     * Refreshes all Event lists in all Widgets using AppWidgetManager (without broadcast).
     */
    fun refreshEventList()
}

@Singleton
class CalendarWidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context
) : WidgetRefresher {

    override fun broadcastRefresh() {
        broadcast(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
    }

    override fun broadcastPostLoginRefresh() {
        broadcast(ACTION_WIDGET_REFRESH_AFTER_LOGIN)
    }

    private fun broadcast(intentAction: String) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, CalendarWidget::class.java))

        context.sendBroadcast(
            Intent(context, CalendarWidget::class.java).apply {
                action = intentAction
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                data = Uri.parse("protoncalendar://widget/refresh/full")
            }
        )
    }

    override fun refreshEventList() {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, CalendarWidget::class.java))
        mgr.notifyAppWidgetViewDataChanged(ids, R.id.lv_widget)
    }
}

private const val ACTION_WIDGET_REFRESH_AFTER_LOGIN =
    "me.proton.android.calendar.action.WIDGET_REFRESH_AFTER_LOGIN"

class CalendarWidget : AppWidgetProvider(), KoinComponent {

    private val coordinator: CalendarWidgetUpdateCoordinator by inject()

    override fun onReceive(context: Context, intent: Intent?) {
        super.onReceive(context, intent)

        val action = intent?.action

        if (action == ACTION_WIDGET_REFRESH_AFTER_LOGIN) {
            coordinator.requestRefreshAfterLogin()
            return
        }

        val needsRefresh = action == Intent.ACTION_TIME_CHANGED ||
                action == Intent.ACTION_TIMEZONE_CHANGED ||
                action == Intent.ACTION_DATE_CHANGED ||
                action == Intent.ACTION_LOCALE_CHANGED

        if (needsRefresh) {
            coordinator.requestRefresh()
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        coordinator.requestRefresh()
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { coordinator.onWidgetRemoved(it) }
        super.onDeleted(context, appWidgetIds)
    }

    override fun onDisabled(context: Context?) {
        coordinator.onDisabled()
        super.onDisabled(context)
    }

    companion object {
        const val WIDGET_DAYS_AHEAD = 14
    }
}

internal class CalendarWidgetRemoteViewsService : RemoteViewsService(), KoinComponent {
    override fun onGetViewFactory(intent: Intent?): RemoteViewsFactory {
        val widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        return CalendarWidgetRemoteViewsFactory(widgetId)
    }
}

internal class CalendarWidgetRemoteViewsFactory(
    private val appWidgetId: Int
) : RemoteViewsService.RemoteViewsFactory, KoinComponent {

    private val cache: WidgetContentCache by inject()
    private val coordinator: CalendarWidgetUpdateCoordinator by inject()
    private val logger: Logger by inject()

    private var data: List<WidgetEvent> = emptyList()

    override fun onCreate() {}

    override fun onDestroy() {}

    override fun onDataSetChanged() {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            logger.e("Widget factory got INVALID_APPWIDGET_ID")
            data = emptyList()
            return
        }

        val cached = cache.get(appWidgetId)
        if (cached != null) {
            data = cached.events
            return
        }
        // process restarted / cache missed: request a refresh and show empty until coordinator repopulates
        coordinator.requestRefresh()
        data = emptyList()
    }

    override fun getCount(): Int = data.size

    override fun getViewAt(position: Int): RemoteViews? {
        val event = data.getOrNull(position) ?: run {
            logger.e("widget item position out of bounds: position=$position size=${data.size}")
            return null
        }

        val rv = RemoteViews(BuildConfig.APPLICATION_ID, R.layout.item_widget)

        // date column
        val showDate = event.showDateColumn
        rv.setViewVisibility(R.id.rl_event_day_container, if (showDate) View.VISIBLE else View.INVISIBLE)
        rv.setTextViewText(R.id.tv_event_day_of_week, if (showDate) event.happensOn.formatDayOfWeekMedium() else "")
        rv.setTextViewText(R.id.tv_event_day_of_month, if (showDate) event.happensOn.dayOfMonth.toString() else "")

        // headers - strikethrough flags
        val flags = if (event.isCancelledOrDeclined) {
            Paint.STRIKE_THRU_TEXT_FLAG or Paint.ANTI_ALIAS_FLAG
        } else {
            Paint.ANTI_ALIAS_FLAG
        }
        rv.setInt(R.id.tv_event_header, "setPaintFlags", flags)
        rv.setInt(R.id.tv_event_subheader, "setPaintFlags", flags)
        // headers - text
        rv.setTextViewText(R.id.tv_event_header, if (event.failedToDecrypt) "" else event.summary)
        rv.setTextViewText(
            R.id.tv_event_header_day_indicator,
            event.fullDayCounter?.let { " $it" } ?: ""
        )
        rv.setTextViewText(R.id.tv_event_subheader, event.subheaderContent)
        // decryption error UI
        rv.setViewVisibility(R.id.decryption_error_view, if (event.failedToDecrypt) View.VISIBLE else View.INVISIBLE)
        rv.setViewVisibility(R.id.decryption_error_icon, if (event.failedToDecrypt) View.VISIBLE else View.INVISIBLE)
        // no events today dummy row
        rv.setViewVisibility(R.id.rl_event_container, if (event.showNoEventsToday) View.INVISIBLE else View.VISIBLE)
        rv.setViewVisibility(R.id.tv_event_no_events_today, if (event.showNoEventsToday) View.VISIBLE else View.INVISIBLE)
        // bottom spacing
        rv.setViewVisibility(R.id.v_event_spacing, if (event.showBottomSpacing) View.VISIBLE else View.GONE)
        // calendar bar resource + tint
        rv.setImageViewResource(
            R.id.iv_calendar_bar,
            if (event.needsAction) R.drawable.ic_calendar_bar_unanswered else R.drawable.shape_calendar_bar
        )
        rv.setInt(R.id.iv_calendar_bar, "setColorFilter", Color.parseColor(event.color))

        // navigate to month on day container
        rv.setOnClickFillInIntent(
            R.id.rl_event_day_container,
            Intent().apply {
                action = MainViewModel.INTENT_ACTION_SHOW_DAY
                data = Navigation.Deeplink.toMonth(event.happensOn)
            }
        )
        // navigate to event details on event
        rv.setOnClickFillInIntent(
            R.id.rl_event_container,
            Intent().apply {
                if (event.failedToDecrypt) {
                    action = MainViewModel.INTENT_ACTION_SHOW_DAY
                    data = Navigation.Deeplink.toMonth(event.happensOn)
                } else {
                    action = MainViewModel.INTENT_ACTION_SHOW_EVENT_DETAILS
                    data = Navigation.Deeplink.toMainActivityWithEventId(event.id, event.occurrenceNumber)
                }
            }
        )
        return rv
    }

    override fun getLoadingView(): RemoteViews =
        RemoteViews(BuildConfig.APPLICATION_ID, R.layout.item_widget_loading)

    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false
}

data class WidgetEvent(
    val id: String,
    val summary: String,
    val subheaderContent: String,
    val isCancelledOrDeclined: Boolean,
    val needsAction: Boolean,
    val failedToDecrypt: Boolean,
    // LocalDate that this Event spans, not necessarily the same as dateStart
    val happensOn: LocalDate,
    val showDateColumn: Boolean,
    val showBottomSpacing: Boolean,
    val showNoEventsToday: Boolean,
    val fullDayCounter: String?,
    val occurrenceNumber: Int,
    val color: String,
)
