package me.proton.android.calendar.presentation.account

import androidx.activity.ComponentActivity
import androidx.lifecycle.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import me.proton.android.calendar.R
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.usecase.*
import me.proton.core.account.domain.entity.*
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.accountmanager.presentation.*
import me.proton.core.auth.presentation.AuthOrchestrator
import me.proton.core.auth.presentation.onAddAccountResult
import me.proton.core.crypto.common.keystore.KeyStoreCrypto
import me.proton.core.domain.entity.Product
import me.proton.core.domain.entity.UserId
import me.proton.core.humanverification.domain.HumanVerificationManager
import me.proton.core.humanverification.presentation.HumanVerificationOrchestrator
import me.proton.core.humanverification.presentation.observe
import me.proton.core.humanverification.presentation.onHumanVerificationNeeded
import me.proton.core.user.domain.UserManager

class AccountViewModel(
    private val userManager: UserManager,
    private val accountManager: AccountManager,
    private val authOrchestrator: AuthOrchestrator,
    private val humanVerificationManager: HumanVerificationManager,
    private val humanVerificationOrchestrator: HumanVerificationOrchestrator,
    private val fetchUserUseCase: FetchUserUseCase,
    private val bootstrapCalendarsUseCase: BootstrapCalendarsUseCase,
    private val valueStoreProvider: ValueStoreProvider,
    private val userSettingsRepository: UserSettingsRepository,
    private val calendarsRepository: CalendarsRepository,
    private val resetCalendarsKeyUseCase: ResetCalendarsKeyUseCase,
    private val keyStoreCrypto: KeyStoreCrypto,
    private val logger: Logger,
    private val product: Product
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

        // TODO: Maybe save fetchResult and skip this call if callAfterReset is true ?
        val fetchResult = fetchUserUseCase.executeFetchUserAndAddresses(userId)
        if (fetchResult !is UseCase.Result.Success<*>) {
            if (fetchResult is UseCase.Result.Error) _errorReport.postValue(fetchResult.error)
            removeUser(userId)
            return
        }

        val bootstrapResult = bootstrapCalendarsUseCase.execute(userId, defaultCalendarName, showConfirmationDialog)
        if (bootstrapResult !is UseCase.Result.Success<*>) {
            if (bootstrapResult is UseCase.Result.Error) {
                _errorReport.postValue(bootstrapResult.error)
                if (bootstrapResult.error == UseCase.Error.RESET_NEEDED ||
                    bootstrapResult.error == UseCase.Error.UPDATE_PASSPHRASE
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

    private suspend fun cleanUser(userId: UserId) {
        // TODO: Stop all running tasks for this user (otherwise -> foreign key SQLiteConstraintException).
        // UseCases could catch Foreign Key Exceptions, if userId is not anymore present (or logged out).
        // Workers could observe getAccount(userId), and cancel if state == Removed ?
        // How to reproduce: 1) Login. 2) During loading/syncing, logout.
        calendarsRepository.shutdown()
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
                .onAccountRemoved { cleanUser(it.userId) }
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
