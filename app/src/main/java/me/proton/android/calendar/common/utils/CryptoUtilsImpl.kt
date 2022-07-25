package me.proton.android.calendar.common.utils

import com.proton.gopenpgp.crypto.Crypto
import ezvcard.VCard
import me.proton.android.calendar.domain.utils.CryptoUtils
import me.proton.android.calendar.domain.utils.CryptoUtils.*
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.crypto.common.pgp.Armored
import me.proton.core.crypto.common.pgp.getFingerprintOrNull
import me.proton.core.key.domain.entity.key.PublicAddress
import me.proton.core.key.domain.entity.key.PublicAddressKey
import me.proton.core.key.domain.entity.key.PublicKey
import me.proton.core.key.domain.entity.key.Recipient

object CryptoUtilsImpl : CryptoUtils {

    override fun extractPinnedKey(
        purpose: PinnedKeyPurpose,
        vCardEmail: String,
        vCard: VCard,
        publicAddress: PublicAddress,
        cryptoContext: CryptoContext
    ): PinnedKeyOrError {

        val isInternal = publicAddress.recipient == Recipient.Internal
        val publicAddressKey = publicAddress.keys.firstOrNull { it.publicKey.isPrimary }

        val propertyGroup = vCard.getGroupForEmail(vCardEmail) ?: return PinnedKeyOrError.Error.NoEmailInVCard

        val vCardPublicKeys = vCard.getKeysForGroup(propertyGroup)

        // TODO in theory we should only get keys that are valid for sending
        val pinnedPublicKey = vCardPublicKeys.firstOrNull() ?: return PinnedKeyOrError.Error.NoKeysAvailable

        val pinnedKeyFingerprint = cryptoContext.pgpCrypto.getFingerprintOrNull(pinnedPublicKey) ?: return PinnedKeyOrError.Error.TrustedKeysInvalid
        val matchingPublicAddressKey = publicAddress.keys.find { cryptoContext.pgpCrypto.getFingerprintOrNull(it.publicKey.key) == pinnedKeyFingerprint }

        // pinned key is not in the public key repository
        if (isInternal && matchingPublicAddressKey == null) return PinnedKeyOrError.Error.TrustedKeysInvalid

        // pinned key is compromised
        if (matchingPublicAddressKey?.isCompromised() == true) return PinnedKeyOrError.Error.TrustedKeysInvalid

        // pinned key is obsolete (invalid for encrypting but we can still verify)
        if (matchingPublicAddressKey?.isObsolete() == true && purpose == PinnedKeyPurpose.Encrypting) return PinnedKeyOrError.Error.TrustedKeysInvalid

        // pinned key is expired
        if (isKeyExpired(pinnedPublicKey) == true) return PinnedKeyOrError.Error.TrustedKeysInvalid

        // pinned key is revoked
        if (isKeyRevoked(pinnedPublicKey) == true) return PinnedKeyOrError.Error.TrustedKeysInvalid

        if (publicAddressKey != null && (publicAddressKey.isObsolete() || publicAddressKey.isCompromised())) return PinnedKeyOrError.Error.PublicKeysInvalid

        return PinnedKeyOrError.Success(PublicKey(pinnedPublicKey, true, true, true, true))
    }

    /**
     * If true, do not use the key for encrypting, nor for signature verification.
     */
    private fun PublicAddressKey.isCompromised() = !(this.flags and 1 == 1)

    /**
     * If true, do not use the key to encrypt new messages, but can verify signatures.
     */
    private fun PublicAddressKey.isObsolete() = !(this.flags and 2 == 2)

    private fun isKeyExpired(armoredKey: Armored): Boolean? {
        return kotlin.runCatching { Crypto.newKeyFromArmored(armoredKey).isExpired }.getOrNull()
    }

    private fun isKeyRevoked(armoredKey: Armored): Boolean? {
        return kotlin.runCatching { Crypto.newKeyFromArmored(armoredKey).isRevoked }.getOrNull()
    }


}

