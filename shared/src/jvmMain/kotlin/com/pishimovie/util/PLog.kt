package com.pishimovie.util

/**
 * App logger. Android writes to Logcat (debug builds only, as before);
 * Desktop writes to stdout. Call sites are identical on both platforms.
 */
expect object PLog {
    fun d(tag: String, msg: String)
    fun d(tag: String, msg: String, throwable: Throwable)
    fun i(tag: String, msg: String)
    fun w(tag: String, msg: String)
    fun w(tag: String, msg: String, throwable: Throwable)
    fun e(tag: String, msg: String)
    fun e(tag: String, msg: String, throwable: Throwable)
}
