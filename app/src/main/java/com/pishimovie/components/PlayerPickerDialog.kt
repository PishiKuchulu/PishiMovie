package com.pishimovie.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pishimovie.R
import com.pishimovie.VideoPlayerActivity
import com.pishimovie.utils.DownloadUtils

/**
 * Issue 1: unified player-picker dialog.
 *
 * Shown before ANY playback (movies AND series). Lets the user choose:
 *  - Built-in Player → launches [VideoPlayerActivity] (ExoPlayer)
 *  - VLC → launches VLC via intent
 *  - MX Player → launches MX Player via intent
 *  - KMPlayer → launches KMPlayer via intent
 *  - Browser → opens the stream URL in the device's default browser
 *    (if a browser is installed)
 *
 * Both movies and series use this dialog so the playback system is identical.
 * The internal player is never forced — the user always picks.
 *
 * Options whose target player is not installed are disabled and labelled
 * «نصب نیست» instead of silently failing in the intent layer. The option
 * buttons live in the scrollable content area (not the confirmButton slot)
 * so the dialog fits small/landscape screens.
 *
 * @param videoUrl the stream URL to play
 * @param title content title (for the internal player's continue-watching)
 * @param image poster URL (for the internal player's continue-watching)
 * @param movieId optional movie ID
 * @param seriesId optional series ID
 * @param seasonId optional season ID
 * @param episodeId optional episode ID
 * @param resumePositionMs optional resume position (movies only)
 * @param onDismiss called when the dialog is dismissed
 */
@Composable
fun PlayerPickerDialog(
    videoUrl: String,
    title: String?,
    image: String?,
    movieId: Int? = null,
    seriesId: Int? = null,
    seasonId: Int? = null,
    episodeId: Int? = null,
    resumePositionMs: Long = 0L,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val playWithTitle = stringResource(R.string.play_with)
    val choosePlayerMsg = if (title != null) {
        stringResource(R.string.playerpicker_choose_player, title)
    } else {
        stringResource(R.string.choose_player, "")
    }
    val internalPlayerLabel = stringResource(R.string.internal_player)
    val vlcLabel = stringResource(R.string.vlc_player)
    val mxLabel = stringResource(R.string.mx_player)
    val kmLabel = stringResource(R.string.km_player)
    val browserLabel = stringResource(R.string.browser_player)
    val cancelLabel = stringResource(R.string.cancel)
    val notInstalledLabel = stringResource(R.string.e4_not_installed)

    // Which external players actually exist on this device? Options for
    // missing players are disabled below instead of failing on tap.
    val vlcInstalled = remember(context) {
        DownloadUtils.isPackageInstalled(context, DownloadUtils.VLC_PACKAGE)
    }
    val mxInstalled = remember(context) {
        DownloadUtils.isAnyPackageInstalled(context, DownloadUtils.MX_PACKAGES)
    }
    val kmInstalled = remember(context) {
        DownloadUtils.isAnyPackageInstalled(context, DownloadUtils.KM_PACKAGES)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = playWithTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            // Scrollable content: message + all player options. (The buttons
            // used to sit in the non-scrolling confirmButton slot and could
            // overflow small screens.)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = choosePlayerMsg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(4.dp))

                // ── Built-in Player (ExoPlayer) — “پخش داخلی” ────────────
                PlayerPickerButton(
                    icon = Icons.Default.PlayCircle,
                    label = internalPlayerLabel,
                    isPrimary = true,
                    onClick = {
                        VideoPlayerActivity.startCinematic(
                            context = context,
                            videoUrl = videoUrl,
                            title = title,
                            image = image,
                            movieId = movieId,
                            resumePositionMs = resumePositionMs,
                            seriesId = seriesId,
                            seasonId = seasonId,
                            episodeId = episodeId,
                        )
                        onDismiss()
                    },
                )

                // ── VLC ───────────────────────────────────────────────────
                PlayerPickerButton(
                    icon = Icons.Default.SmartDisplay,
                    label = vlcLabel,
                    isPrimary = false,
                    enabled = vlcInstalled,
                    notInstalledLabel = notInstalledLabel,
                    onClick = {
                        DownloadUtils.openWithVLC(context, videoUrl)
                        onDismiss()
                    },
                )

                // ── MX Player ────────────────────────────────────────────
                PlayerPickerButton(
                    icon = Icons.Default.OndemandVideo,
                    label = mxLabel,
                    isPrimary = false,
                    enabled = mxInstalled,
                    notInstalledLabel = notInstalledLabel,
                    onClick = {
                        DownloadUtils.openWithMXPlayer(context, videoUrl)
                        onDismiss()
                    },
                )

                // ── KMPlayer ─────────────────────────────────────────────
                PlayerPickerButton(
                    icon = Icons.Default.LiveTv,
                    label = kmLabel,
                    isPrimary = false,
                    enabled = kmInstalled,
                    notInstalledLabel = notInstalledLabel,
                    onClick = {
                        DownloadUtils.openWithKMPlayer(context, videoUrl)
                        onDismiss()
                    },
                )

                // ── Browser (opens URL in default browser if installed) ──
                PlayerPickerButton(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    label = browserLabel,
                    isPrimary = false,
                    onClick = {
                        DownloadUtils.openUrl(context, videoUrl)
                        onDismiss()
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(cancelLabel)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 6.dp,
    )
}

@Composable
private fun PlayerPickerButton(
    icon: ImageVector,
    label: String,
    isPrimary: Boolean,
    enabled: Boolean = true,
    notInstalledLabel: String? = null,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = if (isPrimary) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.padding(end = 8.dp))
        Text(
            text = if (!enabled && notInstalledLabel != null) {
                "$label ($notInstalledLabel)"
            } else {
                label
            },
        )
    }
}
