package me.proton.android.calendar.uitest.rule

import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.uitest.BaseTest
import me.proton.core.test.quark.data.User
import org.junit.rules.ExternalResource

val BaseTest.LogoutAllRule: ExternalResource
    get() = object: ExternalResource()  {
        override fun before() {
            runBlocking {
                quark.jailUnban()
                loginTestHelper.logoutAll()
            }
        }
    }

fun BaseTest.userLoginRule(
    testUser: User,
    shouldSeedUser: Boolean
): ExternalResource = object: ExternalResource() {
    override fun before() {
        runBlocking {
            if (shouldSeedUser) {
                if (testUser.isPaid)
                    quark.seedNewSubscriber(testUser)
                else
                    quark.userCreate(testUser)
            }
            loginTestHelper.login(testUser.name, testUser.password)
        }
    }
}