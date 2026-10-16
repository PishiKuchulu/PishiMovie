package com.pishimovie.download

import com.pishimovie.util.PLog
import android.app.DownloadManager as SystemDownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * PishiMovie Download Manager (Phase 1 refactor).
 *
 * **What changed:** the previous implementation downloaded files itself via a
 * raw `HttpURLConnection` loop. That was unreliable on the streaming backends
 * this app uses (no Referer header, no cross-protocol redirect handling, no
 * system-level retry/resume). It has been replaced by the **system
 * [android.app.DownloadManager]**, which is more robust and gives the user
 * the standard Android download notification + resume-on-network-change
 * behavior for free.
 *
 * **What's preserved (no breaking changes):**
 *  - The `DownloadItem` model and its `StateFlow<List<DownloadItem>>` API.
 *  - `enqueue()` signature — both movies and series call the same method
 *    after picking a quality from their respective dialogs.
 *  - `pause / resume / retry / cancel / remove / clearCompleted / clearAll`.
 *  - History persistence to `download_history.json`.
 *
 * **How it works now:**
 *  - `enqueue()` creates a [DownloadItem] in QUEUED state, then submits a
 *    [SystemDownloadManager.Request] to the system DM. The system-assigned
 *    download ID is stored on the item as `systemDownloadId`.
 *  - A polling coroutine (every 1s) reads each active download's progress
 *    from the system DM via its download ID and updates the in-memory item.
 *  - A [BroadcastReceiver] for `ACTION_DOWNLOAD_COMPLETE` marks the item
 *    COMPLETED or FAILED based on the download's status.
 *
 * Movies and series are unified: both call `enqueue()` with a `Source.url`
 * chosen from a quality-selection dialog. The available qualities come from
 * `movie.sources` / `episode.sources` (the API already returns 480p/720p/
 * 1080p/4K when available).
 */
class DownloadManager private constructor(private val context: Context) {

    private val tag = "DownloadManager"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    /** Maps our item ID → system DownloadManager download ID (0 = not queued yet). */
    private val systemIds = ConcurrentHashMap<String, Long>()

    /** Maps system download ID → our item ID (for the broadcast receiver). */
    private val reverseIds = ConcurrentHashMap<Long, String>()

    /** Active progress-polling jobs (one per downloading item). */
    private val progressJobs = ConcurrentHashMap<String, Job>()

    private val historyFile by lazy { File(context.filesDir, "download_history.json") }
    private val downloadsDir by lazy {
        File(context.getExternalFilesDir(null) ?: context.filesDir, "pishimovie_downloads").apply { mkdirs() }
    }

    private val systemDM: SystemDownloadManager? by lazy {
        try {
            context.getSystemService(Context.DOWNLOAD_SERVICE) as? SystemDownloadManager
        } catch (e: Exception) {
            PLog.e(tag, "System DownloadManager unavailable", e)
            null
        }
    }

    @Volatile private var receiverRegistered = false

    init {
        loadHistory()
        registerCompletionReceiver()
    }

    // ─── Public API ─────────────────────────────────────────────────────

    /**
     * Enqueues a new download via the system DownloadManager. Same signature
     * as before so movie + series flows both use it identically.
     */
    fun enqueue(
        title: String,
        type: String,
        contentId: Int,
        posterUrl: String,
        sourceUrl: String,
        quality: String,
        episodeId: Int? = null,
        seasonId: Int? = null,
    ): DownloadItem {
        // De-dupe by sourceUrl.
        _downloads.value.find { it.sourceUrl == sourceUrl && !it.isTerminal }?.let { existing ->
            return when (existing.state) {
                // A failed download CAN be restarted: reset it and submit anew
                // instead of silently returning the dead item.
                DownloadState.FAILED.name -> {
                    updateItem(existing.id) {
                        it.copy(
                            state = DownloadState.QUEUED.name,
                            errorMessage = null,
                            downloadedBytes = 0L,
                            totalBytes = 0L,
                        )
                    }
                    saveHistory()
                    val refreshed = _downloads.value.find { it.id == existing.id } ?: existing
                    submitToSystemDM(refreshed)
                    _downloads.value.find { it.id == existing.id } ?: refreshed
                }
                // A paused download goes through resume() (which re-submits).
                DownloadState.PAUSED.name -> {
                    resume(existing.id)
                    _downloads.value.find { it.id == existing.id } ?: existing
                }
                else -> existing
            }
        }

        val id = UUID.randomUUID().toString()
        val extension = guessExtension(sourceUrl)
        val savePath = File(downloadsDir, "${id}.${extension}").absolutePath
        val item = DownloadItem(
            id = id,
            title = title,
            type = type,
            contentId = contentId,
            posterUrl = posterUrl,
            sourceUrl = sourceUrl,
            savePath = savePath,
            quality = quality,
            episodeId = episodeId,
            seasonId = seasonId,
        )
        updateDownloads { listOf(item) + it }
        saveHistory()

        submitToSystemDM(item)
        return _downloads.value.find { it.id == id } ?: item
    }

    /**
     * Pauses a download by cancelling its system download.
     *
     * NOTE: [android.app.DownloadManager.remove] also deletes the partially
     * downloaded file (documented platform behavior), so [resume] restarts
     * the download from 0 — progress is NOT preserved across pause/resume.
     */
    fun pause(id: String) {
        val sysId = systemIds[id]
        if (sysId != null && sysId > 0L) {
            try { systemDM?.remove(sysId) } catch (_: Exception) {}
        }
        systemIds.remove(id)
        reverseIds.entries.removeIf { it.value == id }
        progressJobs[id]?.cancel()
        progressJobs.remove(id)
        updateItem(id) { it.copy(state = DownloadState.PAUSED.name) }
        saveHistory()
    }

    /**
     * Resumes a paused or failed download by re-submitting it to the system
     * DM from scratch (see [pause]: partial progress is not preserved —
     * resume always restarts at byte 0).
     */
    fun resume(id: String) {
        val item = _downloads.value.find { it.id == id } ?: return
        // Clear partial file on resume-from-failed so we don't keep a corrupt file.
        if (item.state == DownloadState.FAILED.name) {
            try { File(item.savePath).delete() } catch (_: Exception) {}
        }
        updateItem(id) {
            it.copy(state = DownloadState.QUEUED.name, errorMessage = null, downloadedBytes = 0L, totalBytes = 0L)
        }
        saveHistory()
        submitToSystemDM(item)
    }

    /** Cancels a download and deletes the partial file. */
    fun cancel(id: String) {
        val sysId = systemIds[id]
        if (sysId != null && sysId > 0L) {
            try { systemDM?.remove(sysId) } catch (_: Exception) {}
        }
        systemIds.remove(id)
        reverseIds.entries.removeIf { it.value == id }
        progressJobs[id]?.cancel()
        progressJobs.remove(id)
        updateItem(id) { it.copy(state = DownloadState.CANCELLED.name, downloadedBytes = 0L) }
        try {
            val item = _downloads.value.find { it.id == id }
            item?.let { File(it.savePath).delete() }
        } catch (_: Exception) {}
        saveHistory()
    }

    /** Retries a failed download. */
    fun retry(id: String) = resume(id)

    /** Removes an entry (and its file) from the list. */
    fun remove(id: String) {
        cancel(id)
        updateDownloads { list -> list.filterNot { it.id == id } }
        saveHistory()
    }

    /** Clears all completed downloads (files + entries). */
    fun clearCompleted() {
        val toRemove = _downloads.value.filter { it.state == DownloadState.COMPLETED.name }
        toRemove.forEach { item ->
            try { File(item.savePath).delete() } catch (_: Exception) {}
        }
        updateDownloads { list -> list.filterNot { it.state == DownloadState.COMPLETED.name } }
        saveHistory()
    }

    /** Clears ALL downloads — used by the "Delete All" trash action. */
    fun clearAll() {
        systemIds.keys.toList().forEach { id ->
            val sysId = systemIds[id]
            if (sysId != null && sysId > 0L) {
                try { systemDM?.remove(sysId) } catch (_: Exception) {}
            }
        }
        systemIds.clear()
        reverseIds.clear()
        progressJobs.values.forEach { it.cancel() }
        progressJobs.clear()
        _downloads.value.forEach { item ->
            try { File(item.savePath).delete() } catch (_: Exception) {}
        }
        updateDownloads { emptyList() }
        saveHistory()
    }

    /** Returns the total bytes used by all completed downloads. */
    fun storageUsageBytes(): Long {
        return _downloads.value
            .filter { it.state == DownloadState.COMPLETED.name }
            .sumOf { File(it.savePath).length() }
    }

    /** Returns the number of active (downloading) items. */
    fun activeCount(): Int = _downloads.value.count { it.state == DownloadState.DOWNLOADING.name }

    /**
     * Called on app startup to auto-resume interrupted downloads.
     *
     * Before re-submitting, existing system downloads are ADOPTED: the system
     * DM survives process death but our item→system-ID maps do not, so we
     * match live system downloads against our destination filenames and
     * rebuild the maps instead of creating duplicates. Only items with no
     * matching live download are re-submitted.
     */
    fun resumeInterrupted() {
        adoptSystemDownloads()
        // Re-submit any item left in QUEUED or DOWNLOADING (crashed mid-download),
        // unless it was just adopted above.
        _downloads.value
            .filter { it.state == DownloadState.QUEUED.name || it.state == DownloadState.DOWNLOADING.name }
            .filter { item -> !systemIds.containsKey(item.id) }
            .forEach { item ->
                updateItem(item.id) { it.copy(state = DownloadState.QUEUED.name, downloadedBytes = 0L) }
                submitToSystemDM(item)
            }
    }

    /**
     * Rebuilds [systemIds]/[reverseIds] from downloads still tracked by the
     * system DownloadManager, matching on our destination filenames
     * ("<itemId>.<ext>"). Adopted downloads resume progress polling instead
     * of being re-submitted (which would corrupt the destination file with
     * two concurrent writers).
     */
    private fun adoptSystemDownloads() {
        val dm = systemDM ?: return
        try {
            val byFileName = _downloads.value.associateBy { File(it.savePath).name }
            val query = SystemDownloadManager.Query()
            dm.query(query).use { cursor ->
                if (cursor == null) return
                val idCol = cursor.getColumnIndexOrThrow(SystemDownloadManager.COLUMN_ID)
                val uriCol = cursor.getColumnIndexOrThrow(SystemDownloadManager.COLUMN_LOCAL_URI)
                val statusCol = cursor.getColumnIndexOrThrow(SystemDownloadManager.COLUMN_STATUS)
                while (cursor.moveToNext()) {
                    val sysId = cursor.getLong(idCol)
                    val localUri = try { cursor.getString(uriCol) } catch (_: Exception) { null }
                    val status = try { cursor.getInt(statusCol) } catch (_: Exception) { -1 }
                    if (status == SystemDownloadManager.STATUS_FAILED) continue
                    val fileName = localUri?.let { Uri.parse(it).lastPathSegment }
                    val item = fileName?.let { byFileName[it] } ?: continue
                    // Only adopt items we still consider active.
                    if (item.state != DownloadState.QUEUED.name &&
                        item.state != DownloadState.DOWNLOADING.name &&
                        item.state != DownloadState.PAUSED.name
                    ) continue
                    systemIds[item.id] = sysId
                    reverseIds[sysId] = item.id
                    when (status) {
                        SystemDownloadManager.STATUS_SUCCESSFUL -> {
                            // Finished while we were dead (broadcast missed).
                            updateItem(item.id) {
                                it.copy(
                                    state = DownloadState.COMPLETED.name,
                                    completedAt = System.currentTimeMillis(),
                                    errorMessage = null,
                                )
                            }
                        }
                        else -> {
                            updateItem(item.id) { it.copy(state = DownloadState.DOWNLOADING.name) }
                            startProgressPolling(item.id, sysId)
                        }
                    }
                }
            }
            saveHistory()
        } catch (e: Exception) {
            PLog.w(tag, "Failed to adopt system downloads", e)
        }
    }

    // ─── System DownloadManager integration ────────────────────────────

    private fun submitToSystemDM(item: DownloadItem) {
        val dm = systemDM
        if (dm == null) {
            updateItem(item.id) {
                it.copy(state = DownloadState.FAILED.name, errorMessage = "DownloadManager unavailable")
            }
            saveHistory()
            return
        }
        // Adaptive streams (.m3u8/.mpd) are playlists, not media files: the
        // system DM would fetch only the manifest and mark it COMPLETED.
        // Refuse explicitly instead of producing a bogus "video" file.
        if (isAdaptiveStream(item.sourceUrl)) {
            updateItem(item.id) {
                it.copy(
                    state = DownloadState.FAILED.name,
                    errorMessage = "Adaptive streams (.m3u8/.mpd) can't be downloaded for offline playback",
                )
            }
            saveHistory()
            return
        }
        try {
            val request = SystemDownloadManager.Request(Uri.parse(item.sourceUrl)).apply {
                setTitle("PishiMovie — ${item.title}")
                setDescription("${item.quality} • ${item.type}")
                setNotificationVisibility(SystemDownloadManager.Request.VISIBILITY_VISIBLE)
                setVisibleInDownloadsUi(false)
                // Custom HTTP headers — mirrors the player's config so backends
                // that require a UA / Referer accept the download too.
                addRequestHeader("User-Agent", "PishiMovie/1.0 (Android)")
                addRequestHeader("Referer", "https://server-hi-speed-iran.info/")
                addRequestHeader("Accept", "*/*")
                // Save into our private downloads dir (scoped storage friendly).
                val ext = guessExtension(item.sourceUrl)
                setDestinationUri(Uri.fromFile(File(downloadsDir, "${item.id}.${ext}")))
                // Allow on metered networks too (user choice), but never while
                // roaming — auto-resume on app start must not burn roaming data.
                setAllowedNetworkTypes(
                    SystemDownloadManager.Request.NETWORK_WIFI or
                        SystemDownloadManager.Request.NETWORK_MOBILE
                )
                setAllowedOverRoaming(false)
            }
            val sysId = dm.enqueue(request)
            systemIds[item.id] = sysId
            reverseIds[sysId] = item.id
            updateItem(item.id) {
                it.copy(state = DownloadState.DOWNLOADING.name, startedAt = System.currentTimeMillis())
            }
            saveHistory()
            startProgressPolling(item.id, sysId)
        } catch (e: Exception) {
            PLog.e(tag, "Failed to enqueue system download: ${item.title}", e)
            updateItem(item.id) {
                it.copy(state = DownloadState.FAILED.name, errorMessage = e.message)
            }
            saveHistory()
        }
    }

    private fun startProgressPolling(itemId: String, sysId: Long) {
        progressJobs[itemId]?.cancel()
        val job = scope.launch {
            var keepPolling = true
            while (keepPolling) {
                try {
                    val dm = systemDM
                    if (dm == null) { keepPolling = false; break }
                    val query = SystemDownloadManager.Query().setFilterById(sysId)
                    var stopAfterCursor = false
                    dm.query(query).use { cursor ->
                        if (cursor != null && cursor.moveToFirst()) {
                            val status = cursor.getInt(cursor.getColumnIndexOrThrow(SystemDownloadManager.COLUMN_STATUS))
                            val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(SystemDownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            val total = cursor.getLong(cursor.getColumnIndexOrThrow(SystemDownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                            when (status) {
                                SystemDownloadManager.STATUS_RUNNING -> {
                                    updateItem(itemId) {
                                        it.copy(
                                            state = DownloadState.DOWNLOADING.name,
                                            downloadedBytes = downloaded,
                                            totalBytes = if (total > 0) total else it.totalBytes,
                                        )
                                    }
                                }
                                SystemDownloadManager.STATUS_PAUSED -> {
                                    // System paused (e.g. waiting for network). Surface as PAUSED.
                                    updateItem(itemId) { it.copy(state = DownloadState.PAUSED.name) }
                                }
                                SystemDownloadManager.STATUS_PENDING -> {
                                    updateItem(itemId) { it.copy(state = DownloadState.QUEUED.name) }
                                }
                                SystemDownloadManager.STATUS_SUCCESSFUL -> {
                                    // Completion is also handled by the broadcast receiver,
                                    // but update immediately for snappier UI.
                                    updateItem(itemId) {
                                        it.copy(
                                            state = DownloadState.COMPLETED.name,
                                            downloadedBytes = total.coerceAtLeast(downloaded),
                                            totalBytes = total.coerceAtLeast(downloaded),
                                            completedAt = System.currentTimeMillis(),
                                            errorMessage = null,
                                        )
                                    }
                                    stopAfterCursor = true
                                }
                                SystemDownloadManager.STATUS_FAILED -> {
                                    val reason = cursor.getInt(
                                        cursor.getColumnIndexOrThrow(SystemDownloadManager.COLUMN_REASON)
                                    )
                                    updateItem(itemId) {
                                        it.copy(
                                            state = DownloadState.FAILED.name,
                                            errorMessage = "Download failed (reason $reason)",
                                        )
                                    }
                                    stopAfterCursor = true
                                }
                            }
                        } else {
                            // Cursor empty → download no longer tracked by the system DM.
                            // If our state is still DOWNLOADING, mark FAILED.
                            val current = _downloads.value.find { it.id == itemId }
                            if (current != null && current.state == DownloadState.DOWNLOADING.name) {
                                updateItem(itemId) {
                                    it.copy(state = DownloadState.FAILED.name, errorMessage = "Download removed")
                                }
                            }
                            stopAfterCursor = true
                        }
                    }
                    if (stopAfterCursor) { keepPolling = false }
                } catch (e: Exception) {
                    PLog.w(tag, "Progress poll error for $itemId", e)
                }
                if (keepPolling) delay(1000)
            }
            progressJobs.remove(itemId)
        }
        progressJobs[itemId] = job
    }

    private fun registerCompletionReceiver() {
        if (receiverRegistered) return
        try {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val sysId = intent?.getLongExtra(SystemDownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                    if (sysId <= 0L) return
                    val itemId = reverseIds[sysId] ?: return
                    val dm = systemDM ?: return
                    val query = SystemDownloadManager.Query().setFilterById(sysId)
                    dm.query(query).use { cursor ->
                        if (cursor != null && cursor.moveToFirst()) {
                            val status = cursor.getInt(cursor.getColumnIndexOrThrow(SystemDownloadManager.COLUMN_STATUS))
                            if (status == SystemDownloadManager.STATUS_SUCCESSFUL) {
                                val localUri = cursor.getString(
                                    cursor.getColumnIndexOrThrow(SystemDownloadManager.COLUMN_LOCAL_URI)
                                )
                                updateItem(itemId) {
                                    it.copy(
                                        state = DownloadState.COMPLETED.name,
                                        downloadedBytes = it.totalBytes.coerceAtLeast(0L),
                                        completedAt = System.currentTimeMillis(),
                                        errorMessage = null,
                                    )
                                }
                            } else {
                                val reason = cursor.getInt(cursor.getColumnIndexOrThrow(SystemDownloadManager.COLUMN_REASON))
                                updateItem(itemId) {
                                    it.copy(
                                        state = DownloadState.FAILED.name,
                                        errorMessage = "Download failed (reason $reason)",
                                    )
                                }
                            }
                        }
                    }
                    systemIds.remove(itemId)
                    reverseIds.remove(sysId)
                    progressJobs[itemId]?.cancel()
                    progressJobs.remove(itemId)
                    saveHistory()
                }
            }
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(SystemDownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
        } catch (e: Exception) {
            PLog.w(tag, "Failed to register download-complete receiver", e)
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private fun guessExtension(url: String): String {
        val pathOnly = url.substringBefore('?').substringBefore('#')
        val ext = pathOnly.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return when (ext) {
            "mp4", "m4v", "mkv", "webm", "ts", "flac", "mp3", "aac", "m3u8", "mpd" -> ext
            else -> "mp4"
        }
    }

    /**
     * True for adaptive-stream playlists (.m3u8 HLS / .mpd DASH). Downloading
     * these as single files only saves the manifest — never mark them
     * COMPLETED. See [submitToSystemDM].
     */
    private fun isAdaptiveStream(url: String): Boolean {
        val pathOnly = url.substringBefore('?').substringBefore('#')
        val ext = pathOnly.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return ext == "m3u8" || ext == "m3u" || ext == "mpd"
    }

    private fun updateItem(id: String, transform: (DownloadItem) -> DownloadItem) {
        updateDownloads { list ->
            list.map { if (it.id == id) transform(it) else it }
        }
    }

    private fun updateDownloads(transform: (List<DownloadItem>) -> List<DownloadItem>) {
        _downloads.update(transform)
    }

    private fun saveHistory() {
        try {
            historyFile.writeText(json.encodeToString(_downloads.value))
        } catch (e: Exception) {
            PLog.w(tag, "Failed to save download history", e)
        }
    }

    private fun loadHistory() {
        try {
            if (historyFile.exists()) {
                val list = json.decodeFromString<List<DownloadItem>>(historyFile.readText())
                // Any items that were "DOWNLOADING" when the app died → mark QUEUED for auto-resume.
                _downloads.value = list.map { item ->
                    if (item.state == DownloadState.DOWNLOADING.name) {
                        item.copy(state = DownloadState.QUEUED.name)
                    } else item
                }
            }
        } catch (e: Exception) {
            PLog.w(tag, "Failed to load download history", e)
        }
    }

    companion object {
        @Volatile private var instance: DownloadManager? = null
        fun get(context: Context): DownloadManager =
            instance ?: synchronized(this) {
                instance ?: DownloadManager(context.applicationContext).also { instance = it }
            }
    }
}
