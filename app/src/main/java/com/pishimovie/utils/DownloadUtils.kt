package com.pishimovie.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import com.pishimovie.R
import com.pishimovie.VideoPlayerActivity

object DownloadUtils {
    /**
     * HTTP headers the streaming backends require (hotlink protection).
     * Must stay identical to the internal pipeline's values
     * ([com.pishimovie.player.MediaSourceFactory]).
     */
    private const val STREAM_REFERER = "https://server-hi-speed-iran.info/"
    private const val STREAM_USER_AGENT = "PishiMovie/2.0 (Android; Media3/ExoPlayer)"

    // ─── External player / downloader packages ─────────────────────────
    // Single source of truth for the package names used by the open-with
    // intents below AND by the player/download dialogs, which disable the
    // options whose target app is not installed.
    const val VLC_PACKAGE = "org.videolan.vlc"
    val MX_PACKAGES = listOf("com.mxtech.videoplayer.ad", "com.mxtech.videoplayer.pro")
    val KM_PACKAGES = listOf("com.kmplayer", "com.kmplayerpro", "com.kmplayer.google", "com.kmplayer.d")
    val ADM_PACKAGES = listOf("com.dv.adm", "com.dv.adm.pay", "com.dv.get", "com.dv.adm.old")

    /** True when [packageName] is installed (and visible) on this device. */
    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    /** True when at least one of [packageNames] is installed. */
    fun isAnyPackageInstalled(context: Context, packageNames: List<String>): Boolean =
        packageNames.any { isPackageInstalled(context, it) }

    /**
     * Outbound URLs come from the backend and are handed to other apps, so
     * only http/https may ever leave the app through an intent. Anything
     * else (file:, intent:, javascript:, …) is rejected with a toast.
     */
    private fun hasSupportedScheme(url: String): Boolean {
        val scheme = Uri.parse(url).scheme?.lowercase()
        return scheme == "http" || scheme == "https"
    }

    /** Returns true (after showing a toast) when [url] must not be opened. */
    private fun rejectUnsupportedScheme(context: Context, url: String): Boolean {
        if (hasSupportedScheme(url)) return false
        Toast.makeText(context, R.string.e4_invalid_link, Toast.LENGTH_LONG).show()
        return true
    }

    /**
     * MX Player documents a "headers" intent extra: a String[] of alternating
     * key/value pairs applied as HTTP request headers. VLC and KMPlayer
     * document no header support — the same extra is passed best-effort
     * (ignored if unsupported).
     */
    private fun withStreamHeaders(intent: Intent): Intent {
        intent.putExtra("headers", arrayOf("Referer", STREAM_REFERER, "User-Agent", STREAM_USER_AGENT))
        return intent
    }

    fun openUrl(context: Context, url: String) {
        if (rejectUnsupportedScheme(context, url)) return
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (e: Exception) {
            // Show a generic, localized error — never the raw exception text.
            Toast.makeText(context, R.string.e4_unable_to_open_link, Toast.LENGTH_LONG).show()
        }
    }

    fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Video URL", text)
        clipboard.setPrimaryClip(clip)

        // Show a toast or snackbar to indicate success
        Toast.makeText(context, R.string.e4_link_copied, Toast.LENGTH_SHORT).show()
    }

    fun openWithADM(context: Context, url: String) {
        if (rejectUnsupportedScheme(context, url)) return
        try {
            // Try the known ADM package names in order.
            var success = false
            for (pkg in ADM_PACKAGES) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    intent.`package` = pkg
                    context.startActivity(intent)
                    success = true
                    break
                } catch (e: Exception) {
                    // Try next package
                }
            }

            // If none of the specific packages work, try the general approach
            if (!success) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("adm://$url"))
                    context.startActivity(intent)
                    success = true
                } catch (e: Exception) {
                    // Continue to fallback
                }
            }

            // If all else fails, show error message
            if (!success) {
                Toast.makeText(context, R.string.e4_adm_not_installed, Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            // If ADM is not installed, show error message
            Toast.makeText(context, R.string.e4_adm_not_installed, Toast.LENGTH_LONG).show()
        }
    }

    fun openWithVLC(context: Context, url: String) {
        if (rejectUnsupportedScheme(context, url)) return
        try {
            // VLC documents no header extra — passed best-effort (ignored if
            // unsupported); header-gated streams may still 403 in VLC.
            val intent = withStreamHeaders(Intent(Intent.ACTION_VIEW))
            intent.setDataAndType(Uri.parse(url), "video/*")
            intent.setPackage(VLC_PACKAGE)
            context.startActivity(intent)
        } catch (e: Exception) {
            // If VLC is not installed, show a message
            Toast.makeText(context, R.string.e4_vlc_not_installed, Toast.LENGTH_SHORT).show()
        }
    }

    fun openWithMXPlayer(context: Context, url: String) {
        if (rejectUnsupportedScheme(context, url)) return
        // MX_PACKAGES is ordered free → pro, mirroring the previous fallback.
        for (pkg in MX_PACKAGES) {
            try {
                val intent = withStreamHeaders(Intent(Intent.ACTION_VIEW))
                intent.setDataAndType(Uri.parse(url), "video/*")
                intent.setPackage(pkg)
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                // Try next package
            }
        }
        // If MX Player is not installed, show a message
        Toast.makeText(context, R.string.e4_mx_not_installed, Toast.LENGTH_SHORT).show()
    }

    fun openWithKMPlayer(context: Context, url: String) {
        if (rejectUnsupportedScheme(context, url)) return
        var success = false
        for (pkg in KM_PACKAGES) {
            try {
                // KMPlayer documents no header extra — passed best-effort.
                val intent = withStreamHeaders(Intent(Intent.ACTION_VIEW))
                intent.setDataAndType(Uri.parse(url), "video/*")
                intent.setPackage(pkg)
                context.startActivity(intent)
                success = true
                break
            } catch (e: Exception) {
                // Try next package
            }
        }

        // If none of the specific packages work, try a general approach
        if (!success) {
            try {
                val intent = withStreamHeaders(Intent(Intent.ACTION_VIEW))
                intent.setDataAndType(Uri.parse(url), "video/*")
                context.startActivity(intent)
                success = true
            } catch (e: Exception) {
                // If all else fails, show error message
                Toast.makeText(context, R.string.e4_km_not_installed, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
