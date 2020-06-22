package me.proton.android.calendar.domain

import com.proton.gopenpgp.crypto.SessionKey
import com.proton.gopenpgp.srp.Proofs

interface Crypto {

    /**
     * Generates BCrypted passphrase using provided Base64-encoded salt.
     */
    fun generateUserPassphrase(passphrase: ByteArray, encodedSalt: String): ByteArray

    /**
     * Checks if this key can be unlocked by this passphrase.
     */
    fun checkPassphrase(armoredKey: String, passphrase: ByteArray): Boolean

    /**
     * Signs plaintext using private key.
     */
    fun signTextDetached(
        plainText: String,
        armoredPrivateKey: String,
        passphrase: ByteArray
    ): String?

    /**
     * Verifies plaintext signature, success if at least one key verifies signature correctly.
     */
    fun verifyTextDetached(
        plainText: String,
        armoredSignature: String,
        armoredPublicKeys: List<String>
    ): Boolean

    /**
     * Decrypts text using private key.
     */
    fun decryptText(cipherText: String, armoredPrivateKey: String, passphrase: ByteArray): String?

    /**
     * Encrypts plaintext with armored PublicKey and returns Armored PGPMessage as String. This message contains KeyPacket and DataPacket.
     */
    fun encryptText(plainText: String, armoredPublicKey: String): String?

    /**
     * Encrypts plaintext with SessionKey and returns Armored PGPMessage as String. This message contains DataPacket but no KeyPacket.
     */
    fun encryptText(plainText: String, sessionKey: SessionKey): String?

    /**
     * Extracts public key from supplied key (private or public).
     */
    fun getArmoredPublicKey(armoredKey: String): String?

    /**
     * Generates Proofs for SRP login.
     */
    fun generateSrpProofs(
        username: String,
        passphrase: ByteArray,
        signedModulus: String,
        serverEphemeral: String,
        authVersion: Int,
        salt: String
    ): Proofs?

    /**
     * Decrypts Base64-encoded KeyPacket.
     */
    fun decryptSessionKey(
        encodedKeyPacket: String,
        armoredPrivateKey: String,
        passphrase: ByteArray
    ): SessionKey?
}
