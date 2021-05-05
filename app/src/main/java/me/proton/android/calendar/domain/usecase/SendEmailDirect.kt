package me.proton.android.calendar.domain.usecase

import com.github.mangstadt.vinnie.io.FoldedLineWriter
import com.google.crypto.tink.subtle.Base64
import com.google.crypto.tink.subtle.Hex
import com.google.crypto.tink.subtle.Random
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.EmailMessageRepository
import me.proton.android.calendar.domain.model.PackageType
import me.proton.android.calendar.domain.model.SendPreferences
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.crypto.common.pgp.*
import me.proton.core.crypto.common.pgp.exception.CryptoException
import me.proton.core.key.domain.*
import me.proton.core.mailmessage.domain.encryptAndSignAttachmentOrNull
import me.proton.core.mailmessage.domain.entity.*
import me.proton.core.mailmessage.domain.usecase.SendEmailDirect
import me.proton.core.user.domain.entity.UserAddress
import me.proton.core.util.kotlin.filterNullValues
import me.proton.core.util.kotlin.nullIfBlank
import java.io.ByteArrayInputStream
import java.io.StringWriter
import javax.inject.Inject

class SendEmailDirect @Inject constructor(
    private val emailMessageRepository: EmailMessageRepository,
    private val generateEmailPackageUseCase: GenerateEmailPackageUseCase,
    private val cryptoContext: CryptoContext,
    private val logger: Logger
) {

    data class Arguments(
        val subject: String,
        val body: String,
        val mimeType: String,
        val toEmailList: List<String>,
        val attachments: List<Attachment>
    ) {
        data class Attachment(
            val fileName: Filename,
            val fileSize: Int,
            val mimeType: String,
            val bytes: ByteArray
        ) {
            fun toAttachment() =
                SendEmailDirect.Arguments.Attachment(this.fileName, this.fileSize, this.mimeType, ByteArrayInputStream(this.bytes))
        }
    }

    sealed class Result {
        data class Success(val receipt: EmailReceipt) : Result()

        sealed class Error : Result() {
            data class GeneratingEmailPackages(val emailAddresses: List<String>) : Error()
            data class EncryptingAttachments(val attachmentFileNames: List<String>) : Error()
        }
    }

    suspend operator fun invoke(
        sender: UserAddress,
        arguments: Arguments,
        sendPreferences: Map<Email, SendPreferences>
    ): Result {

        // Encrypt and sign attachments and body, create payload for sender.
        val decryptedAttachmentSessionKeys = mutableListOf<ByteArray>()
        val encodedAttachmentKeyPackets = mutableListOf<String>()

        lateinit var decryptedPlaintextBodySessionKey: ByteArray
        lateinit var encryptedPlaintextBodyDataPacket: ByteArray

        lateinit var decryptedMimeBodySessionKey: ByteArray
        lateinit var encryptedMimeBodyDataPacket: ByteArray

        // Map<Email, Pair<KeyPacket, DataPacket>>
        lateinit var signedAndEncryptedBodyMimeForRecipients: Map<Email, Pair<ByteArray, ByteArray>>

        lateinit var encryptedEmail: EncryptedEmail

        val attachments = mutableMapOf<Filename, EncryptedAttachment?>()
        sender.useKeys(cryptoContext) {
            arguments.attachments.forEach { attachment ->
                attachments[attachment.fileName] =
                    encryptAndSignAttachmentOrNull(attachment.toAttachment() /*TODO remove helper call when we move to core*/)
            }
        }
        val failedAttachmentFilenames = attachments.filterValues { it == null }.keys
        if (failedAttachmentFilenames.isNotEmpty())
            return Result.Error.EncryptingAttachments(failedAttachmentFilenames.toList())

        val encryptedAttachments = attachments.filterNullValues().values

        sender.useKeys(cryptoContext) {
            val senderAttachments = encryptedAttachments.map {
                EncryptedEmail.Attachment(
                    fileName = it.fileName,
                    mimeType = it.mimeType,
                    contents = Base64.encode(it.keyPacket.packet + it.dataPacket.packet + it.signature.packet)
                )
            }

            // sending works with this empty as well
            // encodedAttachmentKeyPackets.add(Base64.encode(encryptedAttachment.keyPacket))

            // Decrypt session keys of all attachments for later creation of packages for plaintext recipients.
            decryptedAttachmentSessionKeys.addAll(encryptedAttachments.map { decryptSessionKey(it.keyPacket.packet) })
            val encryptedBodyPgpMessage = encryptAndSignText(arguments.body)

            encryptedEmail = EncryptedEmail(
                subject = arguments.subject,
                sender = EncryptedEmail.Address(
                    sender.email,
                    sender.displayName?.nullIfBlank() ?: sender.email
                ),
                to = arguments.toEmailList.map { EncryptedEmail.Address(it, it) },
                cc = emptyList(),
                bcc = emptyList(),
                body = encryptedBodyPgpMessage,
                mimeType = arguments.mimeType,
                attachments = senderAttachments
            )

            // Decrypt body's session key to send it for plaintext recipients.
            val encryptedPlaintextBodySplit = encryptedBodyPgpMessage.split(cryptoContext.pgpCrypto)
            decryptedPlaintextBodySessionKey = decryptSessionKey(encryptedPlaintextBodySplit.keyPacket())
            encryptedPlaintextBodyDataPacket = encryptedPlaintextBodySplit.dataPacket()

            // generate MIME version of the email
            val plaintextBodyMime = generateMimeBody(arguments.body, arguments.attachments)

            val encryptedMimeBodySplit = encryptAndSignText(plaintextBodyMime).split(cryptoContext.pgpCrypto)
            decryptedMimeBodySessionKey = decryptSessionKey(encryptedMimeBodySplit.keyPacket())
            encryptedMimeBodyDataPacket = encryptedMimeBodySplit.dataPacket()

            val unlockedPrimaryKey = this.privateKeyRing.unlockedPrimaryKey.unlockedKey.value
            signedAndEncryptedBodyMimeForRecipients = sendPreferences.mapValues { entry ->
                try {
                    with (entry.value) {
                        if (encrypt && pgpScheme != PackageType.ProtonMail && publicKey != null) {
                            val split = cryptoContext.pgpCrypto.encryptAndSignText(plaintextBodyMime, publicKey, unlockedPrimaryKey).split(cryptoContext.pgpCrypto)
                            Pair(split.keyPacket(), split.dataPacket())
                        } else null
                    }
                } catch (e: CryptoException) {
                    logger.e("Exception encrypting and signing Mime body for recipient", e)
                    null
                }
            }.filterNullValues()
        }

        // Generate package for each recipient.
        val emailPackages = mutableMapOf<Email, me.proton.android.calendar.domain.model.EncryptedPackage?>()
        sendPreferences.forEach { entry ->
            emailPackages[entry.key] = generateEmailPackageUseCase.invoke/*OrNull*/(
                signedAndEncryptedBodyMimeForRecipients[entry.key],
                entry.key,
                entry.value,
                decryptedAttachmentSessionKeys,
                decryptedPlaintextBodySessionKey,
                encryptedPlaintextBodyDataPacket,
                decryptedMimeBodySessionKey,
                encryptedMimeBodyDataPacket
            )
        }

        val failedPackageEmails = emailPackages.filterValues { it == null }.keys
        if (failedPackageEmails.isNotEmpty())
            return Result.Error.GeneratingEmailPackages(failedPackageEmails.toList())

        // Send Email, Packages and attachmentKeyPackets.
        val receipt = emailMessageRepository.sendEmailDirect(
            userId = sender.userId,
            encryptedEmail = encryptedEmail,
            encryptedPackages = emailPackages.filterNullValues().values.toList(),
            attachmentKeys = encodedAttachmentKeyPackets //.ifEmpty { null }
        )
        return Result.Success(receipt)
    }

    /**
     * Correctly encode and format plaintext email body
     */
    private fun generateMimeBody(body: String, attachments: List<Arguments.Attachment>): String {

        val boundary = "---------------------${Hex.encode(Random.randBytes(16))}"

        val stringWriter = StringWriter()
        FoldedLineWriter(stringWriter).use {
            it.write(body, true, Charsets.UTF_8)
        }
        val quotedPrintableBody = stringWriter.toString()

        return """
Content-Type: multipart/mixed; boundary=${boundary.substring(2)}

$boundary
Content-Transfer-Encoding: quoted-printable
Content-Type: text/plain; charset=utf-8

$quotedPrintableBody
${attachments.map { "${boundary}\n${generateMimeAttachment(it)}" }.joinToString(separator = "\n")}
$boundary--
""".trimIndent()

    }

    /**
     * Correctly encode and format [Arguments.Attachment]
     */
    private fun generateMimeAttachment(attachment: Arguments.Attachment): String {

        val stringWriter = StringWriter()
        FoldedLineWriter(stringWriter).use {
            it.write(Base64.encode(attachment.bytes))
        }
        val foldedAttachment = stringWriter.toString()

        return """
Content-Type: ${attachment.mimeType}; filename="${attachment.fileName}"; name="${attachment.fileName}"
Content-Transfer-Encoding: base64
Content-Disposition: attachment; filename="${attachment.fileName}"; name="${attachment.fileName}"

$foldedAttachment
""".trimIndent()

    }

}
