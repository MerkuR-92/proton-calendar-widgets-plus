package me.proton.android.calendar.common

import at.favre.lib.crypto.bcrypt.BCrypt
import at.favre.lib.crypto.bcrypt.Radix64Encoder
import com.google.crypto.tink.subtle.Base64
import com.proton.gopenpgp.armor.Armor
import com.proton.gopenpgp.crypto.Crypto.generateSessionKey
import com.proton.gopenpgp.crypto.Crypto.newKeyFromArmored
import com.proton.gopenpgp.crypto.Crypto.newKeyRing
import com.proton.gopenpgp.crypto.KeyRing
import com.proton.gopenpgp.crypto.PGPMessage
import com.proton.gopenpgp.crypto.PlainMessage
import com.proton.gopenpgp.crypto.SessionKey
import com.proton.gopenpgp.helper.Helper
import me.proton.android.calendar.domain.Crypto
import me.proton.android.calendar.domain.Logger
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.inject.Inject
import kotlin.concurrent.read
import kotlin.concurrent.write

class CryptoImpl @Inject constructor(private val logger: Logger) : Crypto {

    // cached unlocked key rings per [armored keys+passphrase], released after decryption pass
    private val unlockedKeyRingCache = ConcurrentHashMap<String, KeyRing>()

    // guards the key cache from being wiped mid-decrypt
    private val cacheLock = ReentrantReadWriteLock()

    private fun unlockedKeyRing(armoredPrivateKeys: List<String>, passphrase: ByteArray): KeyRing {
        val cacheKey = buildString {
            armoredPrivateKeys.forEach { append(it).append(' ') }
            append('|').append(String(passphrase, StandardCharsets.ISO_8859_1))
        }
        return unlockedKeyRingCache.computeIfAbsent(cacheKey) {
            newKeyRing(null).apply {
                armoredPrivateKeys.forEach {
                    try {
                        addKey(newKeyFromArmored(it).unlock(passphrase))
                    } catch (e: Exception) {
                        logger.i("Unlocking key failed", e)
                    }
                }
            }
        }
    }

    override fun clearKeyRingCache() = cacheLock.write {
        val rings = unlockedKeyRingCache.values.toList()
        unlockedKeyRingCache.clear()
        rings.forEach { runCatching { it.clearPrivateParams() } }
    }

    override fun generateUserPassphrase(passphrase: ByteArray, encodedSalt: String): ByteArray {
        val decodedKeySalt: ByteArray = Base64.decode(encodedSalt, Base64.DEFAULT)
        val generatedUserPassphraseByteRawHash = BCrypt.with(BCrypt.Version.VERSION_2Y)
            .hashRaw(10, decodedKeySalt, passphrase).rawHash
        return Radix64Encoder.Default().encode(generatedUserPassphraseByteRawHash)
    }

    override fun checkPassphrase(armoredKey: String, passphrase: ByteArray): Boolean {
        return try {
            val unlockedKey = newKeyFromArmored(armoredKey).unlock(passphrase)
            unlockedKey.clearPrivateParams()
            true
        } catch (e: Exception) {
            System.out.println(e.localizedMessage)
            logger.i("checkPassphrase failed", e)
            false
        }
    }

    override fun decryptText(
        cipherText: String,
        armoredPrivateKeys: List<String>,
        passphrase: ByteArray
    ): String? = cacheLock.read {
        try {
            val keyRing = unlockedKeyRing(armoredPrivateKeys, passphrase)
            // reuse cached ring but synchronize it for thread safety
            synchronized(keyRing) {
                String(
                    keyRing.decrypt(
                        PGPMessage(
                            cipherText
                        ),
                        null,
                        0L
                    ).data,
                    StandardCharsets.UTF_8
                )
            }
        } catch (e: Exception) {
            if (e.message?.contains("incorrect key") == false) {
                logger.i("decrypt failed", e)
            }
            null
        }
    }

    override fun encryptTextWithSessionKey(
        plainText: String,
        publicKeys: List<String>
    ): Pair<String, List<String?>> {
        val sessionKey = generateSessionKey()

        val keyPackets = arrayListOf<String?>()
        publicKeys.forEach { publicKey ->
            keyPackets.add(getKeyPacket(sessionKey, publicKey))
        }

        val dataPacket = sessionKey.encrypt(
            PlainMessage(plainText.toByteArray())
        )

        // TODO Update CipherText to handle multiple key packets
        return Pair(Base64.encode(dataPacket), keyPackets)
    }

    override fun getKeyPacket(sessionKey: SessionKey, publicKey: String): String? {
        return try {
            val keyRing = newKeyRing(newKeyFromArmored(publicKey))
            val keyPacket = keyRing.encryptSessionKey(sessionKey)
            Base64.encode(keyPacket)
        } catch (e: java.lang.Exception) {
            logger.i("getKeyPacket failed", e)
            null
        }
    }

    override fun encryptText(
        plainText: String,
        armoredPublicKey: String
    ): String? {
        return try {
            Helper.encryptBinaryMessageArmored(armoredPublicKey, plainText.toByteArray())
        } catch (e: Exception) {
            logger.i("encrypt text with public key failed", e)
            null
        }
    }

    override fun encryptText(plainText: String, sessionKey: SessionKey): String? {
        return try {
            Base64.encodeToString(
                PGPMessage(
                    sessionKey.encrypt(
                        PlainMessage(
                            plainText.toByteArray()
                        )
                    )
                ).data,
                Base64.DEFAULT
            )
        } catch (e: Exception) {
            logger.i("encrypt text with session key failed", e)
            null
        }
    }

    override fun getArmoredPublicKey(armoredKey: String): String? {
        return try {
            Armor.armorKey(newKeyFromArmored(armoredKey).publicKey)
        } catch (e: Exception) {
            logger.i("getArmoredPublicKey failed", e)
            null
        }
    }

    override fun decryptSessionKey(
        encodedKeyPacket: String,
        armoredPrivateKeys: List<String>,
        passphrase: ByteArray
    ): SessionKey? {
        val keyRing = newKeyRing(null)
        return try {
            armoredPrivateKeys.forEach {
                try {
                    val unlockedKey = newKeyFromArmored(it).unlock(passphrase)
                    keyRing.addKey(unlockedKey)
                } catch (e: Exception) {
                    logger.i("Unlocking key failed", e)
                }
            }

            keyRing.decryptSessionKey(Base64.decode(encodedKeyPacket, Base64.DEFAULT))
        } catch (e: Exception) {
            logger.i("decryptSessionKey failed", e)
            null
        } finally {
            keyRing?.clearPrivateParams()
        }
    }

    override fun generateEccKey(name: String, email: String, passphrase: ByteArray) : String? {
        return try {
            Helper.generateKey(name, email, passphrase, "x25519", 0)
        } catch (e: Exception) {
            logger.i("generate and encrypt ECC key failed", e)
            null
        }
    }

}
