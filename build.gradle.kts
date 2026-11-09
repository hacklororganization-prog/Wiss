// Root build. Plugins are put on the root build classpath so that the Kotlin
// Gradle plugin and the Android Gradle plugin share one classloader. The
// Android-only plugins are added only when an Android SDK is available.
buildscript {
    val hasSdk = System.getenv("ANDROID_HOME") != null ||
        System.getenv("ANDROID_SDK_ROOT") != null ||
        (file("local.properties").exists() && file("local.properties").readText().contains("sdk.dir"))
    val android = hasSdk && System.getenv("PYREELS_CORE_ONLY") == null
    repositories {
        if (android) google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
        classpath("org.jetbrains.kotlin:kotlin-serialization:2.0.21")
        if (android) {
            classpath("com.android.tools.build:gradle:8.7.3")
            classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.0.21")
            classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.0.21-1.0.28")
            classpath("com.google.dagger:hilt-android-gradle-plugin:2.52")
            classpath("com.chaquo.python:gradle:16.0.0")
        }
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal() // also proxies Maven Central; useful when Central rate-limits
    }
}
