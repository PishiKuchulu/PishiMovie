package com.pishimovie.util

actual object NetworkUtils {

    /**
     * Desktop has no system connectivity service to consult; consistent
     * with the Android implementation's philosophy ("the real request is
     * the judge"), the desktop build assumes connectivity and lets the
     * request itself fail if the network is actually down.
     */
    actual fun isOnline(): Boolean = true

    /** Desktop connections are treated as unmetered. */
    actual fun isUnmetered(): Boolean = true
}
