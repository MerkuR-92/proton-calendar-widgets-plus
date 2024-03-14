package me.proton.android.calendar.presentation.main.viewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import me.proton.android.calendar.common.FETCH_FEATURE_FLAG_INTERVAL_SECONDS
import me.proton.android.calendar.common.utils.CalendarFeatureFlag
import me.proton.android.calendar.domain.Logger
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.domain.entity.UserId
import me.proton.core.featureflag.domain.FeatureFlagManager
import me.proton.core.featureflag.domain.entity.FeatureFlag
import me.proton.core.presentation.viewmodel.ViewModelResult
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class FeatureFlagViewModel @Inject constructor(
    private val accountManager: AccountManager,
    private val featureFlagManager: FeatureFlagManager,
    private val logger: Logger
) : ViewModel() {

    private val mutableState = MutableStateFlow<ViewModelResult<FeatureFlag>>(ViewModelResult.Processing)
    val state = mutableState.asStateFlow()

    var holidayCalendarFeatureFlag: LiveData<Boolean> = MutableLiveData()
    var colorPerEventFeatureFlag: LiveData<Boolean> = MutableLiveData()

    private var lastFetchMs = 0L

    fun prefetchGlobal() {
        val featureIds = CalendarFeatureFlag.values().filter { !it.isLocalFlag }.map { it.featureId }.toSet()
        featureFlagManager.prefetch(null, featureIds)
    }

    fun prefetchForCurrentUser() {
        if (System.currentTimeMillis().minus(lastFetchMs) <= TimeUnit.SECONDS.toMillis(FETCH_FEATURE_FLAG_INTERVAL_SECONDS)) return
        accountManager.getPrimaryUserId().filterNotNull().mapLatest { userId ->
            val featureIds = CalendarFeatureFlag.values().filter { !it.isLocalFlag }.map { it.featureId }.toSet()
            featureFlagManager.prefetch(userId, featureIds)
            lastFetchMs = System.currentTimeMillis()
        }.launchIn(viewModelScope)
    }

    /**
     * Use this init method to initialize the remote feature flags we want to observe.
     */
    fun initRemoteFeatureFlagsToObserve(userId: UserId) {
        // Holiday calendar feature flag
        holidayCalendarFeatureFlag = featureFlagManager.observe(
            userId,
            CalendarFeatureFlag.CalendarAndroidHoliday.featureId
        ).map {
            it?.value ?: CalendarFeatureFlag.CalendarAndroidHoliday.fallbackValue
        }.asLiveData(Dispatchers.Default)
        colorPerEventFeatureFlag = featureFlagManager.observe(
            userId,
            CalendarFeatureFlag.CalendarAndroidColorPerEvent.featureId
        ).map {
            it?.value ?: CalendarFeatureFlag.CalendarAndroidColorPerEvent.fallbackValue
        }.asLiveData(Dispatchers.Default)
    }

    private suspend fun isFeatureEnabled(calendarFeatureFlag: CalendarFeatureFlag): Boolean {
        val userId = accountManager.getPrimaryUserId().firstOrNull() ?: return false
        return featureFlagManager.getOrDefault(
            userId,
            calendarFeatureFlag.featureId,
            FeatureFlag.default(
                calendarFeatureFlag.featureId.id,
                calendarFeatureFlag.fallbackValue
            )
        ).value
    }

    private suspend fun getFeatureFlag(calendarFeatureFlag: CalendarFeatureFlag): FeatureFlag {
        val defaultFeatureFlag = FeatureFlag.default(
            calendarFeatureFlag.featureId.id,
            calendarFeatureFlag.fallbackValue
        )
        val userId = accountManager.getPrimaryUserId().firstOrNull() ?: return defaultFeatureFlag
        return featureFlagManager.getOrDefault(
            userId,
            calendarFeatureFlag.featureId,
            defaultFeatureFlag
        )
    }

    suspend fun updateFeatureFlag(calendarFeatureFlag: CalendarFeatureFlag, value: Boolean) {
        val featureFlag = getFeatureFlag(calendarFeatureFlag)
        val updatedFeatureFlag = featureFlag.copy(defaultValue = featureFlag.defaultValue, value = value)
        featureFlagManager.update(updatedFeatureFlag)
    }

    fun isHolidayCalendarEnabled(): Boolean {
        return holidayCalendarFeatureFlag.value ?: CalendarFeatureFlag.CalendarAndroidHoliday.fallbackValue
    }

    fun isColorPerEventEnabled(): Boolean {
        return colorPerEventFeatureFlag.value ?: CalendarFeatureFlag.CalendarAndroidColorPerEvent.fallbackValue
    }

    suspend fun isPlayStoreRatingEnabled(): Boolean {
        return isFeatureEnabled(CalendarFeatureFlag.RatingAndroidCalendar)
    }

    suspend fun isServerDownBannerEnabled(): Boolean {
        return isFeatureEnabled(CalendarFeatureFlag.CalendarAndroidServerDownBanner)
    }

    suspend fun reportPlayStoreRatingFlowStarted() {
        updateFeatureFlag(CalendarFeatureFlag.RatingAndroidCalendar, false)
    }
}
