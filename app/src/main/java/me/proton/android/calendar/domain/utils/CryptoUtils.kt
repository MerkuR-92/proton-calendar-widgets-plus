package me.proton.android.calendar.domain.utils

import ezvcard.VCard
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.key.domain.entity.key.PublicAddress
import me.proton.core.key.domain.entity.key.PublicKey

interface CryptoUtils {
    /**
     * Extracts pinned keys from VCard and checks their validity against server-provided public keys.
     */
    fun extractPinnedKeys(
        purpose: PinnedKeysPurpose,
        vCardEmail: String,
        vCard: VCard,
        publicAddress: PublicAddress,
        cryptoContext: CryptoContext
    ): PinnedKeysOrError

    sealed class PinnedKeysOrError {
        data class Success(val pinnedPublicKeys: List<PublicKey>) : PinnedKeysOrError()

        sealed class Error : PinnedKeysOrError() {
            object NoKeysAvailable : Error()
            object NoEmailInVCard : Error()
            object TrustedKeysInvalid : Error()
            object PublicKeysInvalid : Error()
            object NotEnoughData : Error()
        }
    }

    sealed class PinnedKeysPurpose {
        object VerifyingSignature : PinnedKeysPurpose()
        object Encrypting : PinnedKeysPurpose()
    }


}
