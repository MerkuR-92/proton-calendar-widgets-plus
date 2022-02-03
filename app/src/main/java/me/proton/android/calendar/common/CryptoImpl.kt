package me.proton.android.calendar.common

import at.favre.lib.crypto.bcrypt.BCrypt
import at.favre.lib.crypto.bcrypt.Radix64Encoder
import com.google.crypto.tink.subtle.Base64
import com.proton.gopenpgp.armor.Armor
import com.proton.gopenpgp.crypto.*
import com.proton.gopenpgp.crypto.Crypto.*
import com.proton.gopenpgp.helper.Helper
import me.proton.android.calendar.domain.Crypto
import me.proton.android.calendar.domain.Logger
import javax.inject.Inject


class CryptoImpl @Inject constructor(private val logger: Logger) : Crypto {

    override fun generateUserPassphrase(passphrase: ByteArray, encodedSalt: String): ByteArray {
        val decodedKeySalt: ByteArray = Base64.decode(encodedSalt, Base64.DEFAULT)
        val generatedUserPassphraseByteRawHash = BCrypt.with(BCrypt.Version.VERSION_2Y).hashRaw(10, decodedKeySalt, passphrase).rawHash
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

    override fun signTextDetached(
        plainText: String,
        armoredPrivateKey: String,
        passphrase: ByteArray
    ) : String? {
        return try {
            val privateKeyRing: KeyRing = createAndUnlockKeyring(armoredPrivateKey, passphrase)
            val result = privateKeyRing.signDetached(PlainMessage(plainText)).armored
            privateKeyRing.clearPrivateParams()
            return result
        } catch (e: java.lang.Exception) {
            logger.i("signTextDetached failed", e)
            null
        }
    }

    override fun verifyTextDetached(
        plainText: String,
        armoredSignature: String,
        armoredPublicKeys: List<String>
    ): Boolean {
        return try {
            val keyring = newKeyRing(null)
            armoredPublicKeys.forEach { keyring.addKey(newKeyFromArmored(it)) }
            keyring.verifyDetached(PlainMessage(plainText), PGPSignature(armoredSignature), 0L) // TODO handle actual error? use different method?
            true
        } catch (e: Exception) {
//            logger.i("verifyTextDetached failed", e)
            false
        }
    }

    override fun decryptText(
        cipherText: String,
        armoredPrivateKey: String,
        passphrase: ByteArray
    ): String? {
        return try {
            Helper.decryptMessageArmored(armoredPrivateKey, passphrase, cipherText)
        } catch (e: Exception) {
            logger.i("decrypt failed", e)
            null
        }
    }

    override fun decryptText(
        cipherText: String,
        armoredPrivateKeys: List<String>,
        passphrase: ByteArray
    ): String? {
        var keyRing: KeyRing? = null
        return try {
            keyRing = newKeyRing(null)
            armoredPrivateKeys.forEach {
                try {
                    val unlockedKey = newKeyFromArmored(it).unlock(passphrase)
                    keyRing.addKey(unlockedKey)
                } catch (e: Exception) {
                    logger.i("Unlocking key failed", e)
                }
            }

            keyRing.decrypt(PGPMessage(cipherText), null, 0L).string
        } catch (e: Exception) {
            logger.i("decrypt failed", e)
            null
        } finally {
            keyRing?.clearPrivateParams()
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
            PlainMessage(plainText)
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
            Helper.encryptMessageArmored(armoredPublicKey, plainText)
        } catch (e: Exception) {
            logger.i("encrypt text with public key failed", e)
            null
        }
    }

    override fun encryptText(plainText: String, sessionKey: SessionKey): String? {
        return try {
            Base64.encodeToString(PGPMessage(sessionKey.encrypt(PlainMessage(plainText))).data, Base64.DEFAULT)
        } catch (e: Exception) {
            logger.i("encrypt text with session key failed", e)
            null
        }
    }

    override fun encryptSignText(plaintext: String, armoredPublicKey: String, privateKey: String, passphrase: ByteArray): String? {
        return try {
            Helper.encryptSignMessageArmored(armoredPublicKey, privateKey, passphrase, plaintext)
        } catch (e: Exception) {
            logger.i("encrypt text with public key failed", e)
            null
        }
    }

    override fun encryptTextWithPassphrase(
    plainText: String,
    passphrase: ByteArray
    ): String? {
        return try {
            Helper.encryptMessageWithPassword(passphrase, plainText)
        } catch (e: Exception) {
            logger.i("encrypt text with passphrase failed", e)
            null
        }
    }

    override fun decryptTextWithPassphrase(
        encodedText: String,
        passphrase: ByteArray
    ): String? {
        return try {
            Helper.decryptMessageWithPassword(passphrase, encodedText)
        } catch (e: Exception) {
            logger.i("encrypt text with passphrase failed", e)
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

    private fun createAndUnlockKeyring(armoredPrivateKey: String, passphrase: ByteArray) : KeyRing {
        return newKeyRing(newKeyFromArmored(armoredPrivateKey).unlock(passphrase))
    }

}
