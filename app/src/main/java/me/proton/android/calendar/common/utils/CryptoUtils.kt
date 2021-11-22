package me.proton.android.calendar.common.utils

import me.proton.android.calendar.domain.Logger
import me.proton.core.crypto.common.context.CryptoContext
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

        val encryptException = kotlin.runCatching { encryptData(testData).split(cryptoContext.pgpCrypto) }.exceptionOrNull()

        encryptException?.let {
            logger.e("can't encrypt data in isValidForEncryption", it)
        }

        val signException = kotlin.runCatching { signData(testData) }.exceptionOrNull()

        signException?.let {
            logger.e("can't sign data in isValidForEncryption", it)
        }

        encryptException == null && signException == null

    }

}
