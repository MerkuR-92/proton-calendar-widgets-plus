package me.proton.android.calendar.domain.crypto

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import me.proton.android.calendar.common.utils.getAddressesOrNull
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.data.joinToCalendar
import me.proton.android.calendar.domain.Crypto
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ValueSet
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.model.Calendar
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.domain.entity.UserId
import me.proton.core.key.domain.entity.key.PrivateKeyRing
import me.proton.core.key.domain.entity.key.PublicKeyRing
import me.proton.core.key.domain.entity.keyholder.KeyHolderContext
import me.proton.core.key.domain.publicKey
import me.proton.core.user.domain.UserAddressManager
import me.proton.core.user.domain.entity.UserAddress
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

// wipe key material after this grace idle period
private val RELEASE_DELAY = 2.seconds

/**
 * App-scoped, ref-counted cache of unlocked PGP key material
 * and the per-calendar/per-user metadata it's derived from used during a decryption pass
 */
@Singleton
class DecryptionKeyCache @Inject constructor(
    private val database: AppDatabase,
    private val json: Json,
    private val valueStoreProvider: ValueStoreProvider,
    private val userAddressManager: UserAddressManager,
    private val cryptoContext: CryptoContext,
    private val crypto: Crypto,
    private val logger: Logger,
) {

    data class CalendarContext(
        val calendarEntity: CalendarEntity,
        val userId: String,
        val calendar: Calendar,
        val calendarPrivateKeys: List<String>,
        val keyPassphrase: String,
    )

    private val calendarCtxCache = ConcurrentHashMap<String, CalendarContext>()
    private val userAddressesCache = ConcurrentHashMap<String, List<UserAddress>>()
    private val addressKeyContextCache = ConcurrentHashMap<String, KeyHolderContext>()

    private val keyLock = Any()
    private var activeDecryptions = 0
    private var releaseJob: Job? = null

    @VisibleForTesting
    internal var bgScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // runs one decryption block, counts active decryptions, cleans up after some idle period
    suspend fun <T> withActiveDecryption(block: suspend () -> T): T {
        beginDecryption()
        return try {
            block()
        } finally {
            endDecryption()
        }
    }

    private fun beginDecryption() = synchronized(keyLock) {
        activeDecryptions++
        releaseJob?.cancel()
        releaseJob = null
    }

    private fun endDecryption() = synchronized(keyLock) {
        activeDecryptions--
        if (activeDecryptions == 0) {
            releaseJob = bgScope.launch {
                delay(RELEASE_DELAY)
                synchronized(keyLock) {
                    if (activeDecryptions == 0) takeAndClearCachesLocked() else null
                }?.closeAll()
            }
        }
    }

    private fun takeAndClearCachesLocked(): List<KeyHolderContext> {
        val ctxs = addressKeyContextCache.values.toList()
        calendarCtxCache.clear()
        userAddressesCache.clear()
        addressKeyContextCache.clear()
        crypto.clearKeyRingCache()
        return ctxs
    }

    private fun List<KeyHolderContext>.closeAll() = forEach { runCatching { it.close() } }

    // per-calendar invariant data (keys, passphrase, joined calendar)
    suspend fun calendarContext(calendarId: String): CalendarContext? {
        calendarCtxCache[calendarId]?.let { return it }
        val calendarEntity = database.calendarsDao().selectById(calendarId) ?: return null
        val userId = calendarEntity.fkUserId
        val calendar = calendarEntity.joinToCalendar(database, json) ?: return null
        val calendarPrivateKeys = database.calendarKeysDao().select(calendarId).filter { it.isActive }.map { it.privateKey }
        if (calendarPrivateKeys.isEmpty()) {
            logger.e("DecryptionKeyCache, calendarKey is null")
            return null
        }
        val calendarPassphrase = database.passphrasesDao().select(calendarId).map { it.toPassphrase(json) }.firstOrNull() { it.isActive }
        if (calendarPassphrase == null) {
            logger.e("DecryptionKeyCache, calendarPassphrase is null")
            return null
        }
        val keyPassphrase = valueStoreProvider.provideValueStore(userId).getStringFromSet(ValueSet.CALENDAR_PASSPHRASE, calendarPassphrase.id) // gitleaks:allow
        if (keyPassphrase == null) {
            logger.e("DecryptionKeyCache, keyPassphrase is null")
            return null
        }
        return CalendarContext(calendarEntity, userId, calendar, calendarPrivateKeys, keyPassphrase)
            .also { calendarCtxCache[calendarId] = it }
    }

    suspend fun userAddresses(userId: String): List<UserAddress>? {
        userAddressesCache[userId]?.let { return it }
        return userAddressManager.getAddressesOrNull(UserId(userId))?.also { userAddressesCache[userId] = it }
    }

    fun addressKeyContext(userAddress: UserAddress): KeyHolderContext =
        addressKeyContextCache.computeIfAbsent(userAddress.addressId.id) {
            val privateKeys = userAddress.keys.filter { it.privateKey.isActive }.map { it.privateKey }
            val publicKeys = privateKeys.map { it.publicKey(cryptoContext) }
            KeyHolderContext(cryptoContext, PrivateKeyRing(cryptoContext, privateKeys), PublicKeyRing(publicKeys))
        }
}
