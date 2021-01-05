package me.proton.android.calendar.presentation.account

import androidx.activity.ComponentActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import me.proton.android.calendar.R
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.usecase.*
import me.proton.core.account.domain.entity.AccountState
import me.proton.core.account.domain.entity.SessionState
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.accountmanager.domain.getAccounts
import me.proton.core.accountmanager.presentation.observe
import me.proton.core.accountmanager.presentation.onAccountDisabled
import me.proton.core.accountmanager.presentation.onAccountRemoved
import me.proton.core.accountmanager.presentation.onAccountTwoPassModeFailed
import me.proton.core.auth.domain.entity.AccountType
import me.proton.core.auth.presentation.AuthOrchestrator
import me.proton.core.auth.presentation.onLoginResult
import me.proton.core.auth.presentation.onUserResult
import me.proton.core.domain.entity.UserId

class AccountViewModel(
    private val accountManager: AccountManager,
    private val authOrchestrator: AuthOrchestrator,
    private val fetchUserUseCase: FetchUserUseCase,
    private val bootstrapCalendarsUseCase: BootstrapCalendarsUseCase,
    private val valueStoreProvider: ValueStoreProvider,
    private val usersRepository: UsersRepository,
    private val calendarsRepository: CalendarsRepository,
    private val resetPasswordUseCase: ResetPasswordUseCase,
    private val reactivateCalendarKeyUseCase: ReactivateCalendarKeyUseCase
) : ViewModel() {

    sealed class State {
        object LoginNeeded : State()
        object LoginInProgress : State()
        object Processing : State()
        object Ready : State()
    }

    sealed class Error(val value: String) {
        object NoError : Error("no error")
        object FreeUser : Error("user is free")
        object DelinquentUser : Error("user is delinquent")
        object StorageQuotaReached : Error("user reached storage quota")
        object NoCalendar : Error("error user has no calendar")
        object NoActiveCalendar : Error("error user has no active calendar")
        object ResetNeeded: Error("error reset needed for calendar")
        object UpdatePassphrase: Error("error update passphrase for calendar")
    }

    private val _hasPrimary = MutableLiveData<Boolean>()
    private val _state = MutableLiveData<State>()
    private val _errorReport = MutableLiveData<Error>()

    private var defaultCalendarName: String = "My calendar" // This value is set in init.

    init {
        // General state handling.
        accountManager.observe(viewModelScope)
            .onAccountDisabled { removeUser(it.userId) }
            .onAccountTwoPassModeFailed { removeUser(it.userId) }
            .onAccountRemoved { cleanUser(it.userId) }

        // Check if we already have Ready accounts.
        viewModelScope.launch {
            val initialReadyAccounts = accountManager.getAccounts(AccountState.Ready).first()
            if (initialReadyAccounts.isNotEmpty() && _state.value != State.Processing) {
                _state.postValue(State.Ready)
            }
        }

        // Raise LoginNeeded if no accounts, at anytime.
        accountManager.getAccounts().onEach { accounts ->
            if (accounts.isEmpty()) _state.postValue(State.LoginNeeded)
        }.launchIn(viewModelScope)

        // Observe primary user id.
        accountManager.getPrimaryUserId().onEach { userId ->
            _hasPrimary.postValue(userId != null)
        }.launchIn(viewModelScope)
    }

    private fun saveEventId(eventId: String) {
        val valueStore = valueStoreProvider.provideValueStore(ValueSet.TEMP_LOGIN_SET)
        valueStore.putString(ValueKey.LAST_SERVER_EVENT_ID, eventId)
    }

    private fun saveUser(userId: UserId, passphrase: ByteArray) {
        val valueStore = valueStoreProvider.provideValueStore(ValueSet.TEMP_LOGIN_SET)
        valueStore.putString(ValueKey.USER_ID, userId.id)
        valueStore.putString(ValueKey.USER_PASSPHRASE, String(passphrase))
    }

    private fun trySetupUser(showConfirmationDialog: Boolean = true) {
        val tempValueStore = valueStoreProvider.provideValueStore(ValueSet.TEMP_LOGIN_SET)
        val eventId = tempValueStore.getString(ValueKey.LAST_SERVER_EVENT_ID) ?: return
        val userIdString = tempValueStore.getString(ValueKey.USER_ID) ?: return
        val userId = UserId(userIdString)
        val passphrase = tempValueStore.getString(ValueKey.USER_PASSPHRASE) ?: return

        _state.postValue(State.Processing)

        val valueStore = valueStoreProvider.provideValueStore(userId.id)
        valueStore.putString(ValueKey.USER_PASSPHRASE, passphrase)
        valueStore.putString(ValueKey.LAST_SERVER_EVENT_ID, eventId)

        viewModelScope.launch {
            val fetchResult = fetchUserUseCase.execute(userId) // TODO: Maybe save fetchResult and skip this call if callAfterReset is true ?
            if (fetchResult !is UseCase.Result.Success) {
                if (fetchResult is UseCase.Result.Error) handleError(fetchResult.message)
                removeUser(userId)
                return@launch
            }

            val bootstrapResult = bootstrapCalendarsUseCase.execute(userId, defaultCalendarName, showConfirmationDialog)
            if (bootstrapResult !is UseCase.Result.Success) {
                if (bootstrapResult is UseCase.Result.Error) {
                    handleError(bootstrapResult.message)
                    if (bootstrapResult.message == Error.ResetNeeded.value ||
                        bootstrapResult.message == Error.UpdatePassphrase.value) return@launch
                }
                removeUser(userId)
                return@launch
            }

            _state.postValue(State.Ready)
        }
    }

    // TODO get rid of these strings
    private fun handleError(message: String) {
        when (message) {
            Error.FreeUser.value -> _errorReport.postValue(Error.FreeUser)
            Error.DelinquentUser.value -> _errorReport.postValue(Error.DelinquentUser)
            Error.StorageQuotaReached.value -> _errorReport.postValue(Error.StorageQuotaReached)
            Error.NoCalendar.value -> _errorReport.postValue(Error.NoCalendar)
            Error.NoActiveCalendar.value -> _errorReport.postValue(Error.NoActiveCalendar)
            Error.ResetNeeded.value -> _errorReport.postValue(Error.ResetNeeded)
            Error.UpdatePassphrase.value -> _errorReport.postValue(Error.UpdatePassphrase)
        }
    }

    private suspend fun removeUser(userId: UserId) {
        accountManager.removeAccount(userId)
        valueStoreProvider.provideValueStore(userId.id).clearAll()
        valueStoreProvider.provideValueStore(ValueSet.TEMP_LOGIN_SET).clearAll()
    }

    private suspend fun cleanUser(userId: UserId) {
        // TODO: Stop all running tasks for this user (otherwise -> foreign key SQLiteConstraintException).
        // UseCases could catch Foreign Key Exceptions, if userId is not anymore present (or logged out).
        // Workers could observe getAccount(userId), and cancel if state == Removed ?
        // How to reproduce: 1) Login. 2) During loading/syncing, logout.
        calendarsRepository.shutdown()
        usersRepository.deleteUserById(userId.id)
    }

    private fun finishAppIfNoAccount(context: ComponentActivity) = viewModelScope.launch {
        if (accountManager.getAccounts().first().isEmpty()) {
            context.finish()
        }
    }

    val state: LiveData<State> = _state
    val errorReport: LiveData<Error> = _errorReport
    val hasPrimary: LiveData<Boolean> = _hasPrimary

    fun init(context: ComponentActivity, newActivity: Boolean) {
        // Make sure we clear error on init
        clearError()

        defaultCalendarName = context.resources.getString(R.string.default_calendar_name)

        // Wait on mandatory parameters.
        with(authOrchestrator) {
            register(context)
            onLoginResult { result ->
                result?.let {
                    saveEventId(result.session.eventId)
                    trySetupUser()
                } ?: finishAppIfNoAccount(context)
            }
            onUserResult { result ->
                result?.let {
                    saveUser(UserId(result.id), result.passphrase!!)
                    trySetupUser()
                }
            }
        }

        // Clean any unrecoverable Account.
        viewModelScope.launch {
            accountManager.getAccounts().first()
                .filter { account ->

                    val isAccountUnrecoverable = when (account.state) {
                        AccountState.Removed,
                        AccountState.Disabled,
                        AccountState.TwoPassModeFailed -> true
                        AccountState.TwoPassModeNeeded -> newActivity
                        else -> false
                    }

                    val isSessionUnrecoverable = when (account.sessionState) {
                        SessionState.SecondFactorNeeded -> newActivity // TODO wait for core to actually relaunch the login process
                        else -> false
                    }

                    isAccountUnrecoverable || isSessionUnrecoverable
                }
                .forEach {
                    removeUser(it.userId)
                }
        }
    }

    fun startLoginWorkflow() {
        _state.postValue(State.LoginInProgress)
        authOrchestrator.startLoginWorkflow(AccountType.Internal)
    }

    suspend fun getPrimaryUserId(): UserId? {
        return accountManager.getPrimaryUserId().firstOrNull()
    }

    fun logoutPrimary() = viewModelScope.launch {
        getPrimaryUserId()?.let { userId -> removeUser(userId) }
    }

    fun clearError() {
        _errorReport.postValue(Error.NoError)
    }

    fun resetPassword() {
        viewModelScope.launch {
            val tempValueStore = valueStoreProvider.provideValueStore(ValueSet.TEMP_LOGIN_SET)
            val userIdString = tempValueStore.getString(ValueKey.USER_ID) ?: return@launch
            val userId = UserId(userIdString)

            val resetPasswordResult = resetPasswordUseCase.execute(userId)
            if (resetPasswordResult !is UseCase.Result.Success) {
                removeUser(userId)
                return@launch
            }

            trySetupUser(false)
        }
    }

    fun updatePassphrase() {
        trySetupUser(false)
    }
}
