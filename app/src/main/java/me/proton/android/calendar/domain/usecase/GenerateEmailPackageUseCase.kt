package me.proton.android.calendar.domain.usecase

import com.google.crypto.tink.subtle.Base64
import me.proton.android.calendar.domain.model.EncryptedPackage
import me.proton.android.calendar.domain.model.PackageType
import me.proton.android.calendar.domain.model.SendPreferences
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.crypto.common.pgp.dataPacket
import me.proton.core.crypto.common.pgp.keyPacket
import me.proton.core.crypto.common.pgp.split
import me.proton.core.key.domain.encryptSessionKey
import me.proton.core.key.domain.encryptText
import me.proton.core.key.domain.entity.key.PublicKey
import me.proton.core.mailmessage.domain.entity.Email
import javax.inject.Inject

class GenerateEmailPackageUseCase @Inject constructor(
    private val cryptoContext: CryptoContext
) {
    operator fun invoke(
        signedBodyMime: String,
        recipientEmail: Email,
        sendPreferences: SendPreferences,
        decryptedAttachmentSessionKeys: MutableList<ByteArray>,
        decryptedBodySessionKey: ByteArray,
        encryptedBodyDataPacket: ByteArray,
        decryptedMimeBodySessionKey: ByteArray,
        encryptedMimeBodyDataPacket: ByteArray
    ): EncryptedPackage {

        return if (sendPreferences.encrypt) {

            val publicKey = PublicKey(sendPreferences.publicKey ?: "", isPrimary = true)

            if (sendPreferences.pgpScheme == PackageType.ProtonMail) {

                val recipientBodyKeyPacket = publicKey.encryptSessionKey(cryptoContext, decryptedBodySessionKey)

                val encryptedAttachmentKeyPackets = decryptedAttachmentSessionKeys.map {
                    Base64.encode(publicKey.encryptSessionKey(cryptoContext, it))
                }

                EncryptedPackage(
                    addresses = mapOf(
                        recipientEmail to EncryptedPackage.Address.Internal(
                            bodyKeyPacket = Base64.encode(recipientBodyKeyPacket),
                            attachmentKeyPackets = encryptedAttachmentKeyPackets
                        )
                    ),
                    mimeType = "text/plain",
                    body = Base64.encode(encryptedBodyDataPacket),
                    type = PackageType.ProtonMail.type
                )

            } else {

                val recipientEncryptedMultipartBodyCipherText = publicKey
                    .encryptText(cryptoContext, signedBodyMime)
                    .split(cryptoContext.pgpCrypto)

                EncryptedPackage(
                    addresses = mapOf(
                        recipientEmail to EncryptedPackage.Address.ExternalEncrypted(
                            bodyKeyPacket = Base64.encode(recipientEncryptedMultipartBodyCipherText.keyPacket())
                        )
                    ),
                    mimeType = "multipart/mixed",
                    body = Base64.encode(recipientEncryptedMultipartBodyCipherText.dataPacket()),
                    type = PackageType.PgpMime.type
                )

            }

        } else {

            if (sendPreferences.sign) { // ClearMime

                EncryptedPackage(
                    addresses = mapOf(recipientEmail to EncryptedPackage.Address.ExternalSigned),
                    mimeType = "multipart/mixed",
                    body = Base64.encode(encryptedMimeBodyDataPacket),
                    type = PackageType.ClearMime.type,
                    bodyKey = EncryptedPackage.Key(Base64.encode(decryptedMimeBodySessionKey), "aes256")
                )

            } else { // Cleartext

                val packageAttachmentKeys = decryptedAttachmentSessionKeys.map {
                    EncryptedPackage.Key(Base64.encode(it), "aes256")
                }

                EncryptedPackage(
                    addresses = mapOf(recipientEmail to EncryptedPackage.Address.ExternalPlaintext),
                    mimeType = "text/plain",
                    body = Base64.encode(encryptedBodyDataPacket),
                    type = PackageType.Cleartext.type,
                    attachmentKeys = packageAttachmentKeys,
                    bodyKey = EncryptedPackage.Key(Base64.encode(decryptedBodySessionKey), "aes256")
                )

            }

        }

    }
}
