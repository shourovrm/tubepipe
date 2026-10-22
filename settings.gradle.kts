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

// The extractor submodule's own build asks for a JDK 25 toolchain, and an included build cannot be
// overridden from here. So its sources are compiled by our build files on JDK 21 instead
// (docs/decisions/architecture.md). The submodule itself stays unmodified.
include(":extractor", ":timeago-parser")
project(":extractor").projectDir = file("extractor-build/extractor")
project(":timeago-parser").projectDir = file("extractor-build/timeago-parser")
