import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    // kotlin.time.Instant (backend clock, trip times) is still experimental in Kotlin 2.2.
    compilerOptions { optIn.add("kotlin.time.ExperimentalTime") }

    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("composeApp")
        browser {
            commonWebpackConfig { outputFileName = "composeApp.js" }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
    }
}

// Generated resource accessors (Res.drawable.*, Res.font.*) live in this package.
compose.resources {
    packageOfResClass = "ch.ausweg.app.resources"
}

// Backend the Android app talks to: `-Pausweg.apiUrl=http://192.168.1.20:8080` (or in gradle.properties /
// local gradle.properties) for a phone on the same Wi-Fi. Default: the host machine as seen from the emulator.
val apiUrl: String = providers.gradleProperty("ausweg.apiUrl").getOrElse("http://10.0.2.2:8080")

android {
    namespace = "ch.ausweg.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        applicationId = "ch.ausweg.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "API_URL", "\"$apiUrl\"")
    }
    buildFeatures { buildConfig = true }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
    buildTypes {
        getByName("release") { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
