pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // nanojson, an extractor dependency, is published only on JitPack.
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "tubepipe"

include(":app", ":core:ui", ":core:service", ":core:database", ":core:player")

// The extractor's own build asks for a JDK 25 toolchain, and an included build cannot be
// overridden from here. So the vendored sources (third_party/PipePipeExtractor, upstream plus the
// patches in third_party/patches, see third_party/README.md) are compiled by our build files on JDK 21.
include(":extractor", ":timeago-parser")
project(":extractor").projectDir = file("extractor-build/extractor")
project(":timeago-parser").projectDir = file("extractor-build/timeago-parser")
