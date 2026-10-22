// Compiles extractor/extractor (the PipePipeExtractor submodule) on JDK 21; see settings.gradle.kts.
// Dependencies mirror the submodule's extractor/build.gradle; copy any change there when bumping the tag.
plugins {
    `java-library`
    alias(libs.plugins.wire)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

sourceSets {
    main {
        java.setSrcDirs(listOf(rootProject.file("extractor/extractor/src/main/java")))
    }
}

wire {
    sourcePath {
        srcDir(rootProject.file("extractor/extractor/src/main/proto"))
    }
    java {
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

dependencies {
    implementation(project(":timeago-parser"))
    implementation(libs.nanojson)
    implementation(libs.jsoup)
    implementation(libs.java.websocket)
    implementation(libs.spotbugs.annotations)
    implementation(libs.autolink)
    implementation(libs.protobuf.java)
    implementation(libs.okhttp)
    implementation(libs.brotli.dec)
    implementation(libs.commons.lang3)
    implementation(libs.bouncycastle)
    compileOnly(libs.org.json)
}
