plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "org.tubepipe.core.service"
    compileSdk = 37
    defaultConfig {
        minSdk = 23
    }
    compileOptions {
        // The extractor uses java.time, which API 23 to 25 lack.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":extractor"))
}
