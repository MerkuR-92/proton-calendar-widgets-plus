package me.proton.android.calendar.domain.usecase

import com.google.crypto.tink.subtle.Base64
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.crypto.common.pgp.dataPacket
import me.proton.core.crypto.common.pgp.keyPacket
import me.proton.core.crypto.common.pgp.split
import me.proton.core.key.domain.decryptSessionKey
import me.proton.core.key.domain.encryptAndSignText
import me.proton.core.key.domain.useKeys
import me.proton.core.mailmessage.domain.encryptAndSignAttachmentOrNull
import me.proton.core.mailmessage.domain.entity.*
import me.proton.core.mailmessage.domain.repository.EmailMessageRepository
import me.proton.core.mailmessage.domain.usecase.GenerateEmailPackage
import me.proton.core.mailmessage.domain.usecase.GetRecipientPublicAddresses
import me.proton.core.mailmessage.domain.usecase.SendEmailDirect
import me.proton.core.mailmessage.domain.usecase.invokeOrNull
import me.proton.core.user.domain.entity.UserAddress
import me.proton.core.util.kotlin.filterNullValues
import me.proton.core.util.kotlin.nullIfBlank
import java.io.InputStream
import javax.inject.Inject

class SendEmailDirect @Inject constructor(
    private val emailMessageRepository: EmailMessageRepository,
    private val getRecipientPublicAddresses: GetRecipientPublicAddresses,
    private val generateEmailPackage: GenerateEmailPackage,
    private val cryptoContext: CryptoContext
) {

    data class Arguments(
        val subject: String,
        val body: String,
        val mimeType: String,
        val toEmailList: List<String>,
        val attachments: List<Attachment>
    ) {

        fun toArguments() = SendEmailDirect.Arguments(
            this.subject,
            this.body,
            this.mimeType,
            this.toEmailList,
            this.attachments.map { it.toAttachment() /*TODO remove helper call when we move to core*/ })

        data class Attachment(
            val fileName: Filename,
            val fileSize: Int,
            val mimeType: String,
            val inputStream: InputStream
        ) {
            fun toAttachment() =
                SendEmailDirect.Arguments.Attachment(this.fileName, this.fileSize, this.mimeType, this.inputStream)
        }
    }

    sealed class Result {
        data class Success(val receipt: EmailReceipt) : Result()

        sealed class Error : Result() {
            data class GettingPublicAddressKeys(val emailAddresses: List<String>) : Error()
            data class GeneratingEmailPackages(val emailAddresses: List<String>) : Error()
            data class EncryptingAttachments(val attachmentFileNames: List<String>) : Error()
        }
    }

    suspend operator fun invoke(
        sender: UserAddress,
        arguments: Arguments,
        sendPreferences: Map<Email, ObtainSendPreferencesUseCase.SendPreferences>
    ): Result {

        // TODO handle sendPreferences
        // TODO FIXME remove this step and use sendPreferences for this
        // Get public address keys for recipients.
        val publicAddresses = getRecipientPublicAddresses.invoke(sender.userId, arguments.toEmailList)
        val failedEmails = publicAddresses.filterValues { it == null }.keys
        if (failedEmails.isNotEmpty())
            return Result.Error.GettingPublicAddressKeys(failedEmails.toList())

        // Encrypt and sign attachments and body, create payload for sender.
        val decryptedAttachmentSessionKeys = mutableListOf<ByteArray>()
        val encodedAttachmentKeyPackets = mutableListOf<String>()

        lateinit var decryptedBodySessionKey: ByteArray
        lateinit var encryptedBodyDataPacket: ByteArray
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
            // TODO: sending works with this empty as well
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
            val encryptedBodySplit = encryptedBodyPgpMessage.split(cryptoContext.pgpCrypto)
            decryptedBodySessionKey = decryptSessionKey(encryptedBodySplit.keyPacket())
            encryptedBodyDataPacket = encryptedBodySplit.dataPacket()
        }

        // Generate package for each recipient.
        val emailPackages = mutableMapOf<Email, EncryptedPackage?>()
        publicAddresses.filterNullValues().values.forEach { recipientPublicAddress ->
            emailPackages[recipientPublicAddress.email] = generateEmailPackage.invokeOrNull(
                arguments.toArguments(),
                recipientPublicAddress,
                decryptedAttachmentSessionKeys,
                decryptedBodySessionKey,
                encryptedBodyDataPacket
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
            attachmentKeys = encodedAttachmentKeyPackets
        )
        return Result.Success(receipt)
    }
}
