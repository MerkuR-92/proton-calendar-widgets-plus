package me.proton.android.calendar.common

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class CryptoImplTest {

    private val gson = Gson()
    private val crypto = CryptoImpl(TestsLogger)

    @Test
    fun salted_user_passphrase_is_generated_correctly() {
        val userPassphrase =
            crypto.generateUserPassphrase("123".toByteArray(), "QDaXk39fSJY9ZHJQKrj8yA==")
        assertEquals("7NgO4d0h72zt4XuFLOUbg352vhrn.tu", String(userPassphrase))
    }

    @Test
    fun check_passphrase_for_a_key() {
        assertTrue(
            crypto.checkPassphrase(
                privateKey,
                "7NgO4d0h72zt4XuFLOUbg352vhrn.tu".toByteArray()
            )
        )
        assertFalse(crypto.checkPassphrase(privateKey, "incorrect passphrase".toByteArray()))
    }

    @Test
    fun sign_text_detached() {
        assertNotNull(
            crypto.signTextDetached(
                "plaintext",
                privateKey,
                "7NgO4d0h72zt4XuFLOUbg352vhrn.tu".toByteArray()
            )
        )
        assertNull(
            crypto.signTextDetached(
                "plaintext",
                privateKey,
                "incorrect passphrase".toByteArray()
            )
        )
    }

    @Test
    fun verify_detached_signature() {
        val plainText = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:RZg4WgNwXdrvlyH08Lw2DCHFeJZ6@proton.me
            DTSTAMP:20200622T151718Z
            DTSTART;TZID=Europe/Zurich:20200622T163000
            DTEND;TZID=Europe/Zurich:20200622T173000
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val armoredSignature = """
            -----BEGIN PGP SIGNATURE-----
            Version: OpenPGP.js v4.10.4
            Comment: https://openpgpjs.org

            wsBcBAEBCAAGBQJe8MttAAoJEBHDHo5eB/TQ528H/ihwYXpXdPyEYEA0M/pk
            X10+h2CHnjsqBOohJzjmAXB0kDvIZWOc463Rd5kIC5PdgExOi07nnAcFxdHF
            G/bXC+XBrzQs73/4UGEda89AVZPUUuwIl2pRPbVNH9VroMtqHVXr5YLJIVcM
            Kp4QLAUTYKp55PS3xl9cqIA+XogNpwUmYkYmlvTkzZSlYl+wDbd7yuLDEhhV
            TW1ocAFWnKnEUIHRVSTsPyszF2GSdXWl79I4Fq1SsjbgwI0Pg2VWJFUG2BOs
            Z/MmdHadpuLyW6feMHM3VFpL/Gt9OqnfFed89Ats/J9QRUQVOUJ5gwvuz2Yq
            nMK7xBOUEUE93arL5tQk7dE=
            =fXLv
            -----END PGP SIGNATURE-----
        """.trimIndent()

        assertTrue(crypto.verifyTextDetached(plainText, armoredSignature, listOf(armoredPublicKey)))
    }

    @Test
    fun decrypt_text_with_private_key() {

        val decryptedPassphrase = crypto.decryptText(
            memberPassphrase,
            addressKeyForMemberPassphrase,
            "7NgO4d0h72zt4XuFLOUbg352vhrn.tu".toByteArray()
        )

        assertEquals(decryptedPassphrase, "NDQy8eVB/+qQMJagmkDR2iQOsiHSI3k8XummTgguRWI=")
    }

    @Test
    fun encrypt_decrypt_text_with_keypair() {

        val plainText = "Proton"

        val encrypted = crypto.encryptText(plainText, crypto.getArmoredPublicKey(calendarPrivateKey)!!)

        assertNotNull(encrypted)

        assertEquals(crypto.decryptText(encrypted!!, calendarPrivateKey, calendarPrivateKeyPassphrase.toByteArray()), plainText)
    }

    @Test
    fun encrypt_text_with_keypacket() {

        val plainText = "Proton"
        val encodedKeyPacket = "wV4DP+KI9zrxP8QSAQdAiDyH8M+RGVtpZSOXJUvV+EpRvLk8PdmolbxMX83MYFowMr3hUsQyNQzUZt7SzgDzWpA9+hd3eUp6gfUIfDHH6xreSRa6ALfhVKkEjuhGxrtv"
        val sessionKey = crypto.decryptSessionKey(encodedKeyPacket, calendarPrivateKey, calendarPrivateKeyPassphrase.toByteArray())!!

        val encrypted = crypto.encryptText(plainText, sessionKey)

        assertNotNull(encrypted)
    }

    @Test
    fun decrypt_session_key() {
        val encodedKeyPacket = "wV4DP+KI9zrxP8QSAQdAiDyH8M+RGVtpZSOXJUvV+EpRvLk8PdmolbxMX83MYFowMr3hUsQyNQzUZt7SzgDzWpA9+hd3eUp6gfUIfDHH6xreSRa6ALfhVKkEjuhGxrtv"

        assertNotNull(crypto.decryptSessionKey(encodedKeyPacket, calendarPrivateKey, calendarPrivateKeyPassphrase.toByteArray()))
    }

    val memberPassphrase = """
        -----BEGIN PGP MESSAGE-----
        Version: ProtonMail

        wcBMA5kajsUECZmgAQf/VjkxecbTAGmNOy4WBf9xQhf56RpFnD4Ei9rrSPJp
        x/NZ1xe4d1KNWNtyvE3nIz+YUo6VR0ppwy8B0syv3I2CF1KJ9pImedmuM6ei
        6n8aem5FGTW4iExg4bETyNEfrwMIpBbwqeddfK6HTzhcyxwrfqAZlzAF+bD0
        QtyRrpU4iMUMPPKVMoaAj2zBbzoC/bIcsjfN7IfjzEysYNjBIA2q6OrG6UR1
        d4f+xv3MWhGDP2S3zT6aNnC9Qd1pIBnhV4uY4XEQFjRZu+9RnOWHpm0EKzQj
        Fih7zRql/0pnyZdPylrhRQEieJHy3wX16RN541EbpFmSv6byTyybNl4woEMJ
        +tJkAWRf2tTN4rVzdl00N48ukXbudrJ2oTuF6S5Xo/AhJdx1X53Qmvg1Hxrq
        j37Lr3Tc79fiRgt1FJG8+RMCs74LNAOw8rmhHH/HW0h021G9ysBSiWj0Jo9G
        JPF0rTEhspmRS9I6mA==
        =bKDp
        -----END PGP MESSAGE-----
    """.trimIndent()

    val memberPassphraseSignature = """
        -----BEGIN PGP SIGNATURE-----
        Version: ProtonMail

        wsBcBAEBCAAGBQJeSq3vAAoJEBHDHo5eB/TQmZIH/i5VX8/mUK94/0XdJK4r
        t4lPjgwtuMmurnATEDPIoy8TXuolCstz367Jr7GG4LyRxiY3mxXB4dFxNiWZ
        uSZ4pEeTkzzwy82ZKD+W4L6YO2QXJUifPnZSGUaJ/gW/se6X6eYOs6bbimD5
        JCFGWRQHAgGckalT5QM5VmhfDlcxM/RYBL9BmWH8BZzDpaLHinGrsvau4PQa
        61JC87FEYeTADDi5VhPZy+ikcwWHTsAwB30gZ+kw9xD2QbDP+ZOQfhwvQJoF
        3CZ++mxFBIhxa07nbh0l9kUUo16qRmo+PqyjfwDUyjaH3U6wag33tmmZx3zC
        bZ4h68M9RryfzY3KpB6QYuQ=
        =BXA3
        -----END PGP SIGNATURE-----

    """.trimIndent()

    val addressKeyForMemberPassphrase = """
        -----BEGIN PGP PRIVATE KEY BLOCK-----
        Version: ProtonMail

        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
        dlEt7f8XNvX3HxQw9w==
        =FW0u
        -----END PGP PRIVATE KEY BLOCK-----
    """.trimIndent()

    // adamtst on blue
    val armoredPublicKey = """
            -----BEGIN PGP PUBLIC KEY BLOCK-----
            Version: ProtonMail

            xsBNBF1BfxUBCADUpiiG3AhQK08E2nBmQ50XeztOWArmknINQV41pqGFW5VQ
            kfbQ3FYsANhLGqbDBQ0XxmocjKL7W7W8Y4xmHCGgkCUy6gAqGbi+sXY9Sl8x
            qQNHuZDhWVdqT8+Rtv+DRxp/XrGkzC1U8CBYUmmKS92ldy0/zZIvgQXT6t5Q
            +v+BeUSv4jCsnY3BE0UBOljtrTXlOcXRZHQxORWG+kon0qgcJERdwwzhxY6e
            T8jEfAfJY0hzQaYg+6bj6ZR0zkMtY2Psq2M05kzEw4On/dezZETAu1e9fSqf
            k1mp+H6BeLJ9RUyrFK/PqIO48+pU8CmAvTdx5eIihyOM16CFg/3GgV85ABEB
            AAHNMWFkYW10c3RAcHJvdG9ubWFpbC5ibHVlIDxhZGFtdHN0QHByb3Rvbm1h
            aWwuYmx1ZT7CwHIEEwEIABwFAl1BfxUJEBHDHo5eB/TQAhsDAhkBAgsJAhUI
            AAoJEBHDHo5eB/TQx6IIAAg2A2ZMkzGV+vZPbqAMoAEO+dpG+dq9C93Ui4Hv
            oVHpcSTolVM522r81Yc48xdhbnFz9HLDkicoBzXo40utgQ7bF4iKD4lQztfh
            6+9l+IBNu+1XmdW+laMybygtPh+H4YPxLZA9O6FYRyUcTjlZYFFxipz9pc9q
            I58tDHIILzfjZPCC6reiJpbxJOgp07PV3ZnJqLDIkFPlPkxyqymfuWHnPOJM
            5RxvHnu04ptsp/Z/xbgUra2JEyVLA7gC/yznxfQ58087pCupKqQwepA3zHmE
            CS6vk7uuNp++D9JajjtFsu4piP4cTNVvMqnDXWn0uzwrhhw/fZnnHSllXmBw
            gmPOwE0EXUF/FQEIAMgCI+srSwdQlIpz+n+mlSpS0jPXvRYoL9QgMOdzR3kA
            W5sM1OW2Z7ROlBEZ7ycurpe4Sa/SaKfjtf4wOs2hmpxecL9JxL0x3KGEaSeE
            IiYIkMb4TnSLR9vfowVdReOMTs5RpxMxQL+xmz3nChwLEIF/amAo/ucnXLbU
            NvYFkOpzdtxtN0dy2ykUvR9rsNUiGBoIn/BYCqSXpsCY7kom8lYl039yQvGV
            LWG6vryF6gExRbW61B3yjACpR6NLi2Bqta0SDRkeg5obumxoWaJ7ltJ2uPuV
            ofOpIPXP2CO40iCLKUUZB/r+/kVx+dYfEW3Nk4r+uKsu3CCSB9AZNJRQGiEA
            EQEAAcLAaQQYAQgAEwUCXUF/FQkQEcMejl4H9NACGwwACgkQEcMejl4H9NCC
            8AgAbItodhOOJcb85EggCB1CEoFg6jOs5LgRw4810xI8HBPo/4Gk1L8YPfen
            MA1Uoz0x+3z42d49QU5HZ/hAmtDVW9KP2Sjw/axfsgB7v6sbrXgtB/OMblHX
            oqVJU4wVbQrYvxnG6YN1iX83QGGC1mYHWWDXFjZM8egN63Ocyccbywvq7q/K
            EaXlrqpxbaDW6uUXRUX8ISqDWXAAqEUcgWI1H5fqMKODQolr0yMBbqggI7Gh
            fSOnX3mZaLHqy5ElJZUrXi6J5Pq4vnJgLm1kzP632uztjEKQfEVFPUflksdQ
            P+v3eWKpb6nNTH5tV3Pmo0xvRmicdlEt7f8XNvX3HxQw9w==
            =E6ZL
            -----END PGP PUBLIC KEY BLOCK-----
        """.trimIndent()

    val calendarPrivateKey = """
        -----BEGIN PGP PRIVATE KEY BLOCK-----
        Version: ProtonMail

        xYYEXkqt7xYJKwYBBAHaRw8BAQdA8Bpv0uvXzbDe16Y4QPWWv/4WItKxVrS6
        IkTkqSk0A9T+CQMIsQeIfS3SNcdgNtjZQxirGLGKRRN1D5FU6W+tqHc7bLVu
        A2Us8ui0iTffZAiMZU7Y4ogksyZqr5XEfvr2PmgenkaLuM17ls2cZbjRZ9Id
        cc0MQ2FsZW5kYXIga2V5wngEEBYKACAFAl5Kre8GCwkHCAMCBBUICgIEFgIB
        AAIZAQIbAwIeAQAKCRBfdh714DIHPLGmAP4t+H/WYAJ0LH4ifeOkxEfcdH9d
        F4YaIvlPgOxS+0hcoAD/a379Gwu067TQUMMmZsX3yI7NR7H7KJ2RKcbIwADy
        FgDHiwReSq3vEgorBgEEAZdVAQUBAQdAtKc2+1uULJFFH+YIgbfSsbVttX8k
        4n8+Mti6OYEf6HMDAQgH/gkDCNo/D7eNisONYDItg79Y5LiTrNhlJMumDdQL
        mTvhnd85RHxAZiiRjMrnbDiCfZNi+4/JVeu3hArCYQNWHb9+XAizpsMuqP2J
        zdsV57M20XPCYQQYFggACQUCXkqt7wIbDAAKCRBfdh714DIHPClCAP4/L6EM
        NuC6z7/lBZjczr+zDf6W3d2qxPiNhOLAdmDH/wD+KZaqtTN2/Nh00CmOxEXb
        COe4F9qbz3m5vDy8ZhYTmAY=
        =zCfh
        -----END PGP PRIVATE KEY BLOCK-----
    """.trimIndent()

    val calendarPrivateKeyPassphrase = "NDQy8eVB/+qQMJagmkDR2iQOsiHSI3k8XummTgguRWI="

    val privateKey =
        "-----BEGIN PGP PRIVATE KEY BLOCK-----\nVersion: ProtonMail\n\n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\ndlEt7f8XNvX3HxQw9w==\n=FW0u\n-----END PGP PRIVATE KEY BLOCK-----\n"

}
