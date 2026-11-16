package com.pishimovie.utils

actual object DeviceUtils {
    /** A desktop window is not a TV client. */
    actual fun isTv(): Boolean = false

    /** A desktop window is not a tablet client. */
    actual fun isTablet(): Boolean = false
}
