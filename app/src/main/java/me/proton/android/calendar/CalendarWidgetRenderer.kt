package me.proton.android.calendar

import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.RemoteViews
import me.proton.android.calendar.common.Navigation
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.formatDayOfWeekMedium
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.formatMonth
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.getLocaleForFormatting
import me.proton.android.calendar.presentation.main.MainActivity
import me.proton.android.calendar.presentation.main.viewModel.MainViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle

object CalendarWidgetRenderer {

    internal fun WidgetRenderState.render(
        context: Context,
        appWidgetId: Int,
        type: WidgetType = WidgetType.MonthAgenda
    ): RemoteViews {
        return when (type) {
            WidgetType.Agenda -> renderOriginal(context, appWidgetId)
            WidgetType.MonthAgenda -> renderCustom(context, appWidgetId)
            WidgetType.MonthOnly -> renderMonthOnly(context, appWidgetId)
        }
    }

    enum class WidgetType {
        Agenda, MonthAgenda, MonthOnly
    }

    private fun WidgetRenderState.renderMonthOnly(
        context: Context,
        appWidgetId: Int
    ): RemoteViews {
        val state = this
        val rv = RemoteViews(BuildConfig.APPLICATION_ID, R.layout.calendar_widget_month)

        val now = LocalDate.now()
        val locale = getLocaleForFormatting()

        // Month Name
        val monthName = now.formatMonth(true)
        rv.setTextViewText(R.id.tv_month_year, monthName)

        // Week Headers
        val weekDayIds = intArrayOf(
            R.id.week_day_0, R.id.week_day_1, R.id.week_day_2, R.id.week_day_3,
            R.id.week_day_4, R.id.week_day_5, R.id.week_day_6
        )
        val firstDayOfWeek = state.firstDayOfWeek

        val colorNorm = context.getColor(R.color.text_norm)
        val colorWeekend = context.getColor(R.color.strawberry_base)
        val colorToday = context.getColor(R.color.purple_base)
        val todayOfWeek = now.dayOfWeek

        for (i in 0..6) {
            val dayOfWeek = firstDayOfWeek.plus(i.toLong())
            val label = dayOfWeek.getDisplayName(TextStyle.NARROW, locale).uppercase(locale)
            val dayId = weekDayIds[i]

            if (dayOfWeek == todayOfWeek) {
                val spannable = SpannableString(label)
                spannable.setSpan(StyleSpan(Typeface.BOLD), 0, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                rv.setTextViewText(dayId, spannable)
            } else {
                rv.setTextViewText(dayId, label)
            }

            if (dayOfWeek == DayOfWeek.SUNDAY) {
                rv.setTextColor(dayId, colorWeekend)
            } else if (dayOfWeek == todayOfWeek) {
                rv.setTextColor(dayId, colorToday)
            } else {
                rv.setTextColor(dayId, colorNorm)
            }
        }

        // Calendar Grid
        val dayIds = intArrayOf(
            R.id.day_0, R.id.day_1, R.id.day_2, R.id.day_3, R.id.day_4, R.id.day_5, R.id.day_6,
            R.id.day_7, R.id.day_8, R.id.day_9, R.id.day_10, R.id.day_11, R.id.day_12, R.id.day_13,
            R.id.day_14, R.id.day_15, R.id.day_16, R.id.day_17, R.id.day_18, R.id.day_19, R.id.day_20,
            R.id.day_21, R.id.day_22, R.id.day_23, R.id.day_24, R.id.day_25, R.id.day_26, R.id.day_27,
            R.id.day_28, R.id.day_29, R.id.day_30, R.id.day_31, R.id.day_32, R.id.day_33, R.id.day_34,
            R.id.day_35, R.id.day_36, R.id.day_37, R.id.day_38, R.id.day_39, R.id.day_40, R.id.day_41
        )

        val firstOfMonth = now.withDayOfMonth(1)
        val offset = (firstOfMonth.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
        val startOfGrid = firstOfMonth.minusDays(offset.toLong())

        var showSixthRow = false

        for (i in 0..41) {
            val date = startOfGrid.plusDays(i.toLong())
            val dayId = dayIds[i]

            if (date.month == now.month && date.year == now.year) {
                rv.setTextViewText(dayId, date.dayOfMonth.toString())
                if (i >= 35) showSixthRow = true

                if (date == now) {
                    val bgRes = if (date.dayOfWeek == DayOfWeek.SUNDAY) {
                        R.drawable.shape_widget_day_today_rounded_weekend
                    } else {
                        R.drawable.shape_widget_day_today_rounded
                    }
                    rv.setInt(dayId, "setBackgroundResource", bgRes)
                    rv.setTextColor(dayId, Color.WHITE)
                } else {
                    rv.setInt(dayId, "setBackgroundResource", 0)
                    if (date.dayOfWeek == DayOfWeek.SUNDAY) {
                        rv.setTextColor(dayId, colorWeekend)
                    } else {
                        rv.setTextColor(dayId, colorNorm)
                    }
                }
            } else {
                rv.setTextViewText(dayId, "")
                rv.setInt(dayId, "setBackgroundResource", 0)
            }
        }

        rv.setViewVisibility(R.id.ll_widget_week_5, if (showSixthRow) View.VISIBLE else View.GONE)

        // click intent to open app
        val onClickIntent = PendingIntent.getActivity(context,
            openRequestCode(appWidgetId),
            createOpenAppIntent(context),
            activityFlags(),
        )
        rv.setOnClickPendingIntent(R.id.ll_calendar_container, onClickIntent)

        return rv
    }

    private fun WidgetRenderState.renderOriginal(
        context: Context,
        appWidgetId: Int
    ): RemoteViews {
        val state = this
        val rv = RemoteViews(BuildConfig.APPLICATION_ID, R.layout.calendar_widget_agenda)

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
        rv.setOnClickPendingIntent(R.id.rl_header_container, onClickIntent)
        rv.setOnClickPendingIntent(R.id.tv_widget_main_info_text, onClickIntent)

        // refresh
        rv.setOnClickPendingIntent(
            R.id.ib_refresh,
            PendingIntent.getBroadcast(
                context,
                refreshRequestCode(appWidgetId),
                createWidgetRefreshIntent(context, appWidgetId, WidgetType.Agenda),
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

        val svcIntent = Intent(context, CalendarAgendaWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse(
                "protoncalendar://widget/events/agenda/$appWidgetId" +
                        "?ck=${WidgetConfigKey.current(context)}"
            )
        }
        rv.setRemoteAdapter(R.id.lv_widget, svcIntent)
        rv.setPendingIntentTemplate(R.id.lv_widget, createPendingIntentTemplate(context, appWidgetId))

        return rv
    }

    private fun WidgetRenderState.renderCustom(
        context: Context,
        appWidgetId: Int,
    ): RemoteViews {
        val state = this
        val rv = RemoteViews(BuildConfig.APPLICATION_ID, R.layout.calendar_widget_month_agenda)
        
        val now = LocalDate.now()
        val locale = getLocaleForFormatting()
        
        // Large Date
        val dayOfWeekShort = now.formatDayOfWeekMedium()
        val dayOfMonth = now.dayOfMonth.toString()
        
        val fullText = "$dayOfWeekShort $dayOfMonth"
        val spannableDate = SpannableString(fullText)
        
        val start = 0
        val end = dayOfWeekShort.length
        val colorWeak = context.getColor(R.color.text_weak)
        spannableDate.setSpan(ForegroundColorSpan(colorWeak), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        
        rv.setTextViewText(R.id.tv_large_date_day, spannableDate)
        // tv_large_date_month is GONE in XML
        
        // Month Name
        val monthName = now.formatMonth(true)
        rv.setTextViewText(R.id.tv_month_year, monthName)
        
        // Week Headers
        val weekDayIds = intArrayOf(
            R.id.week_day_0, R.id.week_day_1, R.id.week_day_2, R.id.week_day_3,
            R.id.week_day_4, R.id.week_day_5, R.id.week_day_6
        )
        val firstDayOfWeek = state.firstDayOfWeek
        
        val colorNorm = context.getColor(R.color.text_norm)
        val colorWeekend = context.getColor(R.color.strawberry_base)
        val colorToday = context.getColor(R.color.purple_base)
        val todayOfWeek = now.dayOfWeek

        for (i in 0..6) {
            val dayOfWeek = firstDayOfWeek.plus(i.toLong())
            val label = dayOfWeek.getDisplayName(TextStyle.NARROW, locale).uppercase(locale)
            val dayId = weekDayIds[i]
            
            if (dayOfWeek == todayOfWeek) {
                val spannable = SpannableString(label)
                spannable.setSpan(StyleSpan(Typeface.BOLD), 0, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                rv.setTextViewText(dayId, spannable)
            } else {
                rv.setTextViewText(dayId, label)
            }

            if (dayOfWeek == DayOfWeek.SUNDAY) {
                rv.setTextColor(dayId, colorWeekend)
            } else if (dayOfWeek == todayOfWeek) {
                rv.setTextColor(dayId, colorToday)
            } else {
                rv.setTextColor(dayId, colorNorm)
            }
        }

        // Calendar Grid
        val dayIds = intArrayOf(
            R.id.day_0, R.id.day_1, R.id.day_2, R.id.day_3, R.id.day_4, R.id.day_5, R.id.day_6,
            R.id.day_7, R.id.day_8, R.id.day_9, R.id.day_10, R.id.day_11, R.id.day_12, R.id.day_13,
            R.id.day_14, R.id.day_15, R.id.day_16, R.id.day_17, R.id.day_18, R.id.day_19, R.id.day_20,
            R.id.day_21, R.id.day_22, R.id.day_23, R.id.day_24, R.id.day_25, R.id.day_26, R.id.day_27,
            R.id.day_28, R.id.day_29, R.id.day_30, R.id.day_31, R.id.day_32, R.id.day_33, R.id.day_34,
            R.id.day_35, R.id.day_36, R.id.day_37, R.id.day_38, R.id.day_39, R.id.day_40, R.id.day_41
        )
        
        val firstOfMonth = now.withDayOfMonth(1)
        val offset = (firstOfMonth.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
        val startOfGrid = firstOfMonth.minusDays(offset.toLong())
        
        var showSixthRow = false
        
        for (i in 0..41) {
            val date = startOfGrid.plusDays(i.toLong())
            val dayId = dayIds[i]
            
            if (date.month == now.month && date.year == now.year) {
                rv.setTextViewText(dayId, date.dayOfMonth.toString())
                if (i >= 35) showSixthRow = true
                
                if (date == now) {
                    val bgRes = if (date.dayOfWeek == DayOfWeek.SUNDAY) {
                        R.drawable.shape_widget_day_today_rounded_weekend
                    } else {
                        R.drawable.shape_widget_day_today_rounded
                    }
                    rv.setInt(dayId, "setBackgroundResource", bgRes)
                    rv.setTextColor(dayId, Color.WHITE)
                } else {
                    rv.setInt(dayId, "setBackgroundResource", 0)
                    if (date.dayOfWeek == DayOfWeek.SUNDAY) {
                        rv.setTextColor(dayId, colorWeekend)
                    } else {
                        rv.setTextColor(dayId, colorNorm)
                    }
                }
            } else {
                rv.setTextViewText(dayId, "")
                rv.setInt(dayId, "setBackgroundResource", 0)
            }
        }
        
        rv.setViewVisibility(R.id.ll_widget_week_5, if (showSixthRow) View.VISIBLE else View.GONE)

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
        // Bind click to headers and calendar container
        rv.setOnClickPendingIntent(R.id.ll_calendar_container, onClickIntent)
        rv.setOnClickPendingIntent(R.id.ll_large_date_container, onClickIntent)
        
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
                createWidgetRefreshIntent(context, appWidgetId, WidgetType.MonthAgenda),
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
        val svcIntent = Intent(context, CalendarMonthAgendaWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse(
                "protoncalendar://widget/events/month/$appWidgetId" +
                        "?ck=${WidgetConfigKey.current(context)}"
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
        data = Navigation.Deeplink.toMonth(LocalDate.now())
        action = MainViewModel.INTENT_ACTION_SHOW_DAY
        addCategory(Intent.CATEGORY_LAUNCHER)
    }

    private fun createNewEventIntent(context: Context) = Intent(context, MainActivity::class.java).apply {
        action = MainViewModel.INTENT_ACTION_NEW_EVENT
        addCategory(Intent.CATEGORY_LAUNCHER)
    }

    private fun createWidgetRefreshIntent(context: Context, appWidgetId: Int, type: WidgetType): Intent {
        val mgr = AppWidgetManager.getInstance(context)
        val providerClass = when (type) {
            WidgetType.Agenda -> CalendarAgendaWidget::class.java
            WidgetType.MonthAgenda -> CalendarMonthAgendaWidget::class.java
            WidgetType.MonthOnly -> CalendarMonthWidget::class.java
        }
        val ids = mgr.getAppWidgetIds(ComponentName(context, providerClass))

        return Intent(context, providerClass).apply {
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
