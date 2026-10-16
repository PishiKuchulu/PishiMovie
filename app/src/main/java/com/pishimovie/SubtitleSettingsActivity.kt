package com.pishimovie

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.pishimovie.data.model.FontSettings
import com.pishimovie.screens.SubtitleSettingsScreen
import com.pishimovie.ui.theme.PishiMovieTheme
import com.pishimovie.ui.theme.rememberAppThemeSettings
import com.pishimovie.utils.DeviceUtils
import com.pishimovie.utils.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Standalone Subtitle Settings host.
 *
 * Launched from [VideoPlayerActivity]'s settings menu so the user can
 * tweak subtitle appearance without leaving the player context. When a
 * [EXTRA_VIDEO_URL] is supplied, the screen operates in per-video mode
 * (delay / size / language / selected track overrides apply only to that
 * video); otherwise it edits global defaults.
 *
 * The Activity is thin — it only wires up the theme + edge-to-edge + the
 * [SubtitleSettingsScreen] composable with [onClose] set to `finish()`.
 */
class SubtitleSettingsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIDEO_URL = "video_url"

        /** Launches the Subtitle Settings screen in global mode. */
        fun start(context: Context) {
            val intent = Intent(context, SubtitleSettingsActivity::class.java)
            context.startActivity(intent)
        }

        /**
         * Launches the Subtitle Settings screen in per-video mode for the
         * given [videoUrl]. Per-video overrides apply only to this URL.
         */
        fun startForVideo(context: Context, videoUrl: String) {
            val intent = Intent(context, SubtitleSettingsActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_URL, videoUrl)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Match the rest of the app: portrait on phones, no orientation on TV.
        if (!DeviceUtils.isTv(this)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            enableEdgeToEdge()
        }

        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)?.takeIf { it.isNotBlank() }

        setContent {
            val themeSettingsState = rememberAppThemeSettings()
            val context = LocalContext.current
            val fontSettings by produceState(initialValue = FontSettings(), context) {
                value = withContext(Dispatchers.IO) { StorageUtils.loadFontSettings(context.applicationContext) }
            }

            PishiMovieTheme(themeSettingsState.value, fontSettings) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    SubtitleSettingsScreen(
                        navController = null,
                        videoUrl = videoUrl,
                        onClose = { finish() },
                    )
                }
            }
        }
    }
}
