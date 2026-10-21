package com.pishimovie.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.pishimovie.R
import com.pishimovie.util.AppUpdateChecker
import com.pishimovie.util.AppUpdateInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/** The app-root [AppUpdateController], provided by MainScreen. */
val LocalAppUpdateController = staticCompositionLocalOf<AppUpdateController> {
    error("AppUpdateController not provided")
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    return "%.1f MB".format(kb / 1024.0)
}

/**
 * Shared state machine behind Settings → "App update" and the on-launch
 * update popup. One controller lives at the app root so both surfaces
 * observe the same check/download, and a download started from either
 * place is visible (and cancellable) in both.
 */
class AppUpdateController(private val context: Context) {

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val info: AppUpdateInfo) : State
        data class Downloading(val info: AppUpdateInfo, val downloaded: Long, val total: Long) : State
        data class Downloaded(val info: AppUpdateInfo, val file: File) : State
        data class Failed(val message: String) : State
    }

    var state by mutableStateOf<State>(State.Idle)
        private set

    /** Set by the launch auto-check (when an update is found) and by the
     *  Settings card; the dialog host in MainScreen observes it. */
    var dialogRequested by mutableStateOf(false)

    val currentVersion: String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "?"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var downloadJob: Job? = null

    fun check() {
        if (state is State.Checking || state is State.Downloading) return
        state = State.Checking
        scope.launch {
            state = try {
                AppUpdateChecker.fetchLatestNewerThan(currentVersion)
                    ?.let { State.Available(it) }
                    ?: State.UpToDate
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                State.Failed(e.message ?: "Update check failed")
            }
        }
    }

    fun download(info: AppUpdateInfo) {
        if (state is State.Downloading) return
        val dest = File(File(context.cacheDir, "shared"), "pishimovie-update-${info.version}.apk")
        state = State.Downloading(info, 0L, info.sizeBytes)
        downloadJob = scope.launch {
            try {
                AppUpdateChecker.download(info.downloadUrl, dest) { downloaded, total ->
                    state = State.Downloading(info, downloaded, total)
                }
                state = State.Downloaded(info, dest)
            } catch (e: CancellationException) {
                File(dest.parentFile, dest.name + ".part").delete()
                state = State.Available(info)
            } catch (e: Exception) {
                File(dest.parentFile, dest.name + ".part").delete()
                state = State.Failed(e.message ?: "Download failed")
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }

    /** Opens the system installer; routes to the unknown-apps consent first when needed. */
    fun install(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val consent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(consent) }
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}

/**
 * The update popup (launch prompt and Settings share it): current → new
 * version chips, then Download with live progress, then Install.
 */
@Composable
fun AppUpdateDialog(controller: AppUpdateController, onDismiss: () -> Unit) {
    val state = controller.state
    val info = when (state) {
        is AppUpdateController.State.Available -> state.info
        is AppUpdateController.State.Downloading -> state.info
        is AppUpdateController.State.Downloaded -> state.info
        else -> null
    } ?: return

    AlertDialog(
        onDismissRequest = { if (state !is AppUpdateController.State.Downloading) onDismiss() },
        icon = { Icon(Icons.Filled.SystemUpdate, null) },
        title = { Text(stringResource(R.string.update_available)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.new_version_ready))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.version_current), style = MaterialTheme.typography.labelSmall)
                            Text("v${controller.currentVersion}", style = MaterialTheme.typography.titleMedium)
                        }
                        Icon(Icons.Filled.ArrowForward, null, Modifier.size(20.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.version_new), style = MaterialTheme.typography.labelSmall)
                            Text(
                                "v${info.version}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                Text(
                    "${info.apkName} · ${formatSize(info.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state is AppUpdateController.State.Downloading) {
                    val total = if (state.total > 0) state.total else info.sizeBytes
                    val fraction = if (total > 0) (state.downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${formatSize(state.downloaded)} / ${formatSize(total)}", style = MaterialTheme.typography.bodySmall)
                        Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                is AppUpdateController.State.Available ->
                    Button(onClick = { controller.download(state.info) }) { Text(stringResource(R.string.download_update)) }
                is AppUpdateController.State.Downloading ->
                    Button(onClick = { controller.cancelDownload() }) { Text(stringResource(R.string.cancel)) }
                is AppUpdateController.State.Downloaded ->
                    Button(onClick = { controller.install(state.file); onDismiss() }) { Text(stringResource(R.string.install_update)) }
                else -> {}
            }
        },
        dismissButton = {
            if (state !is AppUpdateController.State.Downloading) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.later)) }
            }
        },
    )
}

/**
 * Settings → About card for the in-app updater: current version, live
 * check status, and the "Check for update" action (styled like the other
 * settings cards). Tapping the card while an update is available /
 * downloading / downloaded re-opens the shared update dialog.
 */
@Composable
fun AppUpdateSettingsCard(controller: AppUpdateController) {
    val state = controller.state
    val dialogWorthy = state is AppUpdateController.State.Available ||
        state is AppUpdateController.State.Downloading ||
        state is AppUpdateController.State.Downloaded
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (dialogWorthy) {
                    Modifier.clickable { controller.dialogRequested = true }
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_update),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${stringResource(R.string.current_version)}: v${controller.currentVersion}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val statusText = when (state) {
                    is AppUpdateController.State.Checking -> stringResource(R.string.update_checking)
                    is AppUpdateController.State.UpToDate -> stringResource(R.string.up_to_date)
                    is AppUpdateController.State.Failed -> stringResource(R.string.update_check_failed)
                    is AppUpdateController.State.Available -> "v${state.info.version}"
                    is AppUpdateController.State.Downloading ->
                        "${formatSize(state.downloaded)} / ${formatSize(state.total)}"
                    is AppUpdateController.State.Downloaded -> "v${state.info.version}"
                    else -> ""
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (dialogWorthy) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                TextButton(
                    onClick = {
                        controller.check()
                        controller.dialogRequested = true
                    },
                    enabled = state !is AppUpdateController.State.Checking &&
                        state !is AppUpdateController.State.Downloading,
                ) {
                    Text(stringResource(R.string.check_for_update))
                }
            }
        }
    }
}
