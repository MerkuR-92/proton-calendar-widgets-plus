package me.proton.android.calendar.domain.usecase

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import me.proton.android.calendar.common.CustomICalPropertyParameter.X_PM_TOKEN
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.common.utils.ICalUtilsImpl.adjustIncomingAllDayEvent
import me.proton.android.calendar.common.utils.ICalUtilsImpl.extractEmail
import me.proton.android.calendar.common.utils.ICalUtilsImpl.generateXPmToken
import me.proton.android.calendar.common.utils.ICalUtilsImpl.sanitise
import me.proton.android.calendar.common.utils.ProtonUtilsImpl.canonicalizeProtonEmail
import me.proton.android.calendar.common.utils.getAddressesOrNull
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.domain.entity.UserId
import me.proton.core.key.domain.entity.key.PublicKey
import me.proton.core.key.domain.extension.publicKeyRing
import me.proton.core.key.domain.repository.PublicAddressRepository
import me.proton.core.key.domain.repository.Source
import me.proton.core.key.domain.verifyText
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.entity.UserAddress
import me.proton.core.util.kotlin.equalsNoCase
import me.proton.core.util.kotlin.toBoolean
import javax.inject.Inject

class TransformEventUseCase @Inject constructor(
    private val json: Json,
    private val database: AppDatabase,
    private val userManager: UserManager,
    private val logger: Logger,
    private val valueStoreProvider: ValueStoreProvider,
    private val crypto: Crypto,
    private val iCal: ICalUtilsImpl,
    private val publicAddressRepository: PublicAddressRepository,
    private val cryptoContext: CryptoContext
) : UseCase { // TODO ADD TEST

    suspend fun execute(eventEntity: EventEntity) : Event? {

        val calendarEntity = database.calendarsDao().selectById(eventEntity.calendarId) ?: return null
        val userId = calendarEntity.fkUserId
        val calendarPrivateKeys = database.calendarKeysDao().select(eventEntity.calendarId).filter { it.isActive }.map { it.privateKey }
        if (calendarPrivateKeys.isNullOrEmpty()) {
            logger.e("TransformEventUseCase, calendarKey is null")
            return null
        }
        val calendarPassphrase = database.passphrasesDao().select(eventEntity.calendarId).map { it.toPassphrase(json) }.firstOrNull() { it.isActive }
        if (calendarPassphrase == null) {
            logger.e("TransformEventUseCase, calendarPassphrase is null")
            return null
        }
        val keyPassphrase = valueStoreProvider.provideValueStore(userId).getStringFromSet(ValueSet.CALENDAR_PASSPHRASE, calendarPassphrase.id)
        if (keyPassphrase == null) {
            logger.e("TransformEventUseCase, keyPassphrase is null")
            return null
        }
        val userAddresses = userManager.getAddressesOrNull(UserId(userId))
        if (userAddresses == null) {
            logger.e("TransformEventUseCase, userAddresses is null")
            return null
        }

        val calendarParts = mutableListOf<String>()
        val verificationStatuses: MutableList<Event.SignatureVerification> = mutableListOf()
        val decryptionStatuses: MutableList<Event.DecryptionStatus> = mutableListOf()

        coroutineScope {

            // process Shared Events
            val processedSharedEvents = async {
                eventEntity.sharedEvents.map {
                    json.decodeFromJsonElement<Event.EventPart.Shared>(it)
                }.map { sharedEvent ->
                    getPlainText(
                        eventEntity.sharedKeyPacket,
                        calendarPrivateKeys,
                        keyPassphrase,
                        sharedEvent,
                        getPublicKeysForAuthor(UserId(userId), sharedEvent, userAddresses)
                    )
                }
            }

            // process Calendar Events
            val processedCalendarEvents = async {
                eventEntity.calendarEvents.map {
                    json.decodeFromJsonElement<Event.EventPart.Calendar>(it)
                }.map { calendarEvent ->
                    getPlainText(
                        eventEntity.calendarKeyPacket,
                        calendarPrivateKeys,
                        keyPassphrase,
                        calendarEvent,
                        getPublicKeysForAuthor(UserId(userId), calendarEvent, userAddresses)
                    )
                }
            }

            // process Personal Events
            val processedPersonalEvents = async {
                eventEntity.personalEvents.map {
                    json.decodeFromJsonElement<Event.EventPart.Personal>(it)
                }.map { personalEvent ->
                    getPlainText(
                        null, // personal parts are only signed
                        calendarPrivateKeys,
                        keyPassphrase,
                        personalEvent,
                        getPublicKeysForAuthor(UserId(userId), personalEvent, userAddresses)
                    )
                }
            }

            // process Attendees Events
            val processedAttendeesEvemts = async {
                eventEntity.attendeesEvents.map {
                    json.decodeFromJsonElement<Event.EventPart.Attendee>(it)
                }.map { attendeeEvent ->
                    getPlainText(
                        eventEntity.sharedKeyPacket,
                        calendarPrivateKeys,
                        keyPassphrase,
                        attendeeEvent,
                        getPublicKeysForAuthor(UserId(userId), attendeeEvent, userAddresses)
                    )
                }
            }

            listOf(processedSharedEvents, processedCalendarEvents, processedPersonalEvents, processedAttendeesEvemts)
                .awaitAll().flatten().forEach { processResult ->
                    processResult.plainText?.let { calendarParts.add(it) }
                    decryptionStatuses.add(processResult.decryptionStatus)
                    verificationStatuses.add(processResult.signatureVerification)
                }

        }

        if (calendarParts.isEmpty()) return null

        val iCalendar = iCal.mergeCalendarPartsIntoICalendar(calendarParts)

        if (iCalendar == null || iCalendar.events.isEmpty() || iCalendar.events.first().sanitise() == false) return null

        // Cross reference unencrypted Attendees and encrypted AttendeesEvents data to update participation status
        var currentUserAttendeeId: String? = null
        if (!iCalendar.events.first().attendees.isNullOrEmpty()) {
            val canonicalUserEmails = userAddresses.map { canonicalizeProtonEmail(it.email, forceCanonicalization = true) }
            val attendees = eventEntity.attendees.map {
                json.decodeFromJsonElement<Event.AttendeeStatusEvent>(it)
            }
            iCalendar.events.first().attendees.forEach { attendee ->
                val attendeeToken = attendee.getParameter(X_PM_TOKEN) ?: generateXPmToken(
                    canonicalizeProtonEmail(attendee.extractEmail() ?: ""), // Do not force canonicalization here
                    iCalendar.events.first().uid.value
                )
                val attendeeStatusEvent = attendees.find { it.token == attendeeToken }
                if (attendeeStatusEvent != null) {
                    val status = attendeeStatusEvent.participationStatus
                    if (canonicalUserEmails.any { it == canonicalizeProtonEmail(attendee.extractEmail() ?: "", forceCanonicalization = true) }) currentUserAttendeeId =
                        attendeeStatusEvent.id
                    attendee.participationStatus = status
                }
            }
        }

        // TODO move sanitising to helper function?
        iCalendar.adjustIncomingAllDayEvent()

        return Event.from(
            id = eventEntity.id,
            calendar = Calendar(
                calendarEntity.id,
                calendarEntity.name,
                calendarEntity.color,
                calendarEntity.flags,
                calendarEntity.display == 1,
                calendarEntity.type
            ),
            iCalendar = iCalendar,
            verificationStatus = when {
                verificationStatuses.all { it == Event.SignatureVerification.SUCCESS } -> {
                    Event.SignatureVerification.SUCCESS
                }
                verificationStatuses.any { it == Event.SignatureVerification.FAILURE } -> {
                    Event.SignatureVerification.FAILURE
                }
                verificationStatuses.any { it == Event.SignatureVerification.SIGNED_BUT_NO_KEYS } -> {
                    Event.SignatureVerification.SIGNED_BUT_NO_KEYS
                }
                verificationStatuses.all { it == Event.SignatureVerification.NOT_SIGNED || it == Event.SignatureVerification.SUCCESS } -> {
                    // if at least 1 part is NOT_SIGNED but the rest is NOT_SIGNED or SUCCESS, then we treat entire Event as NOT_SIGNED
                    Event.SignatureVerification.NOT_SIGNED
                }
                else -> null
            },
            decryptionStatus = when {
                decryptionStatuses.all { it == Event.DecryptionStatus.SUCCESS } -> {
                    Event.DecryptionStatus.SUCCESS
                }
                decryptionStatuses.any { it == Event.DecryptionStatus.FAILURE } -> {
                    Event.DecryptionStatus.FAILURE
                }
                else -> null
            },
            currentUserAttendeeId = currentUserAttendeeId,
            sharedEventId = eventEntity.sharedEventId,
            isProtonProtonInvite = eventEntity.isProtonProtonInvite?.toBoolean()
        )

    }

    private data class ProcessResult(
        val plainText: String?,
        val decryptionStatus: Event.DecryptionStatus,
        val signatureVerification: Event.SignatureVerification
    )

    private suspend fun getPublicKeysForAuthor(userId: UserId, eventPart: Event.EventPart, userAddresses: List<UserAddress>): List<PublicKey> {

        // TODO fix when we properly get public keys for authors
        return emptyList()

        /*return kotlin.runCatching {
            userAddresses.firstOrNull {
                canonicalizeProtonEmail(it.email, forceCanonicalization = true).equalsNoCase(
                    canonicalizeProtonEmail(eventPart.author, forceCanonicalization = true)
                )
            }?.let {
                // current User is the Author of this EventPart
                it.publicKeyRing(cryptoContext).keys

                // Source.LocalIfAvailable, because we can't hit the network here -- if there are no keys available in local cache then it's too bad
            } ?: publicAddressRepository.getPublicAddress(userId, eventPart.author, source = Source.LocalIfAvailable).keys.map { it.publicKey }
        }.getOrNull() ?: emptyList()*/
    }

    /**
     * Get plaintext payload or decrypt & check signature if necessary.
     */
    private fun getPlainText(keyPacket: String?,
                             privateKeys: List<String>,
                             keyPassphrase: String,
                             eventPart: Event.EventPart,
                             authorsPublicKeys: List<PublicKey>
    ): ProcessResult {

        // decrypt if necessary
        val plainText = if (eventPart.isEncrypted) {
            if (keyPacket != null) {
                val cipherText = Ciphertext.from(keyPacket, eventPart.data)
                crypto.decryptText(cipherText.asArmoredPGPMessage(), privateKeys, keyPassphrase.toByteArray())
            } else null
        } else {
            eventPart.data
        }

        return if (plainText != null) {

            var signatureVerification: Event.SignatureVerification = Event.SignatureVerification.FAILURE

            // verify signature if necessary
            if (eventPart.isSigned) {

                if (eventPart.signature != null) {

                    if (authorsPublicKeys.isEmpty()) {
                        signatureVerification = Event.SignatureVerification.SIGNED_BUT_NO_KEYS
                    } else {

                        val signatureOk = eventPart.signature?.let { signature ->
                            authorsPublicKeys.any {
                                it.verifyText(cryptoContext, plainText, signature)
                            }
                        } ?: false

                        if (signatureOk) {
                            signatureVerification = Event.SignatureVerification.SUCCESS
                        } else {
                            signatureVerification = Event.SignatureVerification.FAILURE
                            logger.v("signature not okay for ${plainText}")
                        }
                    }

                } else {
                    logger.e("EventPart ${eventPart.javaClass} is signed but there is no signature")
                    signatureVerification = Event.SignatureVerification.FAILURE
                }

            } else {
                signatureVerification = Event.SignatureVerification.NOT_SIGNED
            }

            ProcessResult(plainText, Event.DecryptionStatus.SUCCESS, signatureVerification)
        } else {
            ProcessResult(null, Event.DecryptionStatus.FAILURE, Event.SignatureVerification.FAILURE)
        }

    }
}
