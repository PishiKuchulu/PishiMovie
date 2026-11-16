package com.pishimovie.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.awt.BorderLayout
import java.awt.Canvas
import javax.swing.JPanel
import kotlinx.coroutines.delay

/**
 * Desktop video player: libmpv driven through JNA, rendered into a
 * heavyweight AWT Canvas inside a SwingPanel (the `wid` option).
 *
 * The native library is loaded lazily and by name ("mpv", then
 * "libmpv-2" as shipped by Windows mpv builds). If it is absent the
 * player screen shows a clear error instead of crashing; bundling
 * the native binaries per platform is a Phase 3 packaging task.
 */
@Suppress("FunctionName")
private interface LibMpv : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(ctx: Pointer): Int
    fun mpv_terminate_destroy(ctx: Pointer)
    fun mpv_set_option_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_command(ctx: Pointer, args: Pointer): Int
    fun mpv_set_property_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_get_property_string(ctx: Pointer, name: String): Pointer?
    fun mpv_free(data: Pointer)

    companion object {
        val instance: LibMpv? by lazy {
            runCatching { Native.load("mpv", LibMpv::class.java) as LibMpv }
                .recoverCatching { Native.load("libmpv-2", LibMpv::class.java) as LibMpv }
                .getOrNull()
        }
    }
}

/** Builds the NULL-terminated char** that mpv_command expects. */
private fun cArgs(vararg args: String): Pointer {
    val ptrSize = Native.POINTER_SIZE
    val array = Memory((args.size + 1) * ptrSize.toLong())
    val strings = args.map { arg ->
        Memory(arg.toByteArray(Charsets.UTF_8).size + 1L).apply {
            setString(0, arg, "UTF-8")
        }
    }
    strings.forEachIndexed { index, mem -> array.setPointer(index * ptrSize.toLong(), mem) }
    array.setPointer(args.size * ptrSize.toLong(), Pointer.NULL)
    // `strings` and `array` stay referenced by this frame until the
    // synchronous native call returns, so the buffers cannot be
    // collected mid-call.
    return array
}

private class MpvEngine {
    private var ctx: Pointer? = null

    fun init(wid: Long): Boolean {
        val lib = LibMpv.instance ?: return false
        val handle = lib.mpv_create() ?: return false
        lib.mpv_set_option_string(handle, "wid", wid.toString())
        lib.mpv_set_option_string(handle, "keep-open", "yes")
        lib.mpv_set_option_string(handle, "hwdec", "auto-safe")
        lib.mpv_set_option_string(handle, "title", "PishiMovie")
        if (lib.mpv_initialize(handle) < 0) {
            lib.mpv_terminate_destroy(handle)
            return false
        }
        ctx = handle
        return true
    }

    private fun command(vararg args: String) {
        val lib = LibMpv.instance ?: return
        val handle = ctx ?: return
        lib.mpv_command(handle, cArgs(*args))
    }

    fun loadFile(url: String) = command("loadfile", url)

    fun togglePause() = command("cycle", "pause")

    fun seekAbsolute(seconds: Double) = command("seek", seconds.toString(), "absolute")

    fun setVolume(percent: Int) {
        val lib = LibMpv.instance ?: return
        val handle = ctx ?: return
        lib.mpv_set_property_string(handle, "volume", percent.toString())
    }

    private fun property(name: String): String? {
        val lib = LibMpv.instance ?: return null
        val handle = ctx ?: return null
        val ptr = lib.mpv_get_property_string(handle, name) ?: return null
        val value = ptr.getString(0, "UTF-8")
        lib.mpv_free(ptr)
        return value
    }

    fun positionSec(): Double = property("time-pos")?.toDoubleOrNull() ?: 0.0

    fun durationSec(): Double = property("duration")?.toDoubleOrNull() ?: 0.0

    fun destroy() {
        val lib = LibMpv.instance ?: return
        ctx?.let { lib.mpv_terminate_destroy(it) }
        ctx = null
    }
}

private class MpvCanvas : Canvas() {
    var onReady: ((Long) -> Unit)? = null
    private var fired = false

    override fun addNotify() {
        super.addNotify()
        if (!fired) {
            fired = true
            val pointer = Native.getComponentPointer(this)
            onReady?.invoke(Pointer.nativeValue(pointer))
        }
    }
}

private fun formatTime(totalSeconds: Double): String {
    val s = totalSeconds.toInt().coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

@Composable
fun PlayerScreen(url: String, title: String, onClose: () -> Unit) {
    val engine = remember { MpvEngine() }
    var ready by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf(0.0) }
    var duration by remember { mutableStateOf(0.0) }
    var volume by remember { mutableStateOf(100f) }
    var seeking by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { engine.destroy() }
    }

    LaunchedEffect(ready) {
        if (ready) {
            while (true) {
                if (!seeking) {
                    position = engine.positionSec()
                    duration = engine.durationSec()
                }
                delay(500)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (LibMpv.instance == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "کتابخانهٔ پخش (mpv) روی این سیستم پیدا نشد.",
                        color = Color.White,
                    )
                }
            } else {
                SwingPanel(
                    factory = {
                        val canvas = MpvCanvas()
                        canvas.background = java.awt.Color.BLACK
                        canvas.onReady = { wid ->
                            if (engine.init(wid)) {
                                engine.loadFile(url)
                                ready = true
                            } else {
                                failed = true
                            }
                        }
                        JPanel(BorderLayout()).apply {
                            background = java.awt.Color.BLACK
                            add(canvas, BorderLayout.CENTER)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                if (failed) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("راه‌اندازی پخش‌کننده ناموفق بود.", color = Color.White)
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(formatTime(position), color = Color.White, style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = if (duration > 0) (position / duration).toFloat().coerceIn(0f, 1f) else 0f,
                    onValueChange = { fraction ->
                        seeking = true
                        position = fraction * duration
                    },
                    onValueChangeFinished = {
                        engine.seekAbsolute(position)
                        seeking = false
                    },
                    enabled = ready && duration > 0,
                    modifier = Modifier.weight(1f),
                )
                Text(formatTime(duration), color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        engine.togglePause()
                        paused = !paused
                    },
                    enabled = ready,
                ) { Text(if (paused) "پخش" else "مکث") }
                Text(
                    title,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text("صدا", color = Color.White, style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = volume,
                    onValueChange = {
                        volume = it
                        engine.setVolume(it.toInt())
                    },
                    valueRange = 0f..100f,
                    enabled = ready,
                    modifier = Modifier.width(140.dp),
                )
                TextButton(onClick = onClose) { Text("بستن", color = Color.White) }
            }
        }
    }
}
