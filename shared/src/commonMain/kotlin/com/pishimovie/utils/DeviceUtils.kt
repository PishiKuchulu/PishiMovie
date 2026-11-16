package com.pishimovie.utils

/**
 * Device-form-factor checks shared by Android and Desktop. The common
 * API is context-free (form factor is a device-global property; the
 * Android actual consults the application Context registered at
 * process start). Android call sites in the app module that pass a
 * Context keep working through the actual's overloads.
 */
expect object DeviceUtils {
    /**
     * Check if the device is a TV
     */
    fun isTv(): Boolean

    /**
     * Check if the device is a tablet
     */
    fun isTablet(): Boolean
}
