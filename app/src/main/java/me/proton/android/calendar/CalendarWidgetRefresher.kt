package me.proton.android.calendar

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import me.proton.android.calendar.CalendarWidgetRenderer.WidgetType
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
class AppWidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context
) : WidgetRefresher {

    override fun broadcastRefresh() {
        broadcast(AppWidgetManager.ACTION_APPWIDGET_UPDATE, WidgetType.MonthAgenda)
        broadcast(AppWidgetManager.ACTION_APPWIDGET_UPDATE, WidgetType.Agenda)
        broadcast(AppWidgetManager.ACTION_APPWIDGET_UPDATE, WidgetType.MonthOnly)
    }

    override fun broadcastPostLoginRefresh() {
        broadcast(ACTION_WIDGET_REFRESH_AFTER_LOGIN, WidgetType.MonthAgenda)
        broadcast(ACTION_WIDGET_REFRESH_AFTER_LOGIN, WidgetType.Agenda)
        broadcast(ACTION_WIDGET_REFRESH_AFTER_LOGIN, WidgetType.MonthOnly)
    }

    override fun refreshEventList() {
        broadcast(ACTION_WIDGET_DEFERRED_REFRESH, WidgetType.MonthAgenda)
        broadcast(ACTION_WIDGET_DEFERRED_REFRESH, WidgetType.Agenda)
        broadcast(ACTION_WIDGET_DEFERRED_REFRESH, WidgetType.MonthOnly)
    }

    private fun broadcast(intentAction: String, widgetType: WidgetType) {
        val mgr = AppWidgetManager.getInstance(context)
        val providerClass = when (widgetType) {
            WidgetType.Agenda -> CalendarAgendaWidget::class.java
            WidgetType.MonthAgenda -> CalendarMonthAgendaWidget::class.java
            WidgetType.MonthOnly -> CalendarMonthWidget::class.java
        }
        val ids = mgr.getAppWidgetIds(ComponentName(context, providerClass))

        context.sendBroadcast(
            Intent(context, providerClass).apply {
                action = intentAction
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                data = Uri.parse("protoncalendar://widget/refresh/full")
            }
        )
    }
}

const val ACTION_WIDGET_REFRESH_AFTER_LOGIN =
    "me.proton.android.calendar.action.WIDGET_REFRESH_AFTER_LOGIN"

const val ACTION_WIDGET_DEFERRED_REFRESH =
    "me.proton.android.calendar.action.WIDGET_DEFERRED_REFRESH"
