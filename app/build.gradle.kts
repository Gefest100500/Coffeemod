import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val secrets = Properties().apply {
    val file = rootProject.file("secrets.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun secret(key: String, fallback: String = ""): String =
    (secrets.getProperty(key) ?: fallback).replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.coffemod"
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "com.coffemod"
        minSdk = 26
        targetSdk = 37
        versionCode = 5
        versionName = "0.8.1"
    }

    signingConfigs {
        create("release") {
            val keystorePath = secret("keystore.file", "keystore/coffemod-release.jks")
            if (keystorePath.isNotBlank()) {
                val keystoreFile = rootProject.file(keystorePath)
                if (keystoreFile.exists()) {
                    storeFile = keystoreFile
                    storePassword = secret("keystore.password")
                    keyAlias = secret("keystore.alias", "kashpendra")
                    keyPassword = secret("keystore.keyPassword", secret("keystore.password"))
                    storeType = "PKCS12"
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.11.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")

    implementation("androidx.camera:camera-core:1.7.0")
    implementation("androidx.camera:camera-camera2:1.7.0")
    implementation("androidx.camera:camera-lifecycle:1.7.0")
    implementation("androidx.camera:camera-view:1.7.0")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("com.google.zxing:core:3.5.4")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
