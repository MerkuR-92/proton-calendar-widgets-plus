@file:Suppress("UnstableApiUsage")

import com.android.build.api.dsl.VariantDimension
import configuration.extensions.protonEnvironment
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.archivesName
import configuration.util.getTokenFromCurl

plugins {
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.10"
    id("org.sonarqube") version "3.3"
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("androidx.navigation.safeargs.kotlin")
    id("dagger.hilt.android.plugin")
    id("jacoco")
    alias(libs.plugins.gradlePlugin.proton.environmentConfig)
}

sonarqube {
    properties {
        property("sonar.projectKey", "android_calendar_proton-calendar-android_AYGvp8U7f_vcScryKn5V")
        property("sonar.qualitygate.wait", true)
    }
}

jacoco { toolVersion = "0.8.8" }
kapt { correctErrorTypes = true }

android {
    buildToolsVersion = Config.buildToolsVersion
    ndkVersion = Config.ndkVersion
    compileSdk = Config.compileSdk
    namespace = Config.applicationId

    kotlinOptions { jvmTarget = JavaVersion.VERSION_17.toString() }

    buildFeatures {
        dataBinding = true
        viewBinding = true
        compose = true
    }

    composeOptions{
        kotlinCompilerExtensionVersion = libs.versions.androidx.compose.compiler.get().toString()
    }

    signingConfigs {
        create("default") {
            storeFile = file(project.properties["keyStoreFilePath"] ?: "protonkey.jks")
            storePassword = System.getenv("KEY_STORE_PASSWORD") ?: "\"Store password\""
            keyAlias = System.getenv("KEY_STORE_KEY_ALIAS") ?: "proton"
            keyPassword = System.getenv("KEY_STORE_KEY_PASSWORD") ?: "\"Store key password\""
        }
    }

    defaultConfig {
        applicationId = Config.applicationId
        minSdk = Config.minSdk
        targetSdk = Config.targetSdk
        versionCode = Config.versionCode
        versionName = Config.versionName
        archivesName.set(Config.archivesBaseName)
        testInstrumentationRunner = Config.testInstrumentationRunner
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
        resourceConfigurations.addAll(Config.resourceConfigurations)

        buildConfigField("String", "ACCOUNT_SENTRY_DSN", null.toBuildConfigValue())

        setAssetLinksResValue("proton.me")

        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }

        protonEnvironment {
            apiPrefix = "calendar-api"
        }
    }

    flavorDimensions.add("env")
    productFlavors {
        create("dev") {
            dimension = "env"
            applicationIdSuffix = ".dev"
            resValue("string", "app_name", "Atlas Proton Calendar")

            val protonHost = "proton.black"
            protonEnvironment {
                proxyToken = getProxyToken()
                host = protonHost
            }
            setAssetLinksResValue(protonHost)
        }
        create("prod") {
            dimension = "env"
            resValue("string", "app_name", "Proton Calendar")

            protonEnvironment {
                // If we are creating a custom build (prod build that points to scientist env)
                // do not use the default pins
                useDefaultPins = true
                apiPrefix = "calendar-api"
            }
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = false // TODO turn off for initial beta release
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("default")

            val sentryDsn = System.getenv("SENTRY_DSN_NEW")
            buildConfigField("String", "SENTRY_DSN_NEW", sentryDsn.toBuildConfigValue())

            val accountSentryDsn = System.getenv("ACCOUNT_SENTRY_DSN")
            buildConfigField("String", "ACCOUNT_SENTRY_DSN", accountSentryDsn.toBuildConfigValue())
        }
        debug {
            buildConfigField("String", "SENTRY_DSN_NEW", null.toBuildConfigValue())
            enableUnitTestCoverage = true
            isDebuggable = true

            if (isGitlabCI) {
                signingConfig = signingConfigs.getByName("default")
            }
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
        unitTests.all {
            it.useJUnitPlatform()
        }
    }

    packagingOptions {
        resources.excludes.add("META-INF/licenses/**")
        resources.excludes.add("META-INF/LICENSE*")
        resources.excludes.add("META-INF/AL2.0")
        resources.excludes.add("META-INF/LGPL2.1")
    }

    sourceSets {
        getByName("androidTest").java.srcDirs("src/uiTest/java", "src/androidTest/java")
        getByName("androidTest").assets.srcDirs("src/uiTest/assets")
    }
}

configurations {
    // Remove duplicate classes (keep "org.jetbrains").
    implementation.get().exclude(mapOf("group" to "com.intellij", "module" to "annotations"))
    implementation.get().exclude(mapOf("group" to "org.intellij", "module" to "annotations"))
}

dependencies {
    coreLibraryDesugaring(libs.tools.desugar)

    // Local
    implementation(project(":week-view-core"))
    implementation(files("../../proton-libs/gopenpgp/gopenpgp.aar"))

    // Hilt Android.
    implementation(libs.dagger.hilt.android)
    kapt(libs.dagger.hilt.android.compiler)
    kaptAndroidTest(libs.dagger.hilt.android.compiler)

    // Assisted Inject.
    compileOnly(libs.assistedInject)
    kapt(libs.assistedInject)

    // Retrofit
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization.converter)

    // Shared preferences
    implementation(libs.androidx.preference)

    implementation(libs.biweekly)
    implementation(libs.bcrypt)
    implementation(libs.sentry)
    implementation(libs.ezVcard)
    implementation(libs.timber)
    implementation(libs.material)
    implementation(libs.tink) // it"s included in security-crypto
    implementation(libs.logging.interceptor)
    implementation(libs.sqlCipher)
    implementation(libs.guava)

    // Proton Core libraries.
    implementation(libs.core.account)
    implementation(libs.core.accountManager)
    implementation(libs.core.accountRecovery)
    implementation(libs.core.auth)
    implementation(libs.core.auth.fido)
    implementation(libs.core.contact)
    implementation(libs.core.country)
    implementation(libs.core.crypto)
    implementation(libs.core.cryptoValidator)
    implementation(libs.core.data)
    implementation(libs.core.dataRoom)
    implementation(libs.core.domain)
    implementation(libs.core.eventManager)
    implementation(libs.core.featureFlag)
    implementation(libs.core.humanVerification)
    implementation(libs.core.key)
    implementation(libs.core.keyTransparency)
    implementation(libs.core.mailMessage)
    implementation(libs.core.mailSettings)
    implementation(libs.core.notification)
    implementation(libs.core.network)
    implementation(libs.core.observability)
    implementation(libs.core.payment)
    implementation(libs.core.paymentIap)
    implementation(libs.core.plan)
    implementation(libs.core.proguard.rules)
    implementation(libs.core.presentation)
    implementation(libs.core.push)
    implementation(libs.core.telemetry)
    implementation(libs.core.user)
    implementation(libs.core.userSettings)
    implementation(libs.core.utilAndroidDagger)
    implementation(libs.core.config.data)
    releaseImplementation(libs.core.config.dagger.staticDefaults)
    debugImplementation(libs.core.config.dagger.contentProvider)
    implementation(libs.core.utilAndroidSentry)
    implementation(libs.core.utilKotlin)
    implementation(libs.core.challenge)
    implementation(libs.core.challengePresentation)
    implementation(libs.core.userRecovery)

    implementation(libs.androidx.core)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.layout.swiperefresh)
    implementation(libs.androidx.layout.constraint)
    implementation(libs.androidx.layout.coordinator)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.work.runtime)

    // Google play review
    implementation(libs.google.play.review)
    implementation(libs.google.play.review.ktx)

    // Alpha version needed for custom language selection.
    // This should be replaced as soon as a stable version is available
    implementation(libs.androidx.appcompat.alpha)

    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.hilt.compiler)
    kapt(libs.androidx.hilt.compiler)

    implementation(libs.androidx.navigation.ui)
    implementation(libs.androidx.navigation.fragment)

    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtime)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.koin.android)
    implementation(libs.koin.android.viewmodel)
    testImplementation(libs.koin.test)

    implementation(libs.playServices.auth)

    debugImplementation(libs.leakCanary)

    testImplementation(libs.jupiter.api)
    testRuntimeOnly(libs.jupiter.engine)

    testImplementation(libs.test.mockk)
    testImplementation(libs.test.assertk.jvm)
    testImplementation(project(":shared-test-code"))

    androidTestImplementation(libs.test.mockk.android)
    androidTestImplementation(libs.test.androidx.core)
    androidTestImplementation(libs.test.androidx.testrunner)
    androidTestImplementation(libs.test.androidx.rules)
    androidTestImplementation(libs.test.androidx.arch)
    androidTestImplementation(libs.test.androidx.compose.ui.test.junit)
    androidTestImplementation(libs.test.fusion)
    androidTestImplementation(libs.test.androidx.ext.junit)
    androidTestImplementation(libs.core.accountRecovery.test)
    androidTestImplementation(libs.core.auth.test)
    androidTestImplementation(libs.core.plan.test)
    androidTestImplementation(libs.core.userSettings.test)
    androidTestImplementation(project(":shared-test-code"))
    androidTestImplementation(libs.dagger.hilt.android.testing)
    androidTestImplementation(libs.test.espresso.core)
    androidTestImplementation(libs.core.userRecovery.test)

    androidTestUtil(libs.test.androidx.orchestrator)
    androidTestUtil(libs.test.androidx.services)
}

tasks.register("createBuildEnv") {
    fun String.toEnvVar() = replace("-", "_").toUpperCase()

    File(projectDir, "build.env").apply {
        writeText("")
        arrayOf("dev-debug", "dev-debug-androidTest", "prod-debug").forEach {
            project.buildOutputs.getByName(it).let { variant ->
                appendText("APK_PATH_${it.toEnvVar()}=\"${variant.outputFile.path}\"\n")
                appendText("APK_NAME_${it.toEnvVar()}=\"${variant.outputFile.name}\"\n")
            }
        }
        appendText("APK_VERSION=\"${Config.versionName}\"")
    }
}

tasks.withType(Test::class) {
    testLogging {
        events.addAll(listOf(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED))
    }
}

tasks.withType<Test> {
    configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

fun String?.toBuildConfigValue() = if (this != null) "\"$this\"" else "null"

val isGitlabCI: Boolean get() = !System.getenv("CI_SERVER_NAME").isNullOrEmpty()

object Config {
    const val applicationId = "me.proton.android.calendar"
    const val compileSdk = 34
    const val minSdk = 23
    const val ndkVersion = "21.3.6528147"
    const val buildToolsVersion = "34.0.0"
    const val targetSdk = 33
    const val versionCode = 291
    const val testInstrumentationRunner = "me.proton.android.calendar.uitest.extension.HiltTestRunner"
    const val versionName = "2.21.5"
    const val archivesBaseName = "ProtonCalendar-$versionName($versionCode)"
    val resourceConfigurations
        get() = listOf(
            "en", // English
            "ca", // Catalan
            "cs", // Czech
            "da", // Danish
            "de", // German
            "es-rES", // Spanish (Spain)
            "b+es+419", // Spanish (Latin America)
            "es-rMX", // Spanish (Mexico)
            "fr", // French
            "fi", // Finnish
            "hu", // Hungarian
            "it", // Italian
            "in", // Indonesian
            "nl", // Nederlands
            "pl", // Polish
            "pt-rBR", // Portuguese (Brazil)
            "pt-rPT", // Portuguese (Portugal)
            "ro", // Romanian
            "sv-rSE", // Swedish
            "tr", // Turkish
            "be", // Belarusian
            "ru", // Russian
            "uk", // Ukrainian
            "ka", // Georgian
            "zh-rTW", // Chinese Traditional (Taiwan)
        )
}

fun getProxyToken(): String {
    val proxyTokenUrl = System.getenv("ATLAS_PROXY_URL") ?: return ""
    return getTokenFromCurl(proxyTokenUrl)
}

fun VariantDimension.setAssetLinksResValue(host: String) {
    resValue(
        type = "string", name = "asset_statements",
        value = """
            [{
              "relation": ["delegate_permission/common.handle_all_urls", "delegate_permission/common.get_login_creds"],
              "target": { "namespace": "web", "site": "https://$host" }
            }]
        """.trimIndent()
    )
}
