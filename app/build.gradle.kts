import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    alias(libs.plugins.compose.compiler)
}

android {
    compileSdk = 36

    namespace = "com.faster.suiting"

    defaultConfig {
        applicationId = "com.faster.suiting"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "1.9"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)

    // Compose BOM governs all androidx.compose.* versions
    implementation(composeBom)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Standard Compose Material3 — provides androidx.compose.material3.Icon
    implementation(libs.androidx.compose.material3)

    // Wear OS: Material3, Foundation (ScalingLazyColumn etc.), Navigation
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)

    // Material Icons — play/pause/skip/volume vector assets
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)

    // Compose tooling
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.ui.test.manifest)

    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
