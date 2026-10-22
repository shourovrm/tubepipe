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

// Live tests hit YouTube, so they run only on request: ./gradlew :core:service:test -Ptubepipe.live=true
tasks.withType<Test>().configureEach {
    systemProperty("tubepipe.live", providers.gradleProperty("tubepipe.live").getOrElse("false"))
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":extractor"))
    api(libs.okhttp)
    implementation(libs.androidx.annotation)
    implementation(libs.coroutines.core)
    testImplementation(libs.junit4)
    testImplementation(libs.coroutines.test)
}
