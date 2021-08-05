package me.proton.android.calendar.common

import me.proton.android.calendar.domain.Logger
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.crypto.common.pgp.exception.CryptoException
import me.proton.core.crypto.common.pgp.split
import me.proton.core.key.domain.encryptData
import me.proton.core.key.domain.signData
import me.proton.core.key.domain.useKeys
import me.proton.core.user.domain.entity.UserAddress

/**
 * Temporary workaround for checking if [UserAddress] is valid for encryption or we should log out the User.
 */
fun UserAddress.isValidForEncryption(cryptoContext: CryptoContext, logger: Logger): Boolean {
    
    return this.useKeys(cryptoContext) {

        val testData = "Test".encodeToByteArray()

        val encryptedData = try {
            encryptData(testData).split(cryptoContext.pgpCrypto)
        } catch (e: CryptoException) {
            logger.e("can't encrypt data in isValidForEncryption", e)
            null
        }
        val signedData = try {
            signData(testData)
        } catch (e: CryptoException) {
            logger.e("can't sign data in isValidForEncryption", e)
            null
        }

        encryptedData != null && signedData != null

    }

}
