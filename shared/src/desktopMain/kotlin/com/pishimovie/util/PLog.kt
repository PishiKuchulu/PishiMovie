package com.pishimovie.util

actual object PLog {

    /** The desktop entry point may mute logging; defaults to on. */
    var debugEnabled: Boolean = true

    actual fun d(tag: String, msg: String) {
        if (debugEnabled) println("D/$tag: $msg")
    }

    actual fun d(tag: String, msg: String, throwable: Throwable) {
        if (debugEnabled) {
            println("D/$tag: $msg")
            throwable.printStackTrace()
        }
    }

    actual fun i(tag: String, msg: String) {
        if (debugEnabled) println("I/$tag: $msg")
    }

    actual fun w(tag: String, msg: String) {
        if (debugEnabled) println("W/$tag: $msg")
    }

    actual fun w(tag: String, msg: String, throwable: Throwable) {
        if (debugEnabled) {
            println("W/$tag: $msg")
            throwable.printStackTrace()
        }
    }

    actual fun e(tag: String, msg: String) {
        if (debugEnabled) System.err.println("E/$tag: $msg")
    }

    actual fun e(tag: String, msg: String, throwable: Throwable) {
        if (debugEnabled) {
            System.err.println("E/$tag: $msg")
            throwable.printStackTrace()
        }
    }
}
