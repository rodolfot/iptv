plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.25" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.25" apply false
    id("com.google.devtools.ksp") version "1.9.25-1.0.20" apply false
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.7"
}

// Run `./gradlew detekt` to lint the whole module. Config lives in
// `config/detekt/detekt.yml`; rules are intentionally light to start with so
// the gate doesn't block PRs over stylistic preferences.
detekt {
    toolVersion = "1.23.7"
    source.setFrom(files("app/src/main/java"))
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    parallel = true
}
