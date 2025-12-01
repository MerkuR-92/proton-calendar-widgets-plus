package me.proton.android.calendar.domain.utils

import android.util.Base64
import com.google.crypto.tink.subtle.Hkdf
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.crypto.common.pgp.Based64Encoded
import me.proton.core.crypto.common.pgp.DecryptedText
import me.proton.core.crypto.common.pgp.PGPCrypto
import me.proton.core.crypto.common.pgp.SessionKey
import me.proton.core.crypto.common.pgp.SignatureContext
import me.proton.core.crypto.common.pgp.VerificationContext
import me.proton.core.crypto.common.srp.SrpCrypto
import me.proton.core.key.domain.decryptAndVerifyText
import me.proton.core.key.domain.encryptAndSignText
import me.proton.core.key.domain.useKeys
import me.proton.core.user.domain.entity.User
import java.security.SecureRandom

interface ProtonMeetCrypto {

    val pgpCrypto: PGPCrypto
    val srpCrypto: SrpCrypto

    fun encryptAndSignText(user: User, text: String): String

    fun randomPassword(len: Int): String

    fun encryptEventName(
        eventName: String,
        sessionKey: SessionKey,
    ): String

    fun decryptSessionKey(
        encryptedSessionKeyB64: String,
        password: String,
        saltB64: String,
    ): SessionKey?

    fun encryptSessionKey(
        sessionKey: SessionKey,
        passwordHash: ByteArray,
    ): String

    fun decryptPassword(user: User, encryptedPassword: String): DecryptedText

    companion object {

        private const val SignatureContextValue = "pw.link.meet.proton"

        private const val PassCharset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

        private const val AesKeyInfo = "aeskey.link.meet.proton"
        private const val MetadataAad = "metadata.meet.proton"
        private const val HdkfSize = 32
        private const val HmacSha256Alg = "HmacSHA256"

        fun fromCrypto(context: CryptoContext) = object : ProtonMeetCrypto {

            override fun encryptAndSignText(
                user: User,
                text: String,
            ): String = user.useKeys(context) {
                encryptAndSignText(
                    text,
                    signatureContext = SignatureContext(
                        value = SignatureContextValue,
                        isCritical = true,
                    ),
                )
            }

            override fun decryptPassword(user: User, encryptedPassword: String): DecryptedText = user.useKeys(context) {
                decryptAndVerifyText(
                    encryptedPassword,
                    verificationContext = VerificationContext(
                        value = SignatureContextValue,
                        required = VerificationContext.ContextRequirement.Required.Always,
                    )
                )
            }

            private fun getPasswordHash(
                password: String,
                saltB64: String,
            ): ByteArray {
                return pgpCrypto.getPassphrase(
                    password = password.encodeToByteArray(),
                    encodedSalt = saltB64,
                )
            }

            override fun encryptSessionKey(
                sessionKey: SessionKey,
                passwordHash: ByteArray,
            ): String {
                val keyPacket = pgpCrypto.encryptSessionKeyWithPassword(
                    sessionKey,
                    passwordHash,
                )
                return pgpCrypto.getBase64EncodedNoWrap(keyPacket)
            }

            override fun decryptSessionKey(
                encryptedSessionKeyB64: String,
                password: String,
                saltB64: String,
            ): SessionKey? {
                val passwordHash = getPasswordHash(password, saltB64)
                val keyPacket = getBase64DecodedNoWrap(encryptedSessionKeyB64)
                return pgpCrypto.decryptSessionKeyWithPassword(
                    keyPacket = keyPacket,
                    password = passwordHash,
                )
            }

            fun getBase64DecodedNoWrap(string: Based64Encoded): ByteArray =
                Base64.decode(string, Base64.NO_WRAP)

            override fun encryptEventName(
                eventName: String,
                sessionKey: SessionKey,
            ): String {
                val aesKeyBytes = deriveAesKey(sessionKey)
                return context.aeadCryptoFactory.create()
                    .encrypt(eventName,
                        key = aesKeyBytes,
                        aad = MetadataAad.encodeToByteArray(),
                    )
            }

            /**
             * Web reference: https://github.com/ProtonMail/WebClients/blob/main/packages/meet/utils/cryptoUtils.ts#L26
             */
            private fun deriveAesKey(sessionKey: SessionKey) = Hkdf.computeHkdf(
                HmacSha256Alg,
                sessionKey.key,
                zeroSalt(),
                AesKeyInfo.encodeToByteArray(),
                HdkfSize,
            )

            private fun zeroSalt() = ByteArray(HdkfSize)

            override val pgpCrypto: PGPCrypto = context.pgpCrypto
            override val srpCrypto: SrpCrypto = context.srpCrypto

            override fun randomPassword(len: Int) = buildString {
                val rnd = SecureRandom()
                repeat(len) {
                    append(PassCharset[rnd.nextInt(PassCharset.length)])
                }
            }
        }
    }
}
