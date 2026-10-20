import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Config de assinatura lida de local.properties (que NAO vai pro git).
val signingProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKey = signingProps.getProperty("YOOFREE_STORE_FILE") != null

android {
    namespace = "br.yoofree.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "br.yoofree.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 6
        versionName = "0.6"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(signingProps.getProperty("YOOFREE_STORE_FILE"))
                storePassword = signingProps.getProperty("YOOFREE_STORE_PASSWORD")
                keyAlias = signingProps.getProperty("YOOFREE_KEY_ALIAS")
                keyPassword = signingProps.getProperty("YOOFREE_KEY_PASSWORD")
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
