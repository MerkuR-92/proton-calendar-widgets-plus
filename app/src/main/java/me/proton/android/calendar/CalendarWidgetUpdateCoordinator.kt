package me.proton.android.calendar

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.text.format.DateFormat
import android.text.format.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.proton.android.calendar.CalendarWidgetRenderer.render
import me.proton.android.calendar.common.getUserSettingsEntity
import me.proton.android.calendar.common.provider.ResourceProviderImpl
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.formatDayOfWeek
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.toDate
import me.proton.android.calendar.common.utils.getAddressesOrNull
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ResourceProvider
import me.proton.android.calendar.domain.model.UiEvent
import me.proton.android.calendar.domain.usecase.GetUiEventsUseCase
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.accountmanager.domain.getPrimaryAccount
import me.proton.core.user.domain.UserAddressManager
import me.proton.core.usersettings.domain.repository.UserSettingsRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Koin-managed singleton for coordinating widget updates
 */
@OptIn(FlowPreview::class)
class CalendarWidgetUpdateCoordinator(
    private val appContext: Context,
    private val cache: WidgetContentCache,
    private val accountManager: AccountManager,
    private val userAddressManager: UserAddressManager,
    private val userSettingsRepository: UserSettingsRepository,
    private val database: AppDatabase,
    private val getUiEventsUseCase: GetUiEventsUseCase,
    connectivityManager: ConnectivityManager?,
) {
    private data object RefreshRequest

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val requests = MutableSharedFlow<RefreshRequest>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val deferredRequests = MutableSharedFlow<RefreshRequest>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private var postLoginJob: Job? = null

    init {
        scope.launch {
            requests.debounce(500).collectLatest {
                retrieveDataAndNotify()
            }
        }

        scope.launch {
            deferredRequests.debounce(30.seconds).collect {
                requests.tryEmit(RefreshRequest)
            }
        }

        connectivityManager?.registerNetworkCallback(
            NetworkRequest.Builder().build(),
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    requestDeferredRefresh()
                }
            }
        )
    }
    
    // to avoid requesting a redundant refresh that would cancel the in-progress one
    @Volatile
    var isRetrieving: Boolean = false
        private set

    fun requestRefresh() {
        requests.tryEmit(RefreshRequest)
    }

    fun requestRefreshIfIdle() {
        if (isRetrieving) {
            return
        }
        requests.tryEmit(RefreshRequest)
    }

    fun requestDeferredRefresh() {
        deferredRequests.tryEmit(RefreshRequest)
    }

    fun requestRefreshAfterLogin() {
        requests.tryEmit(RefreshRequest)  // refresh with whatever we have

        postLoginJob?.cancel()
        postLoginJob = scope.launch {
            val userId = accountManager.getPrimaryAccount().firstOrNull()?.userId
            if (userId == null) {
                // fallback to delayed refresh if no user
                delay(10.seconds)
                requests.tryEmit(RefreshRequest)
                return@launch
            }
            // refresh upon count changes for a short while to let the login loads settle
            withTimeoutOrNull(2.minutes) {
                database.eventOccurrencesDao()
                    .countForUser(userId.id)
                    .debounce(5.seconds)
                    .distinctUntilChanged()
                    .take(10)
                    .collect {
                        requests.tryEmit(RefreshRequest)
                    }
            }
        }
    }

    // a single widget got removed - just clear its cache
    fun onWidgetRemoved(appWidgetId: Int) {
        cache.remove(appWidgetId)
    }

    // the last widget got removed - clear cache,
    // but keep the main scope running because this singleton will be reused the next time the user adds a new widget
    fun onDisabled() {
        cache.removeAll()
        postLoginJob?.cancel()
        postLoginJob = null
    }

    private var emittedLoadingState: WidgetRenderState? = null

    // ensure locale and theme changes are reflected in the widget
    private fun createFreshContext(): Context {
        val config = appContext.resources.configuration
        return appContext.createConfigurationContext(config)
    }

    private suspend fun retrieveDataAndNotify() {
        isRetrieving = true
        try {
            retrieveDataAndNotifyImpl()
        } finally {
            isRetrieving = false
        }
    }

    private suspend fun retrieveDataAndNotifyImpl() {
        val ctx = createFreshContext()
        val resourceProvider = ResourceProviderImpl(ctx.resources)
        val mgr = AppWidgetManager.getInstance(ctx)
        
        val monthAgendaIds = mgr.getAppWidgetIds(ComponentName(ctx, CalendarMonthAgendaWidget::class.java))
        val agendaIds = mgr.getAppWidgetIds(ComponentName(ctx, CalendarAgendaWidget::class.java))
        val monthOnlyIds = mgr.getAppWidgetIds(ComponentName(ctx, CalendarMonthWidget::class.java))
        
        if (monthAgendaIds.isEmpty() && agendaIds.isEmpty() && monthOnlyIds.isEmpty()) return

        val ck = WidgetConfigKey.current(ctx)

        val now = LocalDate.now(ZoneId.systemDefault())
        val headerDayOfWeek = now.formatDayOfWeek()
        val monthAndDay = DateUtils.formatDateTime(
            ctx, now.toDate(ZoneId.systemDefault().id).time,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR
        )

        val account = accountManager.getPrimaryAccount().firstOrNull()
        val firstDayOfWeek = account?.userId?.let {
            userSettingsRepository.getUserSettingsEntity(it, database).weekStartDayOfWeek()
        } ?: WeekFields.of(Locale.getDefault()).firstDayOfWeek

        val loadingState = WidgetRenderState(
            headerDayOfWeek = headerDayOfWeek,
            headerMonthAndDay = monthAndDay,
            infoText = resourceProvider.provideString(R.string.calendar_widget_loading_events),
            showButtons = false,
            firstDayOfWeek = firstDayOfWeek
        )
        if (loadingState != emittedLoadingState) {
            monthAgendaIds.forEach { id -> mgr.updateAppWidget(id, loadingState.render(ctx, id, CalendarWidgetRenderer.WidgetType.MonthAgenda)) }
            agendaIds.forEach { id -> mgr.updateAppWidget(id, loadingState.render(ctx, id, CalendarWidgetRenderer.WidgetType.Agenda)) }
            monthOnlyIds.forEach { id -> mgr.updateAppWidget(id, loadingState.render(ctx, id, CalendarWidgetRenderer.WidgetType.MonthOnly)) }
            
            if (monthAgendaIds.isNotEmpty()) mgr.notifyAppWidgetViewDataChanged(monthAgendaIds, R.id.lv_widget)
            if (agendaIds.isNotEmpty()) mgr.notifyAppWidgetViewDataChanged(agendaIds, R.id.lv_widget)
            
            emittedLoadingState = loadingState
        }

        val content = loadEvents(
            context = ctx,
            resourceProvider = resourceProvider,
            ck = ck,
            headerDayOfWeek = headerDayOfWeek,
            monthAndDay = monthAndDay,
            firstDayOfWeek = firstDayOfWeek,
        )

        // populate caches and notify
        monthAgendaIds.forEach { id -> cache.put(id, content) }
        agendaIds.forEach { id -> cache.put(id, content) }
        monthOnlyIds.forEach { id -> cache.put(id, content) }
        
        val finalState = content.renderState.copy(adapterVersion = System.currentTimeMillis())
        monthAgendaIds.forEach { id -> mgr.updateAppWidget(id, finalState.render(ctx, id, CalendarWidgetRenderer.WidgetType.MonthAgenda)) }
        agendaIds.forEach { id -> mgr.updateAppWidget(id, finalState.render(ctx, id, CalendarWidgetRenderer.WidgetType.Agenda)) }
        monthOnlyIds.forEach { id -> mgr.updateAppWidget(id, finalState.render(ctx, id, CalendarWidgetRenderer.WidgetType.MonthOnly)) }
        
        if (monthAgendaIds.isNotEmpty()) mgr.notifyAppWidgetViewDataChanged(monthAgendaIds, R.id.lv_widget)
        if (agendaIds.isNotEmpty()) mgr.notifyAppWidgetViewDataChanged(agendaIds, R.id.lv_widget)
    }

    private suspend fun loadEvents(
        context: Context,
        resourceProvider: ResourceProvider,
        ck: String,
        headerDayOfWeek: String,
        monthAndDay: String,
        firstDayOfWeek: DayOfWeek,
    ): WidgetContent {
        val zoneId = ZoneId.systemDefault()
        val fromDate = LocalDate.now(zoneId)
        val toDate = fromDate.plusDays(CalendarMonthAgendaWidget.WIDGET_DAYS_AHEAD.toLong())

        val account = accountManager.getPrimaryAccount().firstOrNull()
        val userId = account?.userId
        if (userId == null) {
            return WidgetContent(
                events = emptyList(),
                renderState = WidgetRenderState(
                    headerDayOfWeek = headerDayOfWeek,
                    headerMonthAndDay = monthAndDay,
                    infoText = resourceProvider.provideString(R.string.calendar_widget_please_log_in),
                    showButtons = false,
                    firstDayOfWeek = firstDayOfWeek,
                ),
                configKey = ck
            )
        }

        val emails = userAddressManager.getAddressesOrNull(userId)?.map { it.email }.orEmpty()
        if (emails.isEmpty()) {
            return WidgetContent(
                events = emptyList(),
                renderState = WidgetRenderState(
                    headerDayOfWeek = headerDayOfWeek,
                    headerMonthAndDay = monthAndDay,
                    infoText = resourceProvider.provideString(R.string.calendar_widget_please_log_in),
                    showButtons = false,
                    firstDayOfWeek = firstDayOfWeek,
                ),
                configKey = ck
            )
        }

        val userSettings = userSettingsRepository.getUserSettingsEntity(userId, database)
        val is24h = userSettings.timeFormatIs24Hour(DateFormat.is24HourFormat(context))

        val res = withTimeoutOrNull(1.minutes) {
            getUiEventsUseCase.execute(userId, fromDate, toDate, zoneId.id)
                .firstOrNull { it !is CalendarsRepository.GetEventsResult.Success || !it.isSkeleton }
        }

        if (res == null) {
            return WidgetContent(
                events = emptyList(),
                renderState = WidgetRenderState(
                    headerDayOfWeek = headerDayOfWeek,
                    headerMonthAndDay = monthAndDay,
                    infoText = resourceProvider.provideString(R.string.calendar_widget_loading_events_error),
                    showButtons = true,
                    firstDayOfWeek = firstDayOfWeek,
                ),
                configKey = ck
            )
        }

        return when (res) {
            is CalendarsRepository.GetEventsResult.Exception -> WidgetContent(
                events = emptyList(),
                renderState = WidgetRenderState(
                    headerDayOfWeek = headerDayOfWeek,
                    headerMonthAndDay = monthAndDay,
                    infoText = resourceProvider.provideString(R.string.calendar_widget_loading_events_error),
                    showButtons = true,
                    firstDayOfWeek = firstDayOfWeek,
                ),
                configKey = ck
            )

            CalendarsRepository.GetEventsResult.InProgress -> {
                WidgetContent(
                    events = emptyList(),
                    renderState = WidgetRenderState(
                        headerDayOfWeek = headerDayOfWeek,
                        headerMonthAndDay = monthAndDay,
                        infoText = resourceProvider.provideString(R.string.calendar_widget_loading_events),
                        showButtons = true,
                        firstDayOfWeek = firstDayOfWeek,
                    ),
                    configKey = ck
                )
            }

            is CalendarsRepository.GetEventsResult.Success<UiEvent> -> {
                val snapshot = res.events.toWidgetSnapshot(
                    resourceProvider = resourceProvider,
                    fromDate = fromDate,
                    toDate = toDate,
                    zoneId = zoneId,
                    is24Hour = is24h,
                )
                WidgetContent(
                    events = snapshot.events,
                    renderState = WidgetRenderState(
                        headerDayOfWeek = headerDayOfWeek,
                        headerMonthAndDay = monthAndDay,
                        infoText = snapshot.statusText,
                        showButtons = snapshot.showButtons,
                        firstDayOfWeek = firstDayOfWeek,
                    ),
                    configKey = ck
                )
            }
        }
    }
}
