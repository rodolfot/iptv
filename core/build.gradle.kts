// Pure-JVM module. No Android, no Hilt, no Compose.
// Houses logic that doesn't need a device: parsers, formatters, model helpers.
// Kept small on purpose — it's the leaf of the dependency graph so its tests
// run fast and it can be imported from any other module without dragging in
// Android types.
plugins {
    // Version comes from the root project's classpath (declared in the root
    // build.gradle.kts) — Gradle complains if we re-declare a version here.
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    // Pin Kotlin's JVM target to 17 to match :app and the Java target above.
    // Without this Kotlin defaults to the JDK in use (21 in CI / dev boxes)
    // and Gradle fails the build complaining about inconsistent targets.
    jvmToolchain(17)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
