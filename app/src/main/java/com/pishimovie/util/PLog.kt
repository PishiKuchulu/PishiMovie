package com.pishimovie.util

import android.util.Log
import com.pishimovie.BuildConfig

object PLog {

    fun d(tag: String, msg: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, msg)
        }
    }

    fun d(tag: String, msg: String, throwable: Throwable) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, msg, throwable)
        }
    }

    fun i(tag: String, msg: String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, msg)
        }
    }

    fun w(tag: String, msg: String) {
        if (BuildConfig.DEBUG) {
            Log.w(tag, msg)
        }
    }

    fun w(tag: String, msg: String, throwable: Throwable) {
        if (BuildConfig.DEBUG) {
            Log.w(tag, msg, throwable)
        }
    }

    fun e(tag: String, msg: String) {
        if (BuildConfig.DEBUG) {
            Log.e(tag, msg)
        }
    }

    fun e(tag: String, msg: String, throwable: Throwable) {
        if (BuildConfig.DEBUG) {
            Log.e(tag, msg, throwable)
        }
    }
}