package com.pishimovie.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
            Text(
                text = choosePlayerMsg,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
                    icon = Icons.Default.Movie,
                    label = vlcLabel,
                    isPrimary = false,
                    onClick = {
                        DownloadUtils.openWithVLC(context, videoUrl)
                        onDismiss()
                    },
                )

                // ── MX Player ────────────────────────────────────────────
                PlayerPickerButton(
                    icon = Icons.Default.Movie,
                    label = mxLabel,
                    isPrimary = false,
                    onClick = {
                        DownloadUtils.openWithMXPlayer(context, videoUrl)
                        onDismiss()
                    },
                )

                // ── KMPlayer ─────────────────────────────────────────────
                PlayerPickerButton(
                    icon = Icons.Default.Movie,
                    label = kmLabel,
                    isPrimary = false,
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

                // ── Cancel ───────────────────────────────────────────────
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(cancelLabel)
                }
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
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
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
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(end = 8.dp))
        Text(label)
    }
}
