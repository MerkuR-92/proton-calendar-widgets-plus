package me.proton.android.calendar.presentation.account

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.lifecycle.*
import androidx.work.WorkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import me.proton.android.calendar.R
import me.proton.android.calendar.WidgetRefresher
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.usecase.*
import me.proton.core.account.domain.entity.*
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.accountmanager.presentation.*
import me.proton.core.auth.presentation.AuthOrchestrator
import me.proton.core.auth.presentation.onAddAccountResult
import me.proton.core.domain.entity.Product
import me.proton.core.domain.entity.UserId
import me.proton.core.humanverification.domain.HumanVerificationManager
import me.proton.core.humanverification.presentation.HumanVerificationOrchestrator
import me.proton.core.humanverification.presentation.observe
import me.proton.core.humanverification.presentation.onHumanVerificationNeeded

class AccountViewModel(
    private val accountManager: AccountManager,
    private val authOrchestrator: AuthOrchestrator,
    private val humanVerificationManager: HumanVerificationManager,
    private val humanVerificationOrchestrator: HumanVerificationOrchestrator,
    private val bootstrapCalendarsUseCase: BootstrapCalendarsUseCase,
    private val valueStoreProvider: ValueStoreProvider,
    private val userSettingsRepository: UserSettingsRepository,
    private val calendarsRepository: CalendarsRepository,
    private val resetCalendarsKeyUseCase: ResetCalendarsKeyUseCase,
    private val logger: Logger,
    private val product: Product,
    private val widgetRefresher: WidgetRefresher
) : ViewModel() {

    sealed class State {
        object Initial : State()
        object LoginNeeded : State()
        object StepNeeded : State()
        object Processing : State()
        object Ready : State()
    }

    private val _state = MutableStateFlow<State>(State.Initial)
    private val _hasPrimary = MutableLiveData<Boolean>()
    private val _errorReport = MutableLiveData<UseCase.Error?>()

    private var defaultCalendarName: String = "My calendar" // This value is set in init.

    private suspend fun Account.isBootstrapped() = userSettingsRepository.selectUserSettings(userId.id) != null

    private suspend fun checkAccount(account: Account) {
        runCatching {
            if (account.isBootstrapped()) return

            val valueStore = valueStoreProvider.provideValueStore(account.userId.id)
            val eventId = checkNotNull(account.details.session?.initialEventId)

            valueStore.putString(ValueKey.LAST_SERVER_EVENT_ID, eventId)

            setupUser(account.userId)
        }.onFailure {
            logger.e("checkAccount failed, removing user.", it)
            removeUser(account.userId)

            if (it is CancellationException) throw it
        }
    }

    private suspend fun setupUser(userId: UserId, showConfirmationDialog: Boolean = true) {
        _state.tryEmit(State.Processing)

        val bootstrapResult = bootstrapCalendarsUseCase.execute(userId, defaultCalendarName, showConfirmationDialog)
        bootstrapResult.ifSuccessAndLogErrors(logger) { }
        if (bootstrapResult !is UseCase.Result.Success<*>) {
            if (bootstrapResult is UseCase.Result.Error) {
                _errorReport.postValue(bootstrapResult.error)
                if (bootstrapResult.error == UseCase.Error.Bootstrap.ResetNeeded ||
                    bootstrapResult.error == UseCase.Error.Bootstrap.UpdatePassphrase
                ) return
            }
            removeUser(userId)
            return
        }

        _state.tryEmit(State.Ready)
    }

    private suspend fun removeUser(userId: UserId) {
        accountManager.removeAccount(userId)
        valueStoreProvider.provideValueStore(userId.id).clearAll()
    }

    private suspend fun cleanUser(context: Context) {
        WorkManager.getInstance(context).cancelAllWork()
        calendarsRepository.shutdown()
        widgetRefresher.refresh()
    }

    // TODO: Merge State & Error in the same StateFlow.
    val state = _state.asStateFlow()
    val errorReport: LiveData<UseCase.Error?> = _errorReport
    val hasPrimary: LiveData<Boolean> = _hasPrimary

    fun init(context: ComponentActivity) {
        // Make sure we clear error on init
        clearError()

        defaultCalendarName = context.resources.getString(R.string.default_calendar_name)

        // Account state handling.
        with(authOrchestrator) {
            register(context)
            accountManager.observe(context.lifecycle, minActiveState = Lifecycle.State.CREATED)
                .onAccountReady { checkAccount(it) }
                .onSessionSecondFactorNeeded { startSecondFactorWorkflow(it) }
                .onAccountTwoPassModeNeeded { startTwoPassModeWorkflow(it) }
                .onAccountCreateAddressNeeded { startChooseAddressWorkflow(it) }
                .onAccountTwoPassModeFailed { removeUser(it.userId) }
                .onAccountCreateAddressFailed { removeUser(it.userId) }
                .onAccountDisabled { removeUser(it.userId) }
                .onAccountRemoved { cleanUser(context) }
                .disableInitialNotReadyAccounts()
        }

        // HumanVerification State handling.
        with(humanVerificationOrchestrator) {
            register(context)
            humanVerificationManager.observe(context.lifecycle, minActiveState = Lifecycle.State.RESUMED)
                .onHumanVerificationNeeded { startHumanVerificationWorkflow(it) }
        }

        // Check if we already have Ready account.
        accountManager.getAccounts().onEach { accounts ->
            when {
                accounts.isEmpty() || accounts.all { it.isDisabled() } -> _state.tryEmit(State.LoginNeeded)
                accounts.any { it.isReady() && it.isBootstrapped() } -> _state.tryEmit(State.Ready)
                accounts.any { it.isStepNeeded() } -> _state.tryEmit(State.StepNeeded)
            }
        }.launchIn(context.lifecycleScope)

        // Observe primary user id.
        accountManager.getPrimaryUserId().onEach { userId ->
            _hasPrimary.postValue(userId != null)
        }.launchIn(context.lifecycleScope)
    }

    fun addAccount() {
        authOrchestrator.startAddAccountWorkflow(AccountType.Internal, product)
    }

    fun onAddAccountClosed(block: () -> Unit) {
        authOrchestrator.onAddAccountResult { result -> if (result == null) block() }
    }

    suspend fun getPrimaryUserId(): UserId? {
        return accountManager.getPrimaryUserId().firstOrNull()
    }

    fun logoutPrimary() = viewModelScope.launch {
        getPrimaryUserId()?.let { userId -> removeUser(userId) }
    }

    fun clearError() {
        _errorReport.postValue(null)
    }

    fun resetCalendarsKey(userId: UserId) {
        viewModelScope.launch {
            val resetCalendarsKeyResult = resetCalendarsKeyUseCase.execute(userId)
            resetCalendarsKeyResult.ifSuccessAndLogErrors(logger) { }
            if (resetCalendarsKeyResult !is UseCase.Result.Success<*>) {
                removeUser(userId)
                return@launch
            }

            setupUser(userId, false)
        }
    }

    fun updatePassphrase(userId: UserId) {
        viewModelScope.launch {
            setupUser(userId, false)
        }
    }
}
