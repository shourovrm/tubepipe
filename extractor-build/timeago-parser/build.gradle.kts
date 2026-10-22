// Compiles extractor/timeago-parser (the PipePipeExtractor submodule) on JDK 21; see settings.gradle.kts.
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
        java.setSrcDirs(listOf(rootProject.file("extractor/timeago-parser/src/main/java")))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

dependencies {
    implementation(libs.nanojson)
    implementation(libs.spotbugs.annotations)
}
