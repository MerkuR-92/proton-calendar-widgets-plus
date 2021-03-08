package me.proton.android.calendar.domain.usecase

import kotlinx.serialization.json.Json
import me.proton.android.calendar.common.EMAIL_MIME_TYPE
import me.proton.android.calendar.common.INVITE_ICS_FILE_NAME
import me.proton.android.calendar.common.INVITE_ICS_MIME_TYPE
import me.proton.android.calendar.common.canonicalizeProtonEmail
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.Logger
import me.proton.core.domain.entity.UserId
import me.proton.core.mailmessage.domain.usecase.SendEmailDirect
import me.proton.core.user.domain.UserManager
import java.io.ByteArrayInputStream

class SendEmailUseCase(
    private val logger: Logger,
    private val sendEmailDirectUseCase: SendEmailDirect,
    private val userManager: UserManager,
    private val database: AppDatabase,
    private val json: Json
): UseCase {

    suspend fun executeToOrganizer(
        userId: UserId,
        userAttendeeEmail: String,
        userDisplayName: String,
        organizerEmail: String,
        ics: String,
        subject: String,
        body: String
    ): UseCase.Result {

        val senderAddressesId = database.addressesDao().select(userId.id, canonicalizeProtonEmail(userAttendeeEmail)).map {
            it.toAddress(json)
        }.firstOrNull()?.id ?: return UseCase.Result.Error("SendEmailUseCase failed to get address ID for sender") // TODO better error

        val senderAddress = userManager.getAddresses(userId).find {
            it.addressId.id == senderAddressesId
        }?.copy(email = userAttendeeEmail, displayName = userDisplayName) ?: return UseCase.Result.Error("SendEmailUseCase failed to get address for sender") // TODO better error

        val attachmentBytes = ics.toByteArray()

        val sendEmailArguments = SendEmailDirect.Arguments(
            subject,
            body,
            EMAIL_MIME_TYPE,
            listOf(organizerEmail),
            listOf(
                SendEmailDirect.Arguments.Attachment(
                    INVITE_ICS_FILE_NAME,
                    attachmentBytes.size,
                    INVITE_ICS_MIME_TYPE,
                    ByteArrayInputStream(attachmentBytes)
                )
            )
        )

        return when (val sendEmailResult = sendEmailDirectUseCase.invoke(senderAddress, sendEmailArguments)) {
            is SendEmailDirect.Result.Success -> return UseCase.Result.Success<Unit>()
            else -> UseCase.Result.Error("SendEmailUseCase failed to send email to organizer: $sendEmailResult")
        }
    }

}
