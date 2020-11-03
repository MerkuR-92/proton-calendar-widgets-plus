package me.proton.android.calendar.presentation.account

import androidx.activity.ComponentActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import me.proton.android.calendar.domain.UsersRepository
import me.proton.android.calendar.domain.ValueKey
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.usecase.BootstrapCalendarsUseCase
import me.proton.android.calendar.domain.usecase.FetchUserUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.core.account.domain.entity.AccountState
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
    private val usersRepository: UsersRepository
) : ViewModel() {

    sealed class State {
        object LoginNeeded : State()
        object Ready : State()
    }

    private var userId = MutableStateFlow<String?>(null)
    private var userPassphrase = MutableStateFlow<ByteArray?>(null)
    private var lastServerEventId = MutableStateFlow<String?>(null)

    private val _state = MutableLiveData<State>()

    private fun setupUser(userId: UserId, passphrase: ByteArray, eventId: String) {
        // TODO UGLY HACK SO WE CAN BOOTSTRAP CALENDARS AFTER LOGIN REMOVE THIS
        var valueStore = valueStoreProvider.provideValueStore("TODO LOGIN")
        valueStore.putString(ValueKey.USER_ID, userId.id)

        valueStore = valueStoreProvider.provideValueStore(userId.id)
        valueStore.putString(ValueKey.USER_PASSPHRASE, String(passphrase))
        valueStore.putString(ValueKey.LAST_SERVER_EVENT_ID, eventId)

        viewModelScope.launch {
            val fetchResult = fetchUserUseCase.execute(userId)
            if (fetchResult !is UseCase.Result.Success) {
                removeUser(userId)
                return@launch
            }

            val bootstrapResult = bootstrapCalendarsUseCase.execute(userId)
            if (bootstrapResult !is UseCase.Result.Success) {
                removeUser(userId)
                return@launch
            }

            _state.postValue(State.Ready)
        }
    }

    private suspend fun removeUser(userId: UserId) {
        accountManager.removeAccount(userId)
        valueStoreProvider.provideValueStore("TODO LOGIN").clearAll()
        valueStoreProvider.provideValueStore(userId.id).clearAll()
    }

    private suspend fun cleanUser(userId: UserId) {
        // TODO: Stop all running tasks for this user (otherwise -> foreign key SQLiteConstraintException).
        // UseCases could catch Foreign Key Exceptions, if userId is not anymore present (or logged out).
        // Workers could observe getAccount(userId), and cancel if state == Removed ?
        // How to reproduce: 1) Login. 2) During loading/syncing, logout.
        usersRepository.deleteUserById(userId.id)
    }

    val state: LiveData<State> = _state

    fun init(context: ComponentActivity) {
        authOrchestrator.register(context)

        // Wait on mandatory parameters.
        authOrchestrator
            .onLoginResult { result ->
                result?.let {
                    lastServerEventId.value = result.session.eventId
                }
            }
            .onUserResult { result ->
                result?.let {
                    userId.value = result.id
                    userPassphrase.value = result.passphrase
                }
            }

        // Setup User as soon as all parameters are available.
        combine(userId, userPassphrase, lastServerEventId) { id, passphrase, eventId ->
            if (id != null && passphrase != null && eventId != null) {
                setupUser(UserId(id), passphrase, eventId)
                // Discard each current values.
                this.userId.value = null
                this.userPassphrase.value = null
                this.lastServerEventId.value = null
            }
        }.launchIn(viewModelScope)

        // Check if we already have Ready accounts.
        viewModelScope.launch {
            val initialReadyAccounts = accountManager.getAccounts(AccountState.Ready).first()
            if (initialReadyAccounts.isNotEmpty()) _state.postValue(State.Ready)
        }

        // Raise LoginNeeded if no accounts, at anytime.
        accountManager.getAccounts().onEach { accounts ->
            if (accounts.isEmpty()) _state.postValue(State.LoginNeeded)
        }.launchIn(viewModelScope)

        // General state handling.
        accountManager.observe(viewModelScope)
            .onAccountDisabled { removeUser(it.userId) }
            .onAccountTwoPassModeFailed { removeUser(it.userId) }
            .onAccountRemoved { cleanUser(it.userId) }
    }

    fun startLoginWorkflow() =
        authOrchestrator.startLoginWorkflow(AccountType.Internal)

    fun logoutPrimary() = viewModelScope.launch {
        accountManager.getPrimaryUserId().firstOrNull()?.let { userId -> removeUser(userId) }
    }

    fun hasPrimary(action: (Boolean) -> Unit) {
        accountManager.getPrimaryUserId().onEach { userId ->
            userId?.let { action(true) } ?: action(false)
        }.launchIn(viewModelScope)
    }
}