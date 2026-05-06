import java.util.concurrent.TimeUnit

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

fun runGit(vararg args: String): String? = try {
    val proc = ProcessBuilder(listOf("git") + args)
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    proc.waitFor(5L, TimeUnit.SECONDS)
    proc.inputStream.bufferedReader().readText().trim().takeIf { it.isNotEmpty() }
} catch (_: Exception) {
    null
}

val gitCommitCount: Int = runGit("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1
val gitDescribe: String = runGit("describe", "--tags", "--always", "--dirty")?.removePrefix("v") ?: "1.0.0"

android {
    namespace = "com.iptv.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.iptv.app"
        minSdk = 23
        targetSdk = 35
        versionCode = (project.findProperty("VERSION_CODE") as String?)?.toIntOrNull() ?: gitCommitCount
        versionName = (project.findProperty("VERSION_NAME") as String?) ?: gitDescribe

        // Auto-update endpoint. Override with -PUPDATE_REPO_OWNER=... in CI/local.
        buildConfigField(
            "String", "UPDATE_REPO_OWNER",
            "\"${(project.findProperty("UPDATE_REPO_OWNER") as String?) ?: ""}\""
        )
        buildConfigField(
            "String", "UPDATE_REPO_NAME",
            "\"${(project.findProperty("UPDATE_REPO_NAME") as String?) ?: ""}\""
        )
    }

    signingConfigs {
        create("release") {
            val keystorePath = project.findProperty("RELEASE_KEYSTORE_FILE") as String?
            val keystorePassword = project.findProperty("RELEASE_KEYSTORE_PASSWORD") as String?
            val keyAliasProp = project.findProperty("RELEASE_KEY_ALIAS") as String?
            val keyPasswordProp = project.findProperty("RELEASE_KEY_PASSWORD") as String?
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                keyAlias = keyAliasProp
                keyPassword = keyPasswordProp
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Use the release keystore when its properties are provided; otherwise
            // fall back to the debug keystore so unsigned local builds still work.
            signingConfig = if (project.hasProperty("RELEASE_KEYSTORE_FILE")) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
            "/META-INF/LICENSE*",
            "/META-INF/NOTICE*"
        )
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Compose for TV
    implementation("androidx.tv:tv-foundation:1.0.0-alpha10")
    implementation("androidx.tv:tv-material:1.0.0")

    // Media3 / ExoPlayer
    val media3 = "1.4.0"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-exoplayer-dash:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.moshi:moshi:1.15.1")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    implementation("com.squareup.moshi:moshi-adapters:1.15.1")

    // Coil for images
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Room
    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    // Splash screen
    implementation("androidx.core:core-splashscreen:1.0.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Encrypted storage (Tink-backed)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // WorkManager for EPG refresh
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("net.sf.kxml:kxml2:2.3.0")
}
