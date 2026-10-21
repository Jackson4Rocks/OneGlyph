plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.jackson4rocks.oneglyph"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.jackson4rocks.oneglyph"
        minSdk = 35
        targetSdk = 36
        versionCode = 200
        versionName = "2.0"
    }

    buildTypes {
        release {
            // Smaller APK / DEX for a stock-only app.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        // Unused (no BuildConfig reference in sources): skip generation.
        buildConfig = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    // Liquid-glass bar: real backdrop blur, lens refraction, drag physics.
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.4-rc01")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
