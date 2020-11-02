package me.proton.android.calendar.domain.usecase

import com.google.gson.Gson
import me.proton.android.calendar.common.TimberLogger
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.api.AddressesApi
import me.proton.android.calendar.domain.api.AuthenticationApi
import me.proton.android.calendar.domain.api.KeysApi
import me.proton.android.calendar.domain.api.UsersApi
import me.proton.core.account.domain.entity.Account
import me.proton.core.account.domain.entity.AccountState
import me.proton.core.account.domain.entity.SessionState
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.domain.entity.UserId
import me.proton.core.network.domain.session.Session
import me.proton.core.network.domain.session.SessionId

/**
 * After this UseCase succeeds, we have correct credentials, User and Addresses saved in database.
 *
 * It has to be executed shortly after login, because of special access-token scope required for getting keysalts.
 */
class LoginUserUseCase(
    private val logger: Logger,
    private val gson: Gson,
    private val usersApi: UsersApi,
    private val addressesApi: AddressesApi,
    private val authenticationApi: AuthenticationApi,
    private val keysApi: KeysApi,
    private val crypto: Crypto,
    private val valueStoreProvider: ValueStoreProvider,
    private val usersRepository: UsersRepository,
    private val accountManager: AccountManager
): UseCase {


    /*
    private suspend fun getProofs(
        username: String,
        password: String,
        infoResponse: LoginInfoResponse
    ): Proofs? = withContext(Dispatchers.Default) {
        val auth = Auth(
            infoResponse.version,
            username,
            password,
            infoResponse.salt,
            infoResponse.modulus,
            infoResponse.serverEphemeral
        )
        auth.generateProofs(2048)
    }

    private suspend fun loginWithProofs(loginBody: LoginBody): LoginState {
        return when (val loginResult = api.postLogin(loginBody)) {
            is ApiResult.Error -> {
                LoginState.Error(loginResult, false)
            }

            is ApiResult.Success -> {
                Storage.save(loginResult.value)
                when (val infoResult = api.getVPNInfo()) {
                    is ApiResult.Error -> LoginState.Error(infoResult, true)
                    is ApiResult.Success -> {
                        userData.setLoggedIn(infoResult.valueOrNull)
                        LoginState.Success
                    }
                }
            }
        }
    }

    suspend fun login(password: String): LoginState {
        Storage.delete(LoginResponse::class.java)
        return when (val loginInfoResult = api.postLoginInfo(userData.user)) {
            is ApiResult.Error -> LoginState.Error(loginInfoResult, true)
            is ApiResult.Success -> {
                val loginBody = getLoginBody(loginInfoResult.value, password)
                if (loginBody == null) {
                    LoginState.UnsupportedAuth
                } else {
                    loginWithProofs(loginBody)
                }
            }
        }
    }

    private suspend fun getLoginBody(loginInfo: LoginInfoResponse, password: String): LoginBody? {
        val proofs = getProofs(userData.user, password, loginInfo) ?: return null
        return LoginBody(
            userData.user,
            loginInfo.srpSession,
            ConstantTime.encodeBase64(proofs.clientEphemeral, true),
            ConstantTime.encodeBase64(proofs.clientProof, true),
            ""
        )
    }

     */



//    suspend fun login(password: String): LoginState {
//        Storage.delete(LoginResponse::class.java)
//        return when (val loginInfoResult = api.postLoginInfo(userData.user)) {
//            is ApiResult.Error -> LoginState.Error(loginInfoResult, true)
//            is ApiResult.Success -> {
//                val loginBody = getLoginBody(loginInfoResult.value, password)
//                if (loginBody == null) {
//                    LoginState.UnsupportedAuth
//                } else {
//                    loginWithProofs(loginBody)
//                }
//            }
//        }
//    }





    // TODO I THINK WE CAN CLEAR ENTIRE VALUE_STORE IN CASE OF ERROR HERE
    suspend fun execute(username: String, passphrase: ByteArray) : UseCase.Result {

        logger.v("executing LoginUserUseCase")



        val loginInfoResponse = authenticationApi.fetchLoginInfo(username)

        val proofs = if (loginInfoResponse is ApiResponse.Success) {
            crypto.generateSrpProofs(
                username,
                passphrase,
                loginInfoResponse.data.modulus,
                loginInfoResponse.data.serverEphemeral,
                loginInfoResponse.data.version,
                loginInfoResponse.data.salt
            )
        } else return UseCase.Result.Error("login info request failed: $loginInfoResponse")

        if (proofs == null) return UseCase.Result.Error("generating SRP proofs failed")


        val loginResponse = authenticationApi.login(
            username,
            loginInfoResponse.data.srpSession,
            com.google.crypto.tink.subtle.Base64.encode(proofs.clientEphemeral),
            com.google.crypto.tink.subtle.Base64.encode(proofs.clientProof)
        )
        if (loginResponse !is ApiResponse.Success) return UseCase.Result.Error("login request failed: ${loginResponse}")

        logger.v("loginResponse: ${loginResponse}")


        /* ApiResponse.Success<LoginApiResponse>(LoginApiResponse(
            1000, // TODO check code?
            "fad7745e1eb406b4c28d0160003793c921e3ad4e",
            "281dabe17d8797f367ad2cc5a66deb32e9b571b8",
            "IXFh2TE4LI11sd0GYf94r7fddHNMdZvicfoWMACCjPTS-oNjpBjeclhKlIs6N48-GB5w-zM6uqX_9HFgEnzhYQ==",
            "7ae4c83ec46e24c97774f09733159d26e5c754ce",
            "l_o7TdJpH3UCfYn-0xBWaJVlZ633baHNvjyZFvuX7TD6lFPaOzy-3YkSorWafW5nKivpxfZ1YaU66_d25R58-Q=="
        // TODO password mode?
        ))*/ /*?: UseCaseResult.Error("auth request failed")*/

        // TODO UGLY HACK SO WE CAN BOOTSTRAP CALENDARS AFTER LOGIN REMOVE THIS
        valueStoreProvider.provideValueStore("TODO LOGIN").putString("USERID", loginResponse.data.userId)






        val userId = loginResponse.data.userId
        val valueStore = valueStoreProvider.provideValueStore(userId)

        with (valueStore) {
            putString(ValueKey.AUTH_ACCESS_TOKEN, loginResponse.data.accessToken)
            putString(ValueKey.AUTH_UID, loginResponse.data.uid)
            putString(ValueKey.AUTH_REFRESH_TOKEN, loginResponse.data.refreshToken)
            putString(ValueKey.LAST_SERVER_EVENT_ID, loginResponse.data.eventID)
        }

        TimberLogger.d("eventId = userid ${userId}")
        TimberLogger.d("eventId = ${loginResponse.data.eventID}")

        // Workaround to add account/session (needed to do an api call).
        // Extract data from loginResponse and create/add an Account.
        accountManager.addAccount(
            account = Account(
                userId = UserId(loginResponse.data.userId),
                username = username,
                email = null,
                state = AccountState.Ready,
                sessionId = SessionId(loginResponse.data.uid),
                sessionState = SessionState.Authenticated
            ),
            session = Session(
                sessionId = SessionId(loginResponse.data.uid),
                accessToken = loginResponse.data.accessToken,
                refreshToken = loginResponse.data.refreshToken,
                headers = null,
                scopes = listOf()
            )
        )

        val userResponse = usersApi.getUser(UserId(userId))
        if (userResponse is ApiResponse.Success) {
            usersRepository.persistUser(userResponse.data.user)
        } else return UseCase.Result.Error("user request failed: $userResponse")

        val user = userResponse.data.user.toUser(gson)
        user.primaryKey ?: return UseCase.Result.Error("user has no primary key")

        // get keysalts
        val keySaltsResponse = keysApi.getKeySalts()
        val keySalt = if (keySaltsResponse is ApiResponse.Success) {
            keySaltsResponse.data.keySalts.find { it.id == user.primaryKey.id } ?: return UseCase.Result.Error("no matching keysalt found")
        } else return UseCase.Result.Error("key salts request failed: $keySaltsResponse")

        // generate passphrase and store it locally
        val generatedUserPassphrase = crypto.generateUserPassphrase(passphrase, keySalt.keySalt)
        valueStore.putString(ValueKey.USER_PASSPHRASE, String(generatedUserPassphrase))

        if (!crypto.checkPassphrase(user.primaryKey.privateKey, generatedUserPassphrase)) return UseCase.Result.Error("no passphrase matching user's primary key")

        val addressesResponse = addressesApi.getAddresses()
        if (addressesResponse is ApiResponse.Success) {
            addressesResponse.data.addresses.forEach {
                usersRepository.persistAddress(userId, it)
            }
        } else return UseCase.Result.Error("addresses request failed: $addressesResponse")


        TimberLogger.d("eventId finished success")

        return UseCase.Result.Success
    }

}
