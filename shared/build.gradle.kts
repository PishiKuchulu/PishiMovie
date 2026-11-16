import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

// Shared PishiMovie code for Android + Desktop (Windows/Linux).
// Phase 1 layout:
//  - commonMain: platform-free expect declarations (NetworkUtils,
//    DeviceUtils) + SharedInfo.
//  - jvmMain (hierarchy-template group over jvm + androidTarget):
//    pure-JVM shared code (PLog expect) and the module's JVM deps.
//  - src/dataMain/kotlin: the whole data layer (models, repositories,
//    caches, storage, BackendConfig). It is NOT a source set of its
//    own: androidMain and desktopMain both add it as an extra srcDir,
//    so the same files compile once per target — Android against the
//    real android.content.Context, desktop against the minimal
//    android.content.Context stand-in in desktopMain. (An intermediate
//    source set cannot reference the platform Context: its metadata
//    compilation has neither the SDK nor the stand-in.)
kotlin {
    applyDefaultHierarchyTemplate {
        common {
            group("jvm") {
                withJvm()
                withAndroidTarget()
            }
        }
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation("com.squareup.okhttp3:okhttp:4.12.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            }
        }
        val androidMain by getting {
            kotlin.srcDir("src/dataMain/kotlin")
        }
        val desktopMain by getting {
            kotlin.srcDir("src/dataMain/kotlin")
            dependencies {
                // org.json ships with the Android runtime; the desktop
                // JVM needs the artifact explicitly.
                implementation("org.json:json:20240303")
            }
        }
    }
}

android {
    namespace = "com.pishimovie.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 24
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
