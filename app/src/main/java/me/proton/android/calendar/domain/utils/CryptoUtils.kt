package me.proton.android.calendar.domain.utils

import ezvcard.VCard
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.key.domain.entity.key.PublicAddress
import me.proton.core.key.domain.entity.key.PublicKey

interface CryptoUtils {
    /**
     * Extracts pinned key from VCard and checks its validity against server-provided public keys.
     */
    fun extractPinnedKey(
        purpose: PinnedKeyPurpose,
        vCardEmail: String,
        vCard: VCard,
        publicAddress: PublicAddress,
        cryptoContext: CryptoContext
    ): PinnedKeyOrError

    sealed class PinnedKeyOrError {
        data class Success(val pinnedPublicKey: PublicKey) : PinnedKeyOrError()

        sealed class Error : PinnedKeyOrError() {
            object NoKeysAvailable : Error()
            object NoEmailInVCard : Error()
            object TrustedKeysInvalid : Error()
            object PublicKeysInvalid : Error()
            object NotEnoughData : Error()
        }
    }

    sealed class PinnedKeyPurpose {
        object VerifyingSignature : PinnedKeyPurpose()
        object Encrypting : PinnedKeyPurpose()
    }


}
