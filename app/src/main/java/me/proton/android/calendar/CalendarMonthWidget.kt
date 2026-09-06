package me.proton.android.calendar

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import org.koin.core.KoinComponent
import org.koin.core.inject

class CalendarMonthWidget : AppWidgetProvider(), KoinComponent {

    private val coordinator: CalendarWidgetUpdateCoordinator by inject()

    override fun onReceive(context: Context, intent: Intent?) {
        super.onReceive(context, intent)

        val action = intent?.action

        if (action == ACTION_WIDGET_REFRESH_AFTER_LOGIN) {
            coordinator.requestRefreshAfterLogin()
            return
        }

        if (action == ACTION_WIDGET_DEFERRED_REFRESH) {
            coordinator.requestDeferredRefresh()
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
}
