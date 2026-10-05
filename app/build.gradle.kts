plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.jackson4rocks.oneglyph"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jackson4rocks.oneglyph"
        minSdk = 35
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.08.00")
    implementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}


val bundleLeonProfile = tasks.register("bundleLeonProfile") {
    val output =
        file(
            "src/main/res/drawable/leon_profile.png"
        )

    outputs.file(output)

    onlyIf {
        !output.exists()
    }

    doLast {
        output.parentFile.mkdirs()

        val connection =
            java.net.URI(
                "https://github.com/Jackson4Rocks.png?size=256"
            )
                .toURL()
                .openConnection() as java.net.HttpURLConnection

        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty(
            "User-Agent",
            "OneGlyph-Build"
        )

        try {
            check(
                connection.responseCode in 200..399
            ) {
                "Could not bundle Leon's GitHub profile picture: HTTP ${connection.responseCode}"
            }

            connection.inputStream.use { input ->
                output.outputStream().use { outputStream ->
                    input.copyTo(outputStream)
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(bundleLeonProfile)
}
