plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "org.tubepipe.core.player"
    compileSdk = 37
    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    testOptions {
        // Media3 value classes (Format, TrackGroup) call android.text.TextUtils, which the JVM stub jar does not run.
        unitTests.isReturnDefaultValues = true
    }
    buildFeatures {
        // BuildConfig.DEBUG gates WebView debugging and console logging (docs/decisions/player.md).
        buildConfig = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    // The playback glue reads StreamInfo directly (docs/decisions/architecture.md, layering exception).
    implementation(project(":extractor"))
    implementation(project(":core:service"))
    api(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.session)
    implementation(libs.androidx.annotation)
    implementation(libs.jsr305)
    implementation(libs.nanojson)
    implementation(libs.coroutines.android)
    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.coroutines.test)
}
