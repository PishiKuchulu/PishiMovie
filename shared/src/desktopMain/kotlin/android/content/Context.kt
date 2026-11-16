package android.content

import java.io.File

/**
 * Desktop stand-in for the Android Context, providing exactly the
 * members the shared data layer uses (verified against every call
 * site: filesDir, cacheDir, applicationContext — nothing else).
 *
 * Why this exists: the data layer was written against the platform
 * Context and is passed one at ~160 call sites. Kotlin's expect/actual
 * typealias cannot actualize an expect class in this toolchain
 * (Kotlin 2.0.21/K2 reports a modality mismatch for every shape), so
 * the desktop target instead supplies this minimal class under the
 * same fully-qualified name. It lives ONLY in the desktop source set:
 * Android builds compile and run against the real SDK class, and no
 * shared or app source file changes either way.
 *
 * The desktop entry point creates one instance pointing at the app's
 * data/cache directories under the user's app-data folder.
 */
open class Context(
    val filesDir: File,
    val cacheDir: File,
) {
    val applicationContext: Context
        get() = this
}
