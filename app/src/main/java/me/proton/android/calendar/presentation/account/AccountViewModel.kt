package me.proton.android.calendar.presentation.account

import androidx.activity.ComponentActivity
import androidx.lifecycle.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import me.proton.android.calendar.R
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.usecase.*
import me.proton.core.account.domain.entity.Account
import me.proton.core.account.domain.entity.AccountType
import me.proton.core.account.domain.entity.isReady
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.accountmanager.presentation.*
import me.proton.core.auth.presentation.AuthOrchestrator
import me.proton.core.auth.presentation.onLoginResult
import me.proton.core.crypto.common.keystore.KeyStoreCrypto
import me.proton.core.crypto.common.keystore.decryptWith
import me.proton.core.domain.entity.UserId
import me.proton.core.key.domain.extension.primary
import me.proton.core.user.domain.UserManager

class AccountViewModel(
    private val accountManager: AccountManager,
    private val userManager: UserManager,
    private val authOrchestrator: AuthOrchestrator,
    private val fetchUserUseCase: FetchUserUseCase,
    private val bootstrapCalendarsUseCase: BootstrapCalendarsUseCase,
    private val valueStoreProvider: ValueStoreProvider,
    private val usersRepository: UsersRepository,
    private val calendarsRepository: CalendarsRepository,
    private val resetCalendarsKeyUseCase: ResetCalendarsKeyUseCase,
    private val keyStoreCrypto: KeyStoreCrypto,
    private val logger: Logger
) : ViewModel() {

    sealed class State {
        object LoginNeeded : State()
        object LoginInProgress : State()
        object Processing : State()
        object Ready : State()
    }

    private val _hasPrimary = MutableLiveData<Boolean>()
    private val _state = MutableLiveData<State>()
    private val _errorReport = MutableLiveData<UseCase.Error?>()

    private var defaultCalendarName: String = "My calendar" // This value is set in init.

    private fun Account.isSavedForSetup(): Boolean {
        val valueStore = valueStoreProvider.provideValueStore(ValueSet.TEMP_LOGIN_SET)
        return valueStore.getString(ValueKey.USER_ID) == userId.id
    }

    private suspend fun saveAccountInfo(account: Account) {
        if (!account.isSavedForSetup()) {
            val valueStore = valueStoreProvider.provideValueStore(ValueSet.TEMP_LOGIN_SET)

            val userId = account.userId
            valueStore.putString(ValueKey.USER_ID, userId.id)

            val eventId = checkNotNull(account.details.session?.initialEventId)
            valueStore.putString(ValueKey.LAST_SERVER_EVENT_ID, eventId)

            val user = userManager.getUser(userId, refresh = true)
            val passphrase = user.keys.primary()?.privateKey?.passphrase
            val decryptedPassphrase = checkNotNull(passphrase).decryptWith(keyStoreCrypto)
            valueStore.putString(ValueKey.USER_PASSPHRASE, String(decryptedPassphrase.array))

            setupUser()
        }
    }

    private suspend fun setupUser(showConfirmationDialog: Boolean = true) {
        val tempValueStore = valueStoreProvider.provideValueStore(ValueSet.TEMP_LOGIN_SET)
        val eventId = checkNotNull(tempValueStore.getString(ValueKey.LAST_SERVER_EVENT_ID))
        val userIdString = checkNotNull(tempValueStore.getString(ValueKey.USER_ID))
        val passphrase = checkNotNull(tempValueStore.getString(ValueKey.USER_PASSPHRASE))

        val userId = UserId(userIdString)

        _state.postValue(State.Processing)

        val valueStore = valueStoreProvider.provideValueStore(userId.id)
        valueStore.putString(ValueKey.USER_PASSPHRASE, passphrase)
        valueStore.putString(ValueKey.LAST_SERVER_EVENT_ID, eventId)

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

        _state.postValue(State.Ready)
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
    val errorReport: LiveData<UseCase.Error?> = _errorReport
    val hasPrimary: LiveData<Boolean> = _hasPrimary

    fun init(context: ComponentActivity) {
        // Make sure we clear error on init
        clearError()

        defaultCalendarName = context.resources.getString(R.string.default_calendar_name)

        with(authOrchestrator) {
            register(context)
            // Close app if Login screen has been closed.
            onLoginResult { result -> if (result == null) finishAppIfNoAccount(context) }
            // General state handling.
            accountManager.observe(context.lifecycleScope)
                .onAccountReady { saveAccountInfo(it) }
                .onSessionSecondFactorNeeded { startSecondFactorWorkflow(it) }
                .onAccountTwoPassModeNeeded { startTwoPassModeWorkflow(it) }
                .onAccountCreateAddressNeeded { startChooseAddressWorkflow(it) }
                .onSessionHumanVerificationNeeded { startHumanVerificationWorkflow(it) }
                .onAccountTwoPassModeFailed { removeUser(it.userId) }
                .onAccountCreateAddressFailed { removeUser(it.userId) }
                .onAccountDisabled { removeUser(it.userId) }
                .onAccountRemoved { cleanUser(it.userId) }
                .disableInitialNotReadyAccounts()
        }

        // Check if we already have Ready accounts.
        accountManager.getAccounts().onEach { accounts ->
            when {
                accounts.isEmpty() -> _state.postValue(State.LoginNeeded)
                accounts.any {
                    it.isReady() && usersRepository.selectUserSettings(it.userId.id) != null
                } -> _state.postValue(State.Ready)
            }
        }.launchIn(context.lifecycleScope)

        // Observe primary user id.
        accountManager.getPrimaryUserId().onEach { userId ->
            _hasPrimary.postValue(userId != null)
        }.launchIn(context.lifecycleScope)
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
        _errorReport.postValue(null)
    }

    fun resetCalendarsKey() {
        viewModelScope.launch {
            val userId = getPrimaryUserId() ?: return@launch

            val resetCalendarsKeyResult = resetCalendarsKeyUseCase.execute(userId)
            resetCalendarsKeyResult.ifSuccessAndLogErrors(logger) { }
            if (resetCalendarsKeyResult !is UseCase.Result.Success<*>) {
                removeUser(userId)
                return@launch
            }

            setupUser(false)
        }
    }

    fun updatePassphrase() {
        viewModelScope.launch {
            setupUser(false)
        }
    }
}
