package me.proton.android.calendar

import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import me.proton.android.calendar.common.Navigation
import me.proton.android.calendar.presentation.main.MainActivity
import me.proton.android.calendar.presentation.main.viewModel.MainViewModel

object CalendarWidgetRenderer {

    internal fun WidgetRenderState.render(
        context: Context,
        appWidgetId: Int,
    ): RemoteViews {
        val state = this
        val rv = RemoteViews(BuildConfig.APPLICATION_ID, R.layout.calendar_widget)
        // date
        rv.setTextViewText(R.id.tv_header_text, state.headerDayOfWeek)
        rv.setTextViewText(R.id.tv_main_text, state.headerMonthAndDay)
        // refresh + add visibility
        rv.setViewVisibility(R.id.ib_plus, if (state.showButtons) View.VISIBLE else View.INVISIBLE)
        rv.setViewVisibility(R.id.ib_refresh, if (state.showButtons) View.VISIBLE else View.INVISIBLE)
        // status/empty text
        rv.setEmptyView(R.id.lv_widget, R.id.tv_widget_main_info_text)
        rv.setTextViewText(R.id.tv_widget_main_info_text, state.infoText ?: "")

        // click intents
        val onClickIntent = PendingIntent.getActivity(context,
            openRequestCode(appWidgetId),
            createOpenAppIntent(context),
            activityFlags(),
        )
        rv.setOnClickPendingIntent(
            R.id.rl_header_container,
            onClickIntent,
        )
        rv.setOnClickPendingIntent(
            R.id.tv_widget_main_info_text,
            onClickIntent,
        )
        // refresh
        rv.setOnClickPendingIntent(
            R.id.ib_refresh,
            PendingIntent.getBroadcast(
                context,
                refreshRequestCode(appWidgetId),
                createWidgetRefreshIntent(context, appWidgetId),
                broadcastFlags()
            )
        )
        // add
        rv.setOnClickPendingIntent(
            R.id.ib_plus,
            PendingIntent.getActivity(
                context,
                plusRequestCode(appWidgetId),
                createNewEventIntent(context).apply {
                    data = Uri.parse("protoncalendar://widget/new/$appWidgetId")
                },
                activityFlags()
            )
        )

        val adapterVersion = state.adapterVersion
        val svcIntent = Intent(context, CalendarWidgetRemoteViewsService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse(
                "protoncalendar://widget/events/$appWidgetId" +
                        "?ck=${WidgetConfigKey.current(context)}" +
                        "&v=$adapterVersion" // force a new remoteviewsfactory when data chaneges
            )
        }
        rv.setRemoteAdapter(R.id.lv_widget, svcIntent)

        rv.setPendingIntentTemplate(R.id.lv_widget, createPendingIntentTemplate(context, appWidgetId))

        return rv
    }

    private fun createPendingIntentTemplate(context: Context, appWidgetId: Int): PendingIntent {
        val template = Intent(context, MainActivity::class.java)
        val flags =
            PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE
                    else 0

        return TaskStackBuilder.create(context)
            .addNextIntentWithParentStack(template)
            .getPendingIntent(listTemplateRequestCode(appWidgetId), flags)
    }

    private fun createOpenAppIntent(context: Context) = Intent(context, MainActivity::class.java).apply {
        data = Navigation.Deeplink.toMonth(java.time.LocalDate.now())
        action = MainViewModel.INTENT_ACTION_SHOW_DAY
        addCategory(Intent.CATEGORY_LAUNCHER)
    }

    private fun createNewEventIntent(context: Context) = Intent(context, MainActivity::class.java).apply {
        action = MainViewModel.INTENT_ACTION_NEW_EVENT
        addCategory(Intent.CATEGORY_LAUNCHER)
    }

    private fun createWidgetRefreshIntent(context: Context, appWidgetId: Int): Intent {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, CalendarWidget::class.java))

        return Intent(context, CalendarWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            data = Uri.parse("protoncalendar://widget/refresh/$appWidgetId") // make intent identity unique
        }
    }

    private fun activityFlags(): Int = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun broadcastFlags(): Int = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
}

// ensure intent codes are different per widget and per type (increment)
private fun refreshRequestCode(appWidgetId: Int) = perWidgetRequestCode(appWidgetId, 1)
private fun plusRequestCode(appWidgetId: Int) = perWidgetRequestCode(appWidgetId, 2)
private fun openRequestCode(appWidgetId: Int) = perWidgetRequestCode(appWidgetId, 3)
private fun listTemplateRequestCode(appWidgetId: Int) = perWidgetRequestCode(appWidgetId,  4)

private fun perWidgetRequestCode(appWidgetId: Int, increment: Int) = (appWidgetId * 10) + increment