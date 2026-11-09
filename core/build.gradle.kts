plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.21")
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    // Python-backed checks of the errors library run only when python3 exists.
    systemProperty("pyreels.python", System.getenv("PYREELS_PYTHON") ?: "python3")
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

// Regenerates the 7-episode weekly sample from the roadmap templates.
tasks.register<JavaExec>("generateWeekly") {
    group = "pyreels"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("studio.pyreels.core.tools.GenerateWeeklyKt")
    args(rootProject.file("core/src/main/resources/pyreels/samples/weekly-batch.pyreels.json").path)
}
