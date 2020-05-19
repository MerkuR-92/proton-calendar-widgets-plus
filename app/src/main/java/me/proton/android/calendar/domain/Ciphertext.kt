package me.proton.android.calendar.domain

import com.proton.gopenpgp.armor.Armor
import com.proton.gopenpgp.crypto.PGPSplitMessage

data class Ciphertext(
    private val keyPacket: ByteArray, // TODO multiple?
    private val dataPacket: ByteArray // TODO multiple?
) {

    val encodedKeyPacket: String = com.google.crypto.tink.subtle.Base64.encode(keyPacket)
    val encodedDataPacket: String = com.google.crypto.tink.subtle.Base64.encode(dataPacket)

    fun asArmoredPGPMessage(): String {
        //val binaryData = com.google.crypto.tink.subtle.Base64.decode(encodedKeyPacket, com.google.crypto.tink.subtle.Base64.DEFAULT) + com.google.crypto.tink.subtle.Base64.decode(encodedDataPacket, com.google.crypto.tink.subtle.Base64.DEFAULT)
        //return Armor.armorWithType(binaryData, "PGP MESSAGE")
        return PGPSplitMessage(keyPacket, dataPacket).armored
    }

    companion object {

        fun from(pgpMessage: String): Ciphertext {
            val pgpSplitMessage = PGPSplitMessage(pgpMessage)
            return Ciphertext(pgpSplitMessage.keyPacket, pgpSplitMessage.dataPacket)
        }

        fun from(encodedKeyPacket: String, encodedDataPacket: String): Ciphertext {
            return Ciphertext(com.google.crypto.tink.subtle.Base64.decode(encodedKeyPacket), com.google.crypto.tink.subtle.Base64.decode(encodedDataPacket))
        }

    }
}