package com.pishimovie.util

import android.util.Log
import com.pishimovie.shared.BuildConfig

actual object PLog {

    actual fun d(tag: String, msg: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, msg)
        }
    }

    actual fun d(tag: String, msg: String, throwable: Throwable) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, msg, throwable)
        }
    }

    actual fun i(tag: String, msg: String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, msg)
        }
    }

    actual fun w(tag: String, msg: String) {
        if (BuildConfig.DEBUG) {
            Log.w(tag, msg)
        }
    }

    actual fun w(tag: String, msg: String, throwable: Throwable) {
        if (BuildConfig.DEBUG) {
            Log.w(tag, msg, throwable)
        }
    }

    actual fun e(tag: String, msg: String) {
        if (BuildConfig.DEBUG) {
            Log.e(tag, msg)
        }
    }

    actual fun e(tag: String, msg: String, throwable: Throwable) {
        if (BuildConfig.DEBUG) {
            Log.e(tag, msg, throwable)
        }
    }
}
