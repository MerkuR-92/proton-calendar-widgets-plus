plugins {
    id("com.android.library")
}

android {
    namespace = "com.alamkanak.weekview"
    defaultConfig {
        compileSdk = 32
        minSdk = 23
        targetSdk = 32
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.kotlin.stdlib)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core)
    implementation(libs.androidx.customview)
    implementation(libs.androidx.emoji)
    implementation(libs.androidx.startup)

    coreLibraryDesugaring(libs.tools.desugar)

    testImplementation(libs.test.androidx.ext.junit)
    testImplementation(libs.test.androidx.testrunner)
    testImplementation(libs.test.junit)
    testImplementation(libs.test.google.truth)
    testImplementation(libs.test.mockito.core)
    testImplementation(libs.test.mockito.inline)
    testImplementation(libs.test.mockito.kotlin)
}