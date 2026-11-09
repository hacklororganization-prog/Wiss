// PyReels Studio — Gradle settings.
// The Android `app` module is included only when an Android SDK is present
// (ANDROID_HOME / ANDROID_SDK_ROOT / local.properties). Without it, the pure
// Kotlin `core` module still builds and tests, which keeps the domain logic
// testable on any JVM machine.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal() // also proxies Maven Central; useful when Central rate-limits
    }
}

rootProject.name = "PyReelsStudio"

include(":core")

val localProps = file("local.properties")
val hasSdk = System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null ||
    (localProps.exists() && localProps.readText().contains("sdk.dir"))
if (hasSdk && System.getenv("PYREELS_CORE_ONLY") == null) {
    include(":app")
}
