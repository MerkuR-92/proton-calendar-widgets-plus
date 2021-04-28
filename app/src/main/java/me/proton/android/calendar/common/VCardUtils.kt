package me.proton.android.calendar.common

import com.proton.gopenpgp.armor.Armor
import ezvcard.Ezvcard
import ezvcard.VCard
import ezvcard.property.RawProperty
import me.proton.android.calendar.domain.Logger
import me.proton.core.contact.domain.entity.Contact
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.key.domain.useKeys
import me.proton.core.key.domain.verifyText
import me.proton.core.user.domain.entity.User
import me.proton.core.util.kotlin.equalsNoCase

// TODO test
/**
 * @return Helper method to lookup VCard Extended Property [name] inside a given [group]
 */
fun VCard.getProperty(group: String, name: String): RawProperty? =
    this.extendedProperties.firstOrNull {
        it.group.equalsNoCase(group) && it.propertyName.equalsNoCase(name)
    }

/**
 * @return VCard group assigned to the [email] (this is not a "Contact Group"!)
 */
fun VCard.getGroupForEmail(email: String): String? =
    this.emails.firstOrNull {
        email.equalsNoCase(it.value)
    }?.group // TODO test

fun VCard.getKeysForGroup(group: String): List<String> =
    this.keys.filter {
        group.equalsNoCase(it.group)
    }.map {
        Armor.armorKey(it.data)
    } // TODO test

fun Contact.extractSignedVCard(user: User, cryptoContext: CryptoContext, logger: Logger): VCard? {

    val signedContactCard = this.cards.firstOrNull { it.type == 2 /* signed */ } ?: return null
    val signature = signedContactCard.signature ?: return null

    return try {

        val signatureValid = user.useKeys(cryptoContext) {
            verifyText(signedContactCard.data, signature)
        }

        if (signatureValid) {
            Ezvcard.parse(signedContactCard.data).first()
        } else null

    } catch (e: Exception) {
        logger.e("Exception parsing VCard", e)
        null
    }

}
