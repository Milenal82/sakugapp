plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
// No secrets in Gradle properties, source, or command-line arguments.
val unsignedReleaseCheck = providers.gradleProperty("releaseUnsignedCheck").orNull == "true"
val releaseSigningNames = listOf("ANDROID_RELEASE_KEYSTORE", "ANDROID_RELEASE_STORE_PASSWORD", "ANDROID_RELEASE_KEY_ALIAS", "ANDROID_RELEASE_KEY_PASSWORD")
val releaseSigningValues = releaseSigningNames.associateWith { providers.environmentVariable(it).orNull.orEmpty() }
val releaseSigningComplete = releaseSigningValues.values.all { it.isNotBlank() }
val checkReleaseSigning by tasks.registering {
    doLast {
        if (!unsignedReleaseCheck) {
            val missing = releaseSigningNames.filter { releaseSigningValues.getValue(it).isBlank() }
            check(missing.isEmpty()) { "Release signing is required. Missing environment variables: ${missing.joinToString()}. See docs/release-signing.md. No Debug signing fallback is allowed." }
            check(file(releaseSigningValues.getValue("ANDROID_RELEASE_KEYSTORE")).isFile) { "Configured Release keystore file is unavailable." }
        } else logger.lifecycle("UNSIGNED PREFLIGHT ONLY: this APK must not be installed or published as a signed release.")
    }
}
android {
    namespace = "com.sakugapp"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.sakugapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    signingConfigs {
        if (releaseSigningComplete && !unsignedReleaseCheck) create("release") {
            storeFile = file(releaseSigningValues.getValue("ANDROID_RELEASE_KEYSTORE"))
            storePassword = releaseSigningValues.getValue("ANDROID_RELEASE_STORE_PASSWORD")
            keyAlias = releaseSigningValues.getValue("ANDROID_RELEASE_KEY_ALIAS")
            keyPassword = releaseSigningValues.getValue("ANDROID_RELEASE_KEY_PASSWORD")
        }
    }
    buildTypes {
        getByName("release") {
            isDebuggable = false
            // Preserve the tested implementation; introduce R8 separately after device validation.
            isMinifyEnabled = false
            signingConfig = if(releaseSigningComplete && !unsignedReleaseCheck) signingConfigs.getByName("release") else null
        }
    }
}
tasks.configureEach {
    if (name in setOf("validateSigningRelease", "packageRelease", "assembleRelease")) dependsOn(checkReleaseSigning)
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.12.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-ui:1.6.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
