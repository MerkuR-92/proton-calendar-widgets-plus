@file:Suppress("UnstableApiUsage")

import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.archivesName

plugins {
    id("org.jetbrains.kotlin.plugin.serialization") version "1.7.20"
    id("org.sonarqube") version "3.3"
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("androidx.navigation.safeargs.kotlin")
    id("dagger.hilt.android.plugin")
    id("jacoco")
}

sonarqube {
    properties {
        property("sonar.projectKey", "android_calendar_proton-calendar-android_AYGvp8U7f_vcScryKn5V")
        property("sonar.qualitygate.wait", true)
    }
}

jacoco { toolVersion = "0.8.7" }
kapt { correctErrorTypes = true }

android {
    buildToolsVersion = Config.buildToolsVersion
    ndkVersion = Config.ndkVersion
    compileSdk = Config.compileSdk
    namespace = Config.applicationId

    kotlinOptions { jvmTarget = "11" }

    buildFeatures {
        dataBinding = true
        viewBinding = true
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

        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }
    }

    flavorDimensions.add("env")
    productFlavors {
        create("dev") {
            dimension = "env"
            applicationIdSuffix = ".dev"
            buildConfigField("String", "API_HOST", "\"api.proton.black\"")
            buildConfigField("String", "HV3_HOST", "\"verify.proton.black\"")
            buildConfigField("String", "QUARK_HOST", "\"proton.black\"")
            buildConfigField("String", "PROXY_TOKEN", System.getenv("PROXY_TOKEN").toBuildConfigValue())
            buildConfigField("Boolean", "USE_DEFAULT_PINS", "false")
            resValue("string", "app_name", "Atlas Proton Calendar")
        }
        create("prod") {
            dimension = "env"
            buildConfigField("String", "API_HOST", "\"calendar-api.proton.me\"")
            buildConfigField("String", "HV3_HOST", "\"verify.proton.me\"")
            buildConfigField("String", "QUARK_HOST", "\"\"")
            buildConfigField("String", "PROXY_TOKEN", "\"\"")
            buildConfigField("Boolean", "USE_DEFAULT_PINS", "true")
            resValue("string", "app_name", "Proton Calendar")
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
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
    implementation(libs.sqlCipher)
    implementation(libs.guava)

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
    implementation(libs.core.keyTransparency)
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
    androidTestImplementation(libs.test.fusion)
    androidTestImplementation(libs.test.androidx.ext.junit)
    androidTestImplementation(libs.core.auth.test)
    androidTestImplementation(project(":shared-test-code"))
    androidTestImplementation(libs.dagger.hilt.android.testing)
    androidTestImplementation(libs.test.espresso.core)

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
    const val compileSdk = 33
    const val minSdk = 23
    const val ndkVersion = "21.3.6528147"
    const val buildToolsVersion = "30.0.3"
    const val targetSdk = 33
    const val versionCode = 207
    const val testInstrumentationRunner = "me.proton.android.calendar.uitest.extension.HiltTestRunner"
    const val versionName = "2.11.9"
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
