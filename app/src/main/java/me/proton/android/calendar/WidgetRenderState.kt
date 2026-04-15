package me.proton.android.calendar

import android.content.Context
import android.text.format.DateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class WidgetRenderState(
    val headerDayOfWeek: String,
    val headerMonthAndDay: String,
    val infoText: String?,
    val showButtons: Boolean,
    val adapterVersion: Long = System.currentTimeMillis(),
)

data class WidgetContent(
    val events: List<WidgetEvent>,
    val renderState: WidgetRenderState,
    val configKey: String,
)

interface WidgetContentCache {
    fun get(appWidgetId: Int): WidgetContent?
    fun put(appWidgetId: Int, content: WidgetContent)
    fun remove(appWidgetId: Int)
    fun removeAll()
}

internal class InMemoryWidgetContentCache : WidgetContentCache {
    private val map = ConcurrentHashMap<Int, WidgetContent>()
    override fun get(appWidgetId: Int) = map[appWidgetId]
    override fun put(appWidgetId: Int, content: WidgetContent) { map[appWidgetId] = content }
    override fun remove(appWidgetId: Int) { map.remove(appWidgetId) }
    override fun removeAll() { map.clear() }
}

object WidgetConfigKey {
    // capture context changes that should result in a widget update
    fun current(context: Context): String {
        val is24h = DateFormat.is24HourFormat(context)
        val lang = Locale.getDefault().toLanguageTag()
        val tz = ZoneId.systemDefault().id
        val nightMode = context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val date = LocalDate.now(ZoneId.systemDefault()).toString()
        return "$lang|$tz|$is24h|$nightMode|$date"
    }
}
