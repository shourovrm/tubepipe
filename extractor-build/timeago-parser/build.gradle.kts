// Compiles the vendored timeago-parser (third_party/PipePipeExtractor) on JDK 21; see settings.gradle.kts.
plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

sourceSets {
    main {
        java.setSrcDirs(listOf(rootProject.file("third_party/PipePipeExtractor/timeago-parser/src/main/java")))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

dependencies {
    implementation(libs.nanojson)
    implementation(libs.spotbugs.annotations)
}
