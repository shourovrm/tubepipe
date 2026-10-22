plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.wire) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}

// Format and lint every Kotlin module the same way; the extractor modules are Java and upstream's.
subprojects {
    if (path != ":extractor" && path != ":timeago-parser") {
        apply(plugin = "org.jlleitschuh.gradle.ktlint")
    }
}
