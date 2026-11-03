import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing key. Read from keystore.properties (on your computer, never committed) or from
// SIGNING_* environment variables (GitHub Actions secrets). See "Signing" in the README.
// Without a key, release builds fall back to the debug key so the project still builds anywhere.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingValue(property: String, env: String): String? =
    keystoreProps.getProperty(property)?.takeIf { it.isNotBlank() }
        ?: System.getenv(env)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("storeFile", "SIGNING_STORE_FILE")?.let { file(it) }
val releaseStorePassword = signingValue("storePassword", "SIGNING_STORE_PASSWORD")
val hasReleaseKey = releaseStoreFile?.exists() == true && releaseStorePassword != null

android {
    namespace = "com.channelpulse.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.channelpulse.tv"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = signingValue("keyAlias", "SIGNING_KEY_ALIAS") ?: "channelpulse"
                // PKCS12 keystores use the store password for the key too.
                keyPassword = signingValue("keyPassword", "SIGNING_KEY_PASSWORD") ?: releaseStorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                logger.warn("No release key found: signing the release APK with the debug key. See \"Signing\" in the README.")
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
    }
    lint {
        // Personal sideloaded app: don't fail release builds on lint warnings.
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")

    // Compose for TV (focus-aware cards, buttons, surfaces)
    implementation("androidx.tv:tv-material:1.0.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.google.zxing:core:3.5.3")
}
