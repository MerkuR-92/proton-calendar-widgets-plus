@file:Suppress("UnstableApiUsage")

import io.gitlab.arturbosch.detekt.Detekt
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.archivesName

plugins {
    id("org.jetbrains.kotlin.plugin.serialization") version "1.7.20"
    id("org.sonarqube") version "3.3"
    id("com.android.application")
    id("kotlin-android-extensions")
    id("kotlin-android")
    id("kotlin-kapt")
    id("androidx.navigation.safeargs.kotlin")
    id("io.gitlab.arturbosch.detekt")
    id("dagger.hilt.android.plugin")
    id("jacoco")
}

sonarqube {
    properties {
        property("sonar.projectKey", "android_calendar_proton-calendar-android_AYGvp8U7f_vcScryKn5V")
        property("sonar.qualitygate.wait", true)
    }
}

detekt { config = files("$projectDir/config/detekt/detekt.yml") }
jacoco { toolVersion = "0.8.7" }
kapt { correctErrorTypes = true }

android {
    buildToolsVersion = "30.0.3"
    ndkVersion = "21.3.6528147"
    compileSdk = Config.compileSdk

    kotlinOptions { jvmTarget = "11" }
    dataBinding { enable = true }

    defaultConfig {
        applicationId = Config.applicationId
        minSdk = Config.minSdk
        targetSdk = Config.targetSdk
        versionCode = Config.versionCode
        versionName = Config.versionName
        archivesName.set(Config.archivesBaseName)
        testInstrumentationRunner = Config.testInstrumentationRunner
        resourceConfigurations.addAll(Config.resourceConfigurations)

        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(project.properties["keyStoreFilePath"] ?: "protonkey.jks")
            storePassword = System.getenv("KEY_STORE_PASSWORD") ?: "\"Store password\""
            keyAlias = System.getenv("KEY_STORE_KEY_ALIAS") ?: "proton"
            keyPassword = System.getenv("KEY_STORE_KEY_PASSWORD") ?: "\"Store key password\""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false // TODO turn off for initial beta release
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")

            val sentryDsn = System.getenv("SENTRY_DSN_NEW") ?: ""
            buildConfigField("String", "SENTRY_DSN_NEW", "\"${sentryDsn}\"")
        }
        debug {
            buildConfigField("String", "SENTRY_DSN_NEW", "\"null\"")
            enableUnitTestCoverage = true
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    sourceSets {
        getByName("androidTest").java.srcDirs("src/sharedTest/java")
        getByName("test").java.srcDirs("src/sharedTest/java")
    }

    flavorDimensions.add("default")
}

dependencies {
    coreLibraryDesugaring(libs.tools.desugar)

    // Local
    implementation(project(":week-view-core"))
    implementation(files("../../proton-libs/gopenpgp/gopenpgp.aar"))

    // Hilt Android.
    implementation(libs.dagger.hilt.android)
    kapt(libs.dagger.hilt.android.compiler)

    // Assisted Inject.
    compileOnly(libs.assistedInject)
    kapt(libs.assistedInject)

    detekt(libs.detekt.formatting)
    detekt(libs.detekt.cli)
    detektPlugins(libs.detekt.formatting)

    // Retrofit
    implementation(libs.retrofit)
    implementation(libs.retrofit.coroutines)
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

    // Proton Core libraries.
    implementation(libs.core.account)
    implementation(libs.core.accountManager)
    implementation(libs.core.accountManager.dagger)
    implementation(libs.core.auth)
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
    implementation(libs.core.mailMessage)
    implementation(libs.core.mailSettings)
    implementation(libs.core.network)
    implementation(libs.core.observability)
    implementation(libs.core.payment)
    implementation(libs.core.plan)
    implementation(libs.core.presentation)
    implementation(libs.core.user)
    implementation(libs.core.userSettings)
    implementation(libs.core.utilAndroidDagger)
    implementation(libs.core.utilKotlin)
    implementation(libs.core.challenge)
    implementation(libs.core.challengePresentation)

    implementation(libs.androidx.core)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.layout.swiperefresh)
    implementation(libs.androidx.layout.constraint)
    implementation(libs.androidx.layout.coordinator)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.security.crypto)
    //noinspection GradleDependency
    implementation(libs.androidx.work.runtime)

    // Alpha version needed for custom language selection.
    // This should be replaced as soon as a stable version is available
    implementation(libs.androidx.appcompat.alpha)

    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.hilt.compiler)

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

    androidTestImplementation(libs.test.mockk.android)
    androidTestImplementation(libs.test.androidx.core)
    androidTestImplementation(libs.test.androidx.testrunner)
    androidTestImplementation(libs.test.androidx.rules)
    androidTestImplementation(libs.test.androidx.arch)
    androidTestImplementation(libs.test.fusion)
}

tasks.register("getArchivesName"){
    doLast {
        println(Config.archivesBaseName)
    }
}

tasks.withType(Test::class) {
    testLogging {
        events.addAll(listOf(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED))
    }
}

tasks.named("detekt", Detekt::class).configure {
    jvmTarget = "1.8"
    reports {
        xml.required.set(false)
        html.required.set(true)
        txt.required.set(false)
        custom {
            reportId = "DetektQualityOutputReport"
            outputLocation.set(file("build/reports/detekt.json"))
        }
    }
}

tasks.withType<Test> {
    configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

object Config {
    const val applicationId = "me.proton.android.calendar"
    const val compileSdk = 33
    const val minSdk = 23
    const val targetSdk = 33
    const val versionCode = 179
    const val testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    const val versionName = "2.5.3"
    const val archivesBaseName = "ProtonCalendar-$versionName($versionCode)"
    val resourceConfigurations
        get() = listOf(
            "en",
            "ca",
            "es-rES",
            "es-rMX",
            "fr",
            "fr-rCA",
            "pl",
            "pt-rPT",
            "ro",
            "de",
            "it",
            "nl",
            "uk"
        )
}