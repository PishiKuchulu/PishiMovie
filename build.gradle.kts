// Root plugin declarations (apply false): the Kotlin Gradle plugin must
// be loaded once from the parent project when several subprojects use
// different Kotlin plugin flavors (android / multiplatform). Subprojects
// request the plugins through the version catalog without versions
// being re-declared per project.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.jetbrains.compose) apply false
}
