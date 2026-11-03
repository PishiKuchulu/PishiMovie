package com.pishimovie.screens

import com.pishimovie.ui.theme.ThemeType
import com.pishimovie.ui.theme.rememberAppThemeSettings
import com.pishimovie.ui.theme.rememberThemeManager
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.pishimovie.util.PLog
import com.pishimovie.ui.components.AppUpdateSettingsCard
import com.pishimovie.ui.components.LocalAppUpdateController
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.pishimovie.R
import com.pishimovie.data.model.ExternalPlayer
import com.pishimovie.data.model.VideoPlayerSettings
import com.pishimovie.utils.StorageUtils

/**
 * Issue 6 / Subtitle Center rewrite: Settings screen.
 *
 * Layout (2026-11-03, Farzad's accordion order): each section is ONE large
 * rounded collapsible card — bold title + chevron header, animated
 * expand/collapse — with its controls stacked flat inside. This replaced
 * the flat label + per-control-card layout.
 *
 * Sections:
 *  - Player: Built-in / VLC / MX Player / KMPlayer (chips)
 *  - Playback: Resume playback (switch) / Seek interval (slider) / Playback
 *    speed (slider) / **Subtitle Settings shortcut** — opens the dedicated
 *    [SubtitleSettingsScreen] (no inline subtitle controls anymore).
 *  - Storage: Cache / Watched episodes
 *  - About: About / Reset
 */
@Composable
fun SettingsScreen(
    navController: NavController? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themeManager = rememberThemeManager()
    val themeSettingsState = rememberAppThemeSettings()
    val themeSettings = themeSettingsState.value

    var videoPlayerSettings by remember { mutableStateOf(StorageUtils.loadVideoPlayerSettings(context)) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showClearWatchedEpisodesDialog by remember { mutableStateOf(false) }
    var watchedEpisodesCacheSize by remember { mutableStateOf(0L) }
    var cacheSize by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        watchedEpisodesCacheSize = try {
            val file = java.io.File(context.filesDir, "watched_episodes.json")
            if (file.exists()) file.length() else 0L
        } catch (e: Exception) {
            PLog.e("SettingsScreen", "Error calculating cache size", e)
            0L
        }
        cacheSize = com.pishimovie.util.PerformanceOptimizer.storageSummary(context).formatted()
    }

    fun updateVideoPlayerSettings(newSettings: VideoPlayerSettings) {
        videoPlayerSettings = newSettings
        StorageUtils.saveVideoPlayerSettings(context, newSettings)
    }

    fun clearWatchedEpisodes() {
        try {
            StorageUtils.clearAllWatchedEpisodes(context)
            watchedEpisodesCacheSize = 0L
            Toast.makeText(context, context.getString(R.string.watched_episodes_cleared), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            PLog.e("SettingsScreen", "Error clearing watched episodes", e)
            Toast.makeText(context, context.getString(R.string.error_clearing_cache_alt), Toast.LENGTH_SHORT).show()
        }
    }

    fun resetToDefaults() {
        // Reset video player settings here. Subtitle settings are reset on
        // the dedicated Subtitle Settings screen (cleaner separation of
        // concerns; avoids touching subtitle state from this screen).
        updateVideoPlayerSettings(VideoPlayerSettings.DEFAULT)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Header ────────────────────────────────────────────────────────
        item(key = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { navController?.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    text = stringResource(R.string.settings),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ══════════════════════════════════════════════════════════════════
        // Section: Appearance (Theme & Language)
        // ══════════════════════════════════════════════════════════════════
        item(key = "section_appearance") {
            SettingsAccordionSection(
                title = stringResource(R.string.theme_section),
                initiallyExpanded = true,
            ) {
                SettingsControl(icon = Icons.Default.Palette, title = stringResource(R.string.theme_section)) {
                    ThemeTypeChipRow(
                        current = themeSettings.themeType,
                        onSelect = { type ->
                            scope.launch {
                                val newSettings = themeSettings.copy(themeType = type)
                                themeManager.saveThemeSettings(newSettings)
                                themeSettingsState.value = newSettings
                            }
                        },
                    )
                }

                SettingsControl(icon = Icons.Default.Language, title = stringResource(R.string.language_setting)) {
                    LanguageChipRow(
                        onSelect = { langCode ->
                            val appLocale: androidx.core.os.LocaleListCompat = androidx.core.os.LocaleListCompat.forLanguageTags(langCode)
                            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(appLocale)
                        }
                    )
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════
        // Section: Player
        // ══════════════════════════════════════════════════════════════════
        item(key = "section_player") {
            SettingsAccordionSection(title = stringResource(R.string.player_section)) {
                SettingsControl(icon = Icons.Default.PlayCircle, title = stringResource(R.string.default_player)) {
                    PlayerChipRow(
                        current = videoPlayerSettings.preferredExternalPlayer,
                        onSelect = { ep ->
                            updateVideoPlayerSettings(
                                videoPlayerSettings.copy(preferredExternalPlayer = ep)
                            )
                        },
                    )
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════
        // Section: Playback
        // ══════════════════════════════════════════════════════════════════
        item(key = "section_playback") {
            SettingsAccordionSection(title = stringResource(R.string.playback_section)) {
                SettingsControl(icon = Icons.Default.Restore, title = stringResource(R.string.resume_playback)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.resume_playback_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = videoPlayerSettings.rememberLastPosition,
                            onCheckedChange = { v ->
                                updateVideoPlayerSettings(
                                    videoPlayerSettings.copy(rememberLastPosition = v)
                                )
                            },
                        )
                    }
                }

                SettingsControl(icon = Icons.Default.PlayCircle, title = stringResource(R.string.seek_interval)) {
                    SliderRow(
                        valueText = "${videoPlayerSettings.seekTimeSeconds}s",
                        value = videoPlayerSettings.seekTimeSeconds.toFloat(),
                        onValueChangeFinished = { v ->
                            updateVideoPlayerSettings(
                                videoPlayerSettings.copy(seekTimeSeconds = v.toInt())
                            )
                        },
                        valueRange = 5f..30f,
                        steps = 24,
                    )
                }

                SettingsControl(icon = Icons.Default.PlayCircle, title = stringResource(R.string.playback_speed)) {
                    SliderRow(
                        valueText = String.format("%.2fx", videoPlayerSettings.defaultPlaybackSpeed),
                        value = videoPlayerSettings.defaultPlaybackSpeed,
                        onValueChangeFinished = { v ->
                            updateVideoPlayerSettings(
                                videoPlayerSettings.copy(defaultPlaybackSpeed = v)
                            )
                        },
                        valueRange = 0.25f..3.5f,
                        steps = 12,
                    )
                }

                // Subtitle Settings — shortcut row (full subtitle management
                // lives on its own page: SubtitleSettingsScreen).
                SettingsNavCard(
                    icon = Icons.Default.Subtitles,
                    title = stringResource(R.string.subtitle_settings),
                    subtitle = stringResource(R.string.subtitle_settings_shortcut_subtitle),
                    accentTint = MaterialTheme.colorScheme.primary,
                    onClick = {
                        navController?.navigate(com.pishimovie.navigation.AppScreens.SubtitleSettings.buildRoute(null))
                    },
                )
            }
        }

        // ══════════════════════════════════════════════════════════════════
        // Section: Storage
        // ══════════════════════════════════════════════════════════════════
        item(key = "section_storage") {
            SettingsAccordionSection(title = stringResource(R.string.storage_section)) {
                SettingsNavCard(
                    icon = Icons.Default.Storage,
                    title = stringResource(R.string.cache_management),
                    subtitle = context.getString(R.string.cache_subtitle, cacheSize),
                    accentTint = MaterialTheme.colorScheme.primary,
                    onClick = {
                        com.pishimovie.util.PerformanceOptimizer.clearCaches(context)
                        cacheSize = "0.0 MB"
                        Toast.makeText(context, context.getString(R.string.cache_cleared), Toast.LENGTH_SHORT).show()
                    },
                )

                // Issue 2: Download Manager nav card removed — no Download Manager page.

                SettingsNavCard(
                    icon = Icons.Default.Movie,
                    title = stringResource(R.string.series_episodes_cache),
                    subtitle = context.getString(R.string.series_episodes_size,
                        if (watchedEpisodesCacheSize > 0) "$watchedEpisodesCacheSize B" else context.getString(R.string.empty)
                    ),
                    accentTint = MaterialTheme.colorScheme.primary,
                    onClick = { showClearWatchedEpisodesDialog = true },
                )
            }
        }

        // ══════════════════════════════════════════════════════════════════
        // Section: About
        // ══════════════════════════════════════════════════════════════════
        item(key = "section_about") {
            SettingsAccordionSection(title = stringResource(R.string.about_section)) {
                SettingsNavCard(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.about_pishimovie),
                    subtitle = stringResource(R.string.about_subtitle),
                    accentTint = MaterialTheme.colorScheme.primary,
                    onClick = { navController?.navigate("about") },
                )

                AppUpdateSettingsCard(LocalAppUpdateController.current)

                SettingsNavCard(
                    icon = Icons.Default.Restore,
                    title = stringResource(R.string.reset_to_defaults),
                    subtitle = stringResource(R.string.reset_subtitle),
                    accentTint = MaterialTheme.colorScheme.error,
                    onClick = { showResetDialog = true },
                )
            }
        }

        item(key = "spacer") { Spacer(modifier = Modifier.height(16.dp)) }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.reset_settings)) },
            text = { Text(stringResource(R.string.reset_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    resetToDefaults()
                    showResetDialog = false
                }) { Text(stringResource(R.string.reset)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (showClearWatchedEpisodesDialog) {
        AlertDialog(
            onDismissRequest = { showClearWatchedEpisodesDialog = false },
            title = { Text(stringResource(R.string.clear_watched_episodes)) },
            text = { Text(stringResource(R.string.clear_watched_episodes_message)) },
            confirmButton = {
                TextButton(onClick = {
                    clearWatchedEpisodes()
                    showClearWatchedEpisodesDialog = false
                }) { Text(stringResource(R.string.clear_all)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearWatchedEpisodesDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Material 3 building blocks — flat, non-overlapping
// ───────────────────────────────────────────────────────────────────────────

/**
 * One collapsible settings section in the accordion layout (Farzad's
 * reference screenshot, 2026-11-03): a single large rounded card with a
 * bold section title and a chevron; tapping the header expands/collapses
 * the section's controls with an animated height change. Expansion state
 * is saveable, so it survives rotation and process recreation.
 */
@Composable
private fun SettingsAccordionSection(
    title: String,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "settingsSectionChevron",
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(chevronRotation),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                content()
            }
        }
    }
}

/**
 * A single setting control inside an accordion section. The icon + title
 * form the header row; [content] is rendered below with consistent
 * spacing. Flat (no card shell) — the section card provides the surface.
 */
@Composable
private fun SettingsControl(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

/**
 * Tappable row that navigates / triggers an action. Flat (no card shell)
 * so it sits cleanly inside an accordion section.
 */
@Composable
private fun SettingsNavCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentTint: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accentTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentTint,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Label + value + slider, vertically stacked with consistent spacing.
 */
@Composable
private fun SliderRow(
    valueText: String,
    value: Float,
    onValueChangeFinished: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value) }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_value),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onValueChangeFinished(sliderValue) },
            valueRange = valueRange,
            steps = steps,
        )
    }
}

/**
 * Theme type selector chips: Netflix Dark / Pishi Theme.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeTypeChipRow(
    current: ThemeType,
    onSelect: (ThemeType) -> Unit,
) {
    val options = listOf(
        ThemeType.NETFLIX to stringResource(R.string.theme_netflix_dark),
        ThemeType.PISHI to stringResource(R.string.theme_pishi),
        ThemeType.MIDNIGHT_BLUE to stringResource(R.string.theme_midnight_blue),
        ThemeType.CINEMA_GOLD to stringResource(R.string.theme_cinema_gold),
        ThemeType.OLED_BLACK to stringResource(R.string.theme_oled_black),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (type, label) ->
            FilterChip(
                selected = current == type,
                onClick = { onSelect(type) },
                label = { Text(label) },
            )
        }
    }
}

/**
 * Language selector chips: Persian / English.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageChipRow(
    onSelect: (String) -> Unit,
) {
    val currentLang = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()[0]?.language ?: "fa"
    val options = listOf(
        "fa" to stringResource(R.string.lang_fa_display),
        "en" to stringResource(R.string.lang_en_display),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (code, label) ->
            FilterChip(
                selected = currentLang == code,
                onClick = { onSelect(code) },
                label = { Text(label) },
            )
        }
    }
}

/**
 * Player selector chips: Built-in / VLC / MX Player / KMPlayer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerChipRow(
    current: ExternalPlayer,
    onSelect: (ExternalPlayer) -> Unit,
) {
    val options = listOf(
        ExternalPlayer.NONE to stringResource(R.string.built_in),
        ExternalPlayer.VLC to "VLC",
        ExternalPlayer.MX_PLAYER to "MX Player",
        ExternalPlayer.KM_PLAYER to "KMPlayer",
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (player, label) ->
            FilterChip(
                selected = current == player,
                onClick = { onSelect(player) },
                label = { Text(label) },
            )
        }
    }
}
