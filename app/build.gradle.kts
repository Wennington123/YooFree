import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Config de assinatura: local.properties (local) ou variaveis de ambiente (CI).
// O keystore NUNCA vai pro git.
val signingProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingValue(name: String): String? =
    System.getenv(name)?.takeIf { it.isNotBlank() } ?: signingProps.getProperty(name)

val releaseStoreFile = signingValue("YOOFREE_STORE_FILE")
val releaseStorePassword = signingValue("YOOFREE_STORE_PASSWORD")
val releaseKeyAlias = signingValue("YOOFREE_KEY_ALIAS")
val releaseKeyPassword = signingValue("YOOFREE_KEY_PASSWORD")
val hasReleaseKey = !releaseStoreFile.isNullOrBlank()

android {
    namespace = "br.yoofree.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "br.yoofree.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 8
        versionName = "0.8"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("release")
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
