import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
}

// PishiMovie Desktop (Windows + Linux) — Compose Multiplatform.
// Phase 0: skeleton window proving the toolchain.
// Phase 2: real entry point + home screen over the shared data layer.

// The single source of truth for the backend connection values is the
// Android app module's BuildConfig fields in app/build.gradle.kts
// (unchanged). This task extracts them at build time into a generated
// source file under build/ (never committed), so the desktop app
// injects exactly the same values into the shared BackendConfig and
// no second copy of the values exists in any source file.
val backendConfigGenDir = layout.buildDirectory.dir("generated/backendconfig")
val generateDesktopBackendConfig = tasks.register("generateDesktopBackendConfig") {
    val appBuildFile = rootProject.file("app/build.gradle.kts")
    inputs.file(appBuildFile)
    outputs.dir(backendConfigGenDir)
    doLast {
        val text = appBuildFile.readText()
        fun windowAfter(name: String, size: Int = 4000): String {
            val idx = text.indexOf("\"$name\"")
            require(idx >= 0) { "Desktop backend config: $name not found in app/build.gradle.kts" }
            return text.substring(idx, minOf(text.length, idx + size))
        }
        fun stringField(name: String): String =
            Regex("""\\"(.+?)\\"""").find(windowAfter(name, 600))?.groupValues?.get(1)
                ?: error("Desktop backend config: value of $name not found")
        fun arrayField(name: String): List<String> {
            // The Android module declares String[] fields with a Java
            // array initializer: new String[]{"...", "..."}
            val body = Regex("""new String\[\]\{(.*?)\}""", RegexOption.DOT_MATCHES_ALL)
                .find(windowAfter(name))?.groupValues?.get(1)
                ?: error("Desktop backend config: String[] initializer for $name not found")
            return Regex("""\\"(.+?)\\"""").findAll(body).map { it.groupValues[1] }.toList()
        }
        fun esc(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
        val apiBaseUrl = stringField("API_BASE_URL")
        val apiKeyValue = stringField("API_KEY")
        val helpers = arrayField("HELPER_SERVERS")
        val outFile = backendConfigGenDir.get().file("DesktopBackendConfig.kt").asFile
        outFile.parentFile.mkdirs()
        outFile.writeText(
            """
            package com.pishimovie.desktop

            // GENERATED at build time from app/build.gradle.kts — do not edit.
            internal object DesktopBackendConfig {
                const val API_BASE_URL: String = "${esc(apiBaseUrl)}"
                const val API_KEY: String = "${esc(apiKeyValue)}"
                val HELPER_SERVERS: Array<String> = arrayOf(${helpers.joinToString(", ") { "\"${esc(it)}\"" }})
            }
            """.trimIndent() + "\n"
        )
    }
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        jvmMain {
            kotlin.srcDir(backendConfigGenDir)
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(project(":shared"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.7.3")
                implementation("io.coil-kt.coil3:coil-compose:3.0.4")
                implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
                // JNA binding for libmpv (desktop player, Phase 2d).
                implementation("net.java.dev.jna:jna:5.14.0")
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(generateDesktopBackendConfig)
}

compose.desktop {
    application {
        mainClass = "com.pishimovie.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "PishiMovie"
            packageVersion = "1.0.0"
            description = "PishiMovie — movies and series, on your desktop"
            vendor = "PishiKuchulu"

            windows {
                menuGroup = "PishiMovie"
                // Stable upgrade UUID: installers upgrade in place.
                // Never change this after the first public release.
                upgradeUuid = "1a93df17-252f-44d9-947a-90dfb0b9dc61"
            }
            linux {
                packageName = "pishimovie"
            }
        }
    }
}
