package me.proton.android.calendar.common

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

internal class AndroidUtilsTest {

    @Test
    fun `canonize proton emails`() {
        val emails = arrayListOf<String>()
        emails.add("Test.address-1_2+group@protonmail.com")
        val canonizedEmails = canonizeProtonEmails(emails)
        assertThat(canonizedEmails["Test.address-1_2+group@protonmail.com"]).isEqualTo("testaddress12@protonmail.com")
    }
}
