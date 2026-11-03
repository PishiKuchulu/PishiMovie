package com.pishimovie.util

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** A newer signed release published on GitHub, with the APK for this device. */
data class AppUpdateInfo(
    val version: String,
    val tag: String,
    val apkName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    /** Download URL of the release's `<apkName>.sha256` asset, or null when
     *  the release does not publish one (verification then falls back to
     *  the size check only). */
    val sha256Url: String? = null,
)

/**
 * In-app update check against this app's own distribution channel —
 * the versioned GitHub releases of PishiKuchulu/PishiMovie. Deliberately
 * NOT the `/releases/latest` endpoint: the rolling debug release (tag
 * `latest`) is republished on every main build and could shadow a
 * versioned release, so the list is scanned for strict `vX.Y.Z` tags and
 * the highest wins. The asset for the device's primary ABI is preferred,
 * with the universal APK as fallback. No token needed (public repo), no
 * data leaves the device beyond the request itself.
 */
object AppUpdateChecker {

    private const val TAG = "AppUpdateChecker"
    private const val RELEASES_URL =
        "https://api.github.com/repos/PishiKuchulu/PishiMovie/releases?per_page=20"
    private const val USER_AGENT = "PishiMovie-Updater"

    /** The newest release strictly newer than [currentVersion], or null. */
    suspend fun fetchLatestNewerThan(currentVersion: String): AppUpdateInfo? =
        withContext(Dispatchers.IO) {
            val releases = JSONArray(httpGet(RELEASES_URL))
            val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
            var best: AppUpdateInfo? = null
            for (i in 0 until releases.length()) {
                val rel = releases.optJSONObject(i) ?: continue
                if (rel.optBoolean("draft") || rel.optBoolean("prerelease")) continue
                val tag = rel.optString("tag_name")
                val version = VersionNumbers.parse(tag)?.joinToString(".") ?: continue
                if (!VersionNumbers.isNewer(version, currentVersion)) continue
                val current = best
                if (current != null && !VersionNumbers.isNewer(version, current.version)) continue
                val assets = rel.optJSONArray("assets")
                val asset = pickAsset(assets, version, abi) ?: continue
                val apkName = asset.optString("name")
                best = AppUpdateInfo(
                    version = version,
                    tag = tag,
                    apkName = apkName,
                    downloadUrl = asset.optString("browser_download_url"),
                    sizeBytes = asset.optLong("size"),
                    // The signed-release workflow publishes "<apk>.sha256"
                    // next to each APK; used to verify the download.
                    sha256Url = findAssetUrl(assets, "$apkName.sha256"),
                )
            }
            best
        }

    /** browser_download_url of the asset named [name], or null if absent. */
    private fun findAssetUrl(assets: JSONArray?, name: String): String? {
        if (assets == null) return null
        for (j in 0 until assets.length()) {
            val asset = assets.optJSONObject(j) ?: continue
            if (asset.optString("name") == name) {
                return asset.optString("browser_download_url").takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun pickAsset(assets: JSONArray?, version: String, abi: String): JSONObject? {
        if (assets == null) return null
        var universal: JSONObject? = null
        for (j in 0 until assets.length()) {
            val asset = assets.optJSONObject(j) ?: continue
            when (asset.optString("name")) {
                "PishiMovie-$version-$abi.apk" -> return asset
                "PishiMovie-$version.apk" -> universal = asset
            }
        }
        return universal
    }

    /**
     * Downloads [info]'s APK to [dest] (via a `.part` sibling), reporting
     * (downloaded, total) progress, then VERIFIES the result before it may
     * be installed:
     *  1. the byte count must equal the release metadata's [AppUpdateInfo.sizeBytes];
     *  2. when the release publishes a `.sha256` asset ([AppUpdateInfo.sha256Url]),
     *     the file's SHA-256 must match it (a listed-but-unreadable or
     *     malformed checksum fails the download — never silently skipped);
     *  3. with no `.sha256` asset, the size check alone applies (logged).
     * Any mismatch deletes the file and throws [IOException] with the
     * reason, which the controller surfaces as its Failed state.
     * Cancellation lands between chunks; a cancelled or failed attempt
     * only leaves the `.part` file, which the caller removes.
     */
    suspend fun download(
        info: AppUpdateInfo,
        dest: File,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ) {
        withContext(Dispatchers.IO) {
            val conn = (URL(info.downloadUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 30_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/octet-stream")
            }
            try {
                val code = conn.responseCode
                check(code in 200..299) { "Download failed (HTTP $code)" }
                val total = conn.contentLengthLong
                dest.parentFile?.mkdirs()
                val part = File(dest.parentFile, dest.name + ".part")
                var downloaded = 0L
                conn.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            downloaded += n
                            onProgress(downloaded, if (total > 0) total else downloaded)
                        }
                    }
                }
                // (1) Size integrity — a truncated or padded stream must
                // never reach the installer.
                if (info.sizeBytes > 0 && downloaded != info.sizeBytes) {
                    part.delete()
                    throw IOException(
                        "Update size mismatch: expected ${info.sizeBytes} bytes, got $downloaded"
                    )
                }
                if (dest.exists()) dest.delete()
                check(part.renameTo(dest)) { "Could not finalize the downloaded update" }
                // (2)/(3) Hash integrity against the release checksum asset.
                val sha256Url = info.sha256Url
                if (sha256Url != null) {
                    val expected = runCatching { httpGet(sha256Url) }.getOrNull()
                        ?.trim()
                        ?.split(Regex("\\s+"))
                        ?.firstOrNull()
                        ?.lowercase()
                        ?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
                    if (expected == null) {
                        dest.delete()
                        throw IOException("Could not verify the update (checksum unreadable)")
                    }
                    if (sha256HexOfFile(dest) != expected) {
                        dest.delete()
                        throw IOException("Update integrity check failed (SHA-256 mismatch)")
                    }
                } else {
                    PLog.w(TAG, "Release publishes no .sha256 asset; update verified by size only")
                }
            } finally {
                conn.disconnect()
            }
        }
    }

    /** Streaming SHA-256 hex digest of [file]. */
    private fun sha256HexOfFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            val code = conn.responseCode
            check(code in 200..299) { "Update check failed (HTTP $code)" }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
