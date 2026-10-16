package com.pishimovie.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.pishimovie.ui.components.PishiBrandingFooter
import com.pishimovie.R
import com.pishimovie.data.model.SubtitleAlignment
import com.pishimovie.data.model.SubtitleFont
import com.pishimovie.data.model.SubtitlePerVideo
import com.pishimovie.data.model.SubtitlePosition
import com.pishimovie.data.model.SubtitleProfile
import com.pishimovie.data.model.SubtitleSettings
import com.pishimovie.utils.SubtitleSettingsStorage

/**
 * Professional Subtitle Center — full subtitle management UI.
 *
 * Three sections (Basic / Appearance / Advanced), permanent live preview at
 * the top, profile presets, and optional per-video overrides when launched
 * from the player with a [videoUrl].
 *
 * Every change persists immediately — there is no Apply button. The preview
 * re-renders on every state update via Compose's recomposition.
 *
 * Two entry points:
 *  - Settings → Playback → Subtitle Settings → [NavController] based nav
 *  - Player → Subtitle Settings → [com.pishimovie.SubtitleSettingsActivity]
 *
 * Both flows render this composable; only the close behavior differs.
 */
@Composable
fun SubtitleSettingsScreen(
    navController: NavController? = null,
    videoUrl: String? = null,
    onClose: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val isPerVideo = !videoUrl.isNullOrBlank()

    // ── Persistent state ──────────────────────────────────────────────────
    var globalSettings by remember {
        mutableStateOf(SubtitleSettingsStorage.loadGlobal(context))
    }
    var customProfiles by remember {
        mutableStateOf(SubtitleSettingsStorage.loadCustomProfiles(context))
    }
    var activeProfileId by remember {
        mutableStateOf(SubtitleSettingsStorage.loadActiveProfileId(context))
    }
    var perVideo by remember(videoUrl) {
        mutableStateOf(
            videoUrl?.let { SubtitleSettingsStorage.loadPerVideo(context, it) }
        )
    }

    // ── UI state ───────────────────────────────────────────────────────────
    var advancedExpanded by remember { mutableStateOf(false) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }

    // The "effective" settings used for the live preview: global + per-video overrides
    val previewSettings = remember(globalSettings, perVideo) {
        if (isPerVideo && perVideo != null) {
            globalSettings.copy(
                delayMs = perVideo!!.delayMs ?: globalSettings.delayMs,
                textSize = perVideo!!.textSize ?: globalSettings.textSize,
                language = perVideo!!.language ?: globalSettings.language,
            )
        } else {
            globalSettings
        }
    }

    // ── Mutation helpers ───────────────────────────────────────────────────
    fun updateGlobal(newSettings: SubtitleSettings) {
        globalSettings = newSettings
        SubtitleSettingsStorage.saveGlobal(context, newSettings)
        // Mark as "Custom" profile (user-edited) when not matching any preset
        val matchedProfile = SubtitleProfile.BUILT_IN.firstOrNull { it.settings == newSettings }
            ?: customProfiles.firstOrNull { it.settings == newSettings }
        if (matchedProfile != null) {
            activeProfileId = matchedProfile.id
            SubtitleSettingsStorage.saveActiveProfileId(context, matchedProfile.id)
        } else {
            activeProfileId = null
            SubtitleSettingsStorage.saveActiveProfileId(context, null)
        }
    }

    fun updatePerVideo(patch: SubtitlePerVideo.() -> SubtitlePerVideo) {
        if (!isPerVideo) return
        val current = perVideo ?: SubtitlePerVideo(videoUrl = videoUrl!!)
        val updated = current.patch()
        perVideo = updated
        SubtitleSettingsStorage.savePerVideo(context, updated)
    }

    fun applyProfile(profile: SubtitleProfile) {
        // Apply style-only fields; preserve basic control fields (enable, language, delay)
        val merged = globalSettings.copy(
            fontFamily = profile.settings.fontFamily,
            textSize = profile.settings.textSize,
            fontScale = profile.settings.fontScale,
            textColor = profile.settings.textColor,
            textOpacity = profile.settings.textOpacity,
            bold = profile.settings.bold,
            italic = profile.settings.italic,
            borderColor = profile.settings.borderColor,
            borderThickness = profile.settings.borderThickness,
            shadow = profile.settings.shadow,
            shadowStrength = profile.settings.shadowStrength,
            backgroundEnabled = profile.settings.backgroundEnabled,
            backgroundColor = profile.settings.backgroundColor,
            backgroundOpacity = profile.settings.backgroundOpacity,
            cornerRadius = profile.settings.cornerRadius,
            textAlignment = profile.settings.textAlignment,
            bottomMargin = profile.settings.bottomMargin,
            rtlOptimization = profile.settings.rtlOptimization,
            improveComplexScript = profile.settings.improveComplexScript,
            unicodeOptimization = profile.settings.unicodeOptimization,
        )
        updateGlobal(merged)
        activeProfileId = profile.id
        SubtitleSettingsStorage.saveActiveProfileId(context, profile.id)
    }

    fun saveCustomPreset(name: String) {
        val id = "custom_${System.currentTimeMillis()}"
        val profile = SubtitleProfile(
            id = id,
            name = name,
            settings = globalSettings,
            isBuiltIn = false,
        )
        SubtitleSettingsStorage.upsertCustomProfile(context, profile)
        customProfiles = SubtitleSettingsStorage.loadCustomProfiles(context)
        activeProfileId = id
        SubtitleSettingsStorage.saveActiveProfileId(context, id)
    }

    fun deleteCustomPreset(id: String) {
        SubtitleSettingsStorage.deleteCustomProfile(context, id)
        customProfiles = SubtitleSettingsStorage.loadCustomProfiles(context)
        if (activeProfileId == id) {
            activeProfileId = null
            SubtitleSettingsStorage.saveActiveProfileId(context, null)
        }
    }

    fun close() {
        if (onClose != null) onClose() else navController?.popBackStack()
    }

    // ── Layout ─────────────────────────────────────────────────────────────
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
        // ── Header ─────────────────────────────────────────────────────────
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { close() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    text = stringResource(R.string.subtitle_settings),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    SubtitleSettingsStorage.resetGlobal(context)
                    globalSettings = SubtitleSettingsStorage.loadGlobal(context)
                    activeProfileId = null
                    Toast.makeText(context, context.getString(R.string.reset_to_defaults_toast), Toast.LENGTH_SHORT).show()
                }) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = stringResource(R.string.subtitle_reset_cd),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }

        // ── Per-video banner ──────────────────────────────────────────────
        if (isPerVideo) {
            item(key = "per_video_banner") {
                PerVideoBanner(
                    hasOverrides = perVideo?.let {
                        it.delayMs != null || it.textSize != null || it.language != null ||
                            it.selectedTrackKey != null || it.externalSubtitleUri != null
                    } ?: false,
                    onClearOverrides = {
                        SubtitleSettingsStorage.clearPerVideo(context, videoUrl!!)
                        perVideo = null
                        Toast.makeText(context, context.getString(R.string.per_video_cleared), Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }

        // ── Live preview ──────────────────────────────────────────────────
        item(key = "preview") {
            SubtitlePreviewCard(settings = previewSettings)
        }

        // ── Profiles ──────────────────────────────────────────────────────
        item(key = "profiles") {
            ProfileSection(
                builtIn = SubtitleProfile.BUILT_IN,
                custom = customProfiles,
                activeId = activeProfileId,
                onSelect = ::applyProfile,
                onSaveCurrent = { showSavePresetDialog = true },
                onDeleteCustom = ::deleteCustomPreset,
            )
        }

        // ── Basic ─────────────────────────────────────────────────────────
        item(key = "section_basic") { SettingsSectionLabel(stringResource(R.string.subtitle_settings_basic)) }

        item(key = "basic_card") {
            BasicSection(
                settings = globalSettings,
                perVideo = perVideo,
                isPerVideo = isPerVideo,
                onGlobalChange = ::updateGlobal,
                onPerVideoChange = ::updatePerVideo,
                onDownloadSubtitle = { showDownloadDialog = true },
            )
        }

        // ── Appearance ────────────────────────────────────────────────────
        item(key = "section_appearance") { SettingsSectionLabel(stringResource(R.string.subtitle_settings_appearance)) }

        item(key = "appearance_card") {
            AppearanceSection(
                settings = globalSettings,
                onChange = ::updateGlobal,
            )
        }

        // ── Advanced (expandable) ─────────────────────────────────────────
        item(key = "section_advanced") {
            AdvancedSectionHeader(expanded = advancedExpanded) {
                advancedExpanded = !advancedExpanded
            }
        }
        item(key = "advanced_card") {
            AnimatedVisibility(
                visible = advancedExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                AdvancedSection(
                    settings = globalSettings,
                    onChange = ::updateGlobal,
                )
            }
        }

        item(key = "footer") {
            PishiBrandingFooter()
        }
    }

    // ── Dialogs ────────────────────────────────────────────────────────────
    if (showSavePresetDialog) {
        SavePresetDialog(
            onDismiss = { showSavePresetDialog = false },
            onSave = { name ->
                saveCustomPreset(name)
                showSavePresetDialog = false
                Toast.makeText(context, context.getString(R.string.preset_saved, name), Toast.LENGTH_SHORT).show()
            },
        )
    }
    if (showDownloadDialog) {
        SubtitleDownloadDialog(
            onDismiss = { showDownloadDialog = false },
            onChoose = { lang ->
                showDownloadDialog = false
                Toast.makeText(context, context.getString(R.string.searching_subtitles, lang), Toast.LENGTH_SHORT).show()
            },
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════
// Live Preview
// ══════════════════════════════════════════════════════════════════════════

/**
 * Renders the live subtitle preview against a faux-video dark gradient.
 *
 * Two sample lines are stacked: one English, one Persian — so RTL/Unicode
 * rendering issues are visible immediately. Every appearance property
 * drives the rendered text: color, opacity, bold, italic, shadow, outline
 * (simulated via shadow blur), background, corner radius, alignment,
 * font family, and effective scale.
 */
@Composable
private fun SubtitlePreviewCard(settings: SubtitleSettings) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Subtitles,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.subtitle_settings_preview),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            SubtitlePreviewCanvas(settings = settings)
        }
    }
}

@Composable
private fun SubtitlePreviewCanvas(settings: SubtitleSettings) {
    val textColor = Color(settings.textColor).copy(alpha = settings.textOpacity)
    val borderColor = Color(settings.borderColor)
    val bgColor = if (settings.backgroundEnabled) {
        Color(settings.backgroundColor).copy(alpha = settings.backgroundOpacity)
    } else {
        Color.Transparent
    }

    // Simulated subtitle text — one English, one Persian (RTL).
    val englishLine = stringResource(R.string.subtitle_preview_line)
    val persianLine = "این یک نمونه زیرنویس فارسی است"

    // Effective text size in sp (combined scale)
    val effectiveSp = (settings.textSize * settings.effectiveScale).coerceIn(8f, 96f)

    // Alignment mapping
    val align = when (settings.textAlignment) {
        SubtitleAlignment.START -> TextAlign.Start
        SubtitleAlignment.CENTER -> TextAlign.Center
        SubtitleAlignment.END -> TextAlign.End
    }

    // Build composite shadow: outline + drop shadow
    val shadowList = buildList {
        if (settings.borderThickness > 0f) {
            // Outline simulation via small-radius shadow
            add(Shadow(color = borderColor, offset = androidx.compose.ui.geometry.Offset(0f, 0f), blurRadius = settings.borderThickness * 2.5f))
        }
        if (settings.shadow) {
            // Drop shadow
            add(Shadow(color = Color.Black.copy(alpha = 0.85f), offset = androidx.compose.ui.geometry.Offset(1.5f, 1.5f), blurRadius = settings.shadowStrength * 4f))
        }
    }
    val primaryShadow = shadowList.firstOrNull()

    // Font weight + style
    val fontWeight = if (settings.bold) FontWeight.Bold else FontWeight.Normal
    val fontStyle = if (settings.italic) FontStyle.Italic else FontStyle.Normal

    // Vertical alignment inside the preview area based on `position`
    val verticalArrangement = when (settings.position) {
        SubtitlePosition.TOP -> Arrangement.Top
        SubtitlePosition.CENTER -> Arrangement.Center
        SubtitlePosition.BOTTOM -> Arrangement.Bottom
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A24),
                        Color(0xFF050508),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = if (settings.position == SubtitlePosition.TOP) 16.dp else 0.dp,
                    bottom = if (settings.position == SubtitlePosition.BOTTOM) (settings.bottomMargin * 320).dp.coerceAtMost(80.dp) else 0.dp,
                ),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = when (settings.textAlignment) {
                SubtitleAlignment.START -> Alignment.Start
                SubtitleAlignment.CENTER -> Alignment.CenterHorizontally
                SubtitleAlignment.END -> Alignment.End
            },
        ) {
            // English line
            PreviewSubtitleLine(
                text = englishLine,
                fontSize = effectiveSp,
                color = textColor,
                fontWeight = fontWeight,
                fontStyle = fontStyle,
                align = align,
                shadow = primaryShadow,
                bgColor = bgColor,
                cornerRadius = settings.cornerRadius,
                lineSpacing = settings.lineSpacing,
            )
            Spacer(modifier = Modifier.height((4 * settings.lineSpacing).dp))
            // Persian line (RTL)
            PreviewSubtitleLine(
                text = persianLine,
                fontSize = effectiveSp,
                color = textColor,
                fontWeight = fontWeight,
                fontStyle = fontStyle,
                align = align,
                shadow = primaryShadow,
                bgColor = bgColor,
                cornerRadius = settings.cornerRadius,
                lineSpacing = settings.lineSpacing,
            )
        }
    }
}

@Composable
private fun PreviewSubtitleLine(
    text: String,
    fontSize: Float,
    color: Color,
    fontWeight: FontWeight,
    fontStyle: FontStyle,
    align: TextAlign,
    shadow: Shadow?,
    bgColor: Color,
    cornerRadius: Float,
    lineSpacing: Float,
) {
    val baseModifier = if (bgColor != Color.Transparent) {
        Modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    } else {
        Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    }
    Text(
        text = text,
        modifier = baseModifier,
        style = TextStyle(
            color = color,
            fontSize = fontSize.sp,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            textAlign = align,
            lineHeight = (fontSize * lineSpacing * 1.2f).sp,
            shadow = shadow,
        ),
    )
}

// ══════════════════════════════════════════════════════════════════════════
// Per-Video Banner
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun PerVideoBanner(
    hasOverrides: Boolean,
    onClearOverrides: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.per_video_mode),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (hasOverrides) stringResource(R.string.per_video_active_delay)
                    else stringResource(R.string.per_video_active_no_overrides),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (hasOverrides) {
                TextButton(onClick = onClearOverrides) { Text(stringResource(R.string.reset)) }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// Profile Section
// ══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileSection(
    builtIn: List<SubtitleProfile>,
    custom: List<SubtitleProfile>,
    activeId: String?,
    onSelect: (SubtitleProfile) -> Unit,
    onSaveCurrent: () -> Unit,
    onDeleteCustom: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.subtitle_settings_profiles),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onSaveCurrent) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.save_current))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                builtIn.forEach { profile ->
                    ProfileChip(
                        profile = profile,
                        active = profile.id == activeId,
                        onSelect = { onSelect(profile) },
                        onDelete = null,
                    )
                }
                custom.forEach { profile ->
                    ProfileChip(
                        profile = profile,
                        active = profile.id == activeId,
                        onSelect = { onSelect(profile) },
                        onDelete = { onDeleteCustom(profile.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileChip(
    profile: SubtitleProfile,
    active: Boolean,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (active) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                }
            )
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(14.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (active) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Column {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                )
                profile.description?.let { desc ->
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (onDelete != null) {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onDelete, modifier = Modifier.size(20.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.subtitle_delete_preset_cd),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// Basic Section
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun BasicSection(
    settings: SubtitleSettings,
    perVideo: SubtitlePerVideo?,
    isPerVideo: Boolean,
    onGlobalChange: (SubtitleSettings) -> Unit,
    onPerVideoChange: (SubtitlePerVideo.() -> SubtitlePerVideo) -> Unit,
    onDownloadSubtitle: () -> Unit,
) {
    val context = LocalContext.current

    // File picker for local subtitle
    val localSubtitleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            try {
                // Persist permission so we can read it later
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            if (isPerVideo) {
                onPerVideoChange {
                    copy(
                        externalSubtitleUri = uri.toString(),
                        externalSubtitleMime = guessSubtitleMime(uri.toString()),
                    )
                }
                Toast.makeText(context, context.getString(R.string.local_subtitle_attached_toast), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, context.getString(R.string.local_subtitle_apply_per_video), Toast.LENGTH_SHORT).show()
            }
        }
    }

    SectionCard(
        icon = Icons.Default.TextFields,
        title = stringResource(R.string.subtitle_settings_basic),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Subtitle Enable/Disable
        SwitchRow(
            label = stringResource(R.string.subtitles_enabled),
            description = stringResource(R.string.subtitles_enabled_desc),
            checked = settings.enabled,
            onCheckedChange = { onGlobalChange(settings.copy(enabled = it)) },
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Subtitle Language
        DropdownRow(
            label = stringResource(R.string.subtitle_language),
            icon = Icons.Default.Language,
            current = settings.language,
            options = SUBTITLE_LANGUAGES,
            onSelected = { code ->
                if (isPerVideo) {
                    onPerVideoChange { copy(language = code) }
                } else {
                    onGlobalChange(settings.copy(language = code))
                }
            },
            valueLabel = { code -> 
                val resId = SUBTITLE_LANGUAGES.firstOrNull { it.first == code }?.second
                if (resId != null) context.getString(resId) else code 
            },
        )
        if (isPerVideo && perVideo?.language != null) {
            PerVideoResetHint(onReset = { onPerVideoChange { copy(language = null) } })
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Download Subtitle
        ActionRow(
            label = stringResource(R.string.subtitle_download),
            icon = Icons.Default.Download,
            description = stringResource(R.string.subtitle_download_desc),
            onClick = onDownloadSubtitle,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Load Local Subtitle
        ActionRow(
            label = stringResource(R.string.load_local_subtitle),
            icon = Icons.Default.FolderOpen,
            description = stringResource(R.string.load_local_subtitle_desc),
            onClick = {
                localSubtitleLauncher.launch(
                    arrayOf(
                        "application/octet-stream",
                        "text/plain",
                        "application/x-subrip",
                        "text/vtt",
                        "text/ssa",
                        "*/*",
                    )
                )
            },
        )
        if (isPerVideo && perVideo?.externalSubtitleUri != null) {
            PerVideoResetHint(
                label = stringResource(R.string.local_subtitle_attached),
                onReset = { onPerVideoChange { copy(externalSubtitleUri = null, externalSubtitleMime = null) } },
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Character Encoding
        DropdownRow(
            label = stringResource(R.string.character_encoding),
            icon = Icons.Default.Tune,
            current = settings.characterEncoding,
            options = SUBTITLE_ENCODINGS,
            onSelected = { enc -> onGlobalChange(settings.copy(characterEncoding = enc)) },
            valueLabel = { code ->
                val resId = SUBTITLE_ENCODINGS.firstOrNull { it.first == code }?.second
                if (resId != null) context.getString(resId) else code
            },
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Font Size
        SliderRow(
            label = stringResource(R.string.font_size),
            valueText = "${(if (isPerVideo) (perVideo?.textSize ?: settings.textSize) else settings.textSize).toInt()}sp",
            value = (if (isPerVideo) (perVideo?.textSize ?: settings.textSize) else settings.textSize),
            valueRange = 10f..50f,
            onValueChangeFinished = { v ->
                if (isPerVideo) {
                    onPerVideoChange { copy(textSize = v) }
                } else {
                    onGlobalChange(settings.copy(textSize = v))
                }
            },
        )
        if (isPerVideo && perVideo?.textSize != null) {
            PerVideoResetHint(onReset = { onPerVideoChange { copy(textSize = null) } })
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Subtitle Delay
        SliderRow(
            label = stringResource(R.string.subtitle_delay),
            valueText = "${(if (isPerVideo) (perVideo?.delayMs ?: settings.delayMs) else settings.delayMs)}ms",
            value = (if (isPerVideo) (perVideo?.delayMs ?: settings.delayMs) else settings.delayMs).toFloat(),
            valueRange = -10000f..10000f,
            steps = 40,
            onValueChangeFinished = { v ->
                if (isPerVideo) {
                    onPerVideoChange { copy(delayMs = v.toLong()) }
                } else {
                    onGlobalChange(settings.copy(delayMs = v.toLong()))
                }
            },
        )
        if (isPerVideo && perVideo?.delayMs != null) {
            PerVideoResetHint(onReset = { onPerVideoChange { copy(delayMs = null) } })
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Subtitle Position
        DropdownRow(
            label = stringResource(R.string.subtitle_position),
            icon = Icons.Default.Subtitles,
            current = settings.position.name,
            options = listOf(
                SubtitlePosition.TOP.name to R.string.position_top,
                SubtitlePosition.CENTER.name to R.string.position_center,
                SubtitlePosition.BOTTOM.name to R.string.position_bottom,
            ),
            onSelected = { name ->
                val pos = SubtitlePosition.valueOf(name)
                onGlobalChange(settings.copy(position = pos))
            },
            valueLabel = { name ->
                when (name) {
                    SubtitlePosition.TOP.name -> context.getString(R.string.position_top)
                    SubtitlePosition.CENTER.name -> context.getString(R.string.position_center)
                    SubtitlePosition.BOTTOM.name -> context.getString(R.string.position_bottom)
                    else -> name
                }
            },
        )
    }
}

private fun guessSubtitleMime(uri: String): String {
    val ext = uri.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return when (ext) {
        "srt" -> "application/x-subrip"
        "vtt" -> "text/vtt"
        "ass", "ssa" -> "text/ssa"
        "sub" -> "text/plain"
        else -> "application/octet-stream"
    }
}

// ══════════════════════════════════════════════════════════════════════════
// Appearance Section
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun AppearanceSection(
    settings: SubtitleSettings,
    onChange: (SubtitleSettings) -> Unit,
) {
    val context = LocalContext.current
    SectionCard(
        icon = Icons.Default.Palette,
        title = stringResource(R.string.subtitle_settings_appearance),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Font Family
        DropdownRow(
            label = stringResource(R.string.font_family),
            icon = Icons.Default.TextFields,
            current = settings.fontFamily.name,
            options = SubtitleFont.values().map { it.name to fontLabel(it) },
            onSelected = { name ->
                onChange(settings.copy(fontFamily = SubtitleFont.valueOf(name)))
            },
            valueLabel = { name -> context.getString(fontLabel(SubtitleFont.valueOf(name))) },
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Font Size
        SliderRow(
            label = stringResource(R.string.font_size),
            valueText = "${settings.textSize.toInt()}sp",
            value = settings.textSize,
            valueRange = 10f..50f,
            onValueChangeFinished = { onChange(settings.copy(textSize = it)) },
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Font Scale
        SliderRow(
            label = stringResource(R.string.font_scale),
            valueText = String.format("%.2fx", settings.fontScale),
            value = settings.fontScale,
            valueRange = 0.5f..3f,
            onValueChangeFinished = { onChange(settings.copy(fontScale = it)) },
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Font Color
        ColorSwatchRow(
            label = stringResource(R.string.font_color),
            currentColor = Color(settings.textColor),
            onColorSelected = { color -> onChange(settings.copy(textColor = color.toArgb())) },
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Bold + Italic row
        Row(modifier = Modifier.fillMaxWidth()) {
            SwitchRow(
                label = stringResource(R.string.bold),
                checked = settings.bold,
                onCheckedChange = { onChange(settings.copy(bold = it)) },
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            SwitchRow(
                label = stringResource(R.string.italic),
                checked = settings.italic,
                onCheckedChange = { onChange(settings.copy(italic = it)) },
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Border Color
        ColorSwatchRow(
            label = stringResource(R.string.border_color),
            currentColor = Color(settings.borderColor),
            onColorSelected = { color -> onChange(settings.copy(borderColor = color.toArgb())) },
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Border Thickness
        SliderRow(
            label = stringResource(R.string.border_thickness),
            valueText = String.format("%.1fdp", settings.borderThickness),
            value = settings.borderThickness,
            valueRange = 0f..10f,
            onValueChangeFinished = { onChange(settings.copy(borderThickness = it)) },
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Shadow + Strength
        SwitchRow(
            label = stringResource(R.string.drop_shadow),
            description = stringResource(R.string.drop_shadow_desc),
            checked = settings.shadow,
            onCheckedChange = { onChange(settings.copy(shadow = it)) },
        )
        if (settings.shadow) {
            Spacer(modifier = Modifier.height(8.dp))
            SliderRow(
                label = stringResource(R.string.shadow_strength),
                valueText = String.format("%.2f", settings.shadowStrength),
                value = settings.shadowStrength,
                valueRange = 0f..2f,
                onValueChangeFinished = { onChange(settings.copy(shadowStrength = it)) },
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Background Enable
        SwitchRow(
            label = stringResource(R.string.background),
            description = stringResource(R.string.background_desc),
            checked = settings.backgroundEnabled,
            onCheckedChange = { onChange(settings.copy(backgroundEnabled = it)) },
        )
        if (settings.backgroundEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            ColorSwatchRow(
                label = stringResource(R.string.background_color),
                currentColor = Color(settings.backgroundColor),
                onColorSelected = { color -> onChange(settings.copy(backgroundColor = color.toArgb())) },
            )
            Spacer(modifier = Modifier.height(8.dp))
            SliderRow(
                label = stringResource(R.string.background_opacity),
                valueText = "${(settings.backgroundOpacity * 100).toInt()}%",
                value = settings.backgroundOpacity,
                valueRange = 0f..1f,
                onValueChangeFinished = { onChange(settings.copy(backgroundOpacity = it)) },
            )
            Spacer(modifier = Modifier.height(8.dp))
            SliderRow(
                label = stringResource(R.string.corner_radius),
                valueText = "${settings.cornerRadius.toInt()}dp",
                value = settings.cornerRadius,
                valueRange = 0f..32f,
                onValueChangeFinished = { onChange(settings.copy(cornerRadius = it)) },
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Text Alignment
        SegmentedRow(
            label = stringResource(R.string.text_alignment),
            options = listOf(
                SubtitleAlignment.START to stringResource(R.string.align_start) to Icons.Default.FormatAlignLeft,
                SubtitleAlignment.CENTER to stringResource(R.string.align_center) to Icons.Default.FormatAlignCenter,
                SubtitleAlignment.END to stringResource(R.string.align_end) to Icons.Default.FormatAlignRight,
            ),
            current = settings.textAlignment,
            onSelected = { onChange(settings.copy(textAlignment = it)) },
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Bottom Margin
        SliderRow(
            label = stringResource(R.string.bottom_margin),
            valueText = "${(settings.bottomMargin * 100).toInt()}%",
            value = settings.bottomMargin,
            valueRange = 0f..0.5f,
            onValueChangeFinished = { onChange(settings.copy(bottomMargin = it)) },
        )
    }
}

private fun fontLabel(font: SubtitleFont): Int = when (font) {
    SubtitleFont.DEFAULT -> R.string.font_default
    SubtitleFont.SANS -> R.string.font_sans
    SubtitleFont.SERIF -> R.string.font_serif
    SubtitleFont.MONOSPACE -> R.string.font_monospace
    SubtitleFont.VAZIRMATN -> R.string.font_vazirmatn
    SubtitleFont.COMPACT -> R.string.font_compact
}

// ══════════════════════════════════════════════════════════════════════════
// Advanced Section
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun AdvancedSectionHeader(expanded: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.subtitle_settings_advanced),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AdvancedSection(
    settings: SubtitleSettings,
    onChange: (SubtitleSettings) -> Unit,
) {
    SectionCard(
        icon = Icons.Default.Tune,
        title = stringResource(R.string.advanced_rendering),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        SwitchRow(
            label = stringResource(R.string.ssa_ass_rendering),
            description = stringResource(R.string.ssa_ass_rendering_desc),
            checked = settings.ssaAssRendering,
            onCheckedChange = { onChange(settings.copy(ssaAssRendering = it)) },
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SwitchRow(
            label = stringResource(R.string.improve_stroke),
            description = stringResource(R.string.improve_stroke_desc),
            checked = settings.improveStroke,
            onCheckedChange = { onChange(settings.copy(improveStroke = it)) },
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SwitchRow(
            label = stringResource(R.string.improve_complex_script),
            description = stringResource(R.string.improve_complex_script_desc),
            checked = settings.improveComplexScript,
            onCheckedChange = { onChange(settings.copy(improveComplexScript = it)) },
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SwitchRow(
            label = stringResource(R.string.ignore_embedded_font),
            description = stringResource(R.string.ignore_embedded_font_desc),
            checked = settings.ignoreEmbeddedFont,
            onCheckedChange = { onChange(settings.copy(ignoreEmbeddedFont = it)) },
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SwitchRow(
            label = stringResource(R.string.fit_subtitle_into_video),
            description = stringResource(R.string.fit_subtitle_into_video_desc),
            checked = settings.fitSubtitleIntoVideo,
            onCheckedChange = { onChange(settings.copy(fitSubtitleIntoVideo = it)) },
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SwitchRow(
            label = stringResource(R.string.fade_animation),
            description = stringResource(R.string.fade_animation_desc),
            checked = settings.fadeAnimation,
            onCheckedChange = { onChange(settings.copy(fadeAnimation = it)) },
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SliderRow(
            label = stringResource(R.string.subtitle_scale),
            valueText = String.format("%.2fx", settings.subtitleScale),
            value = settings.subtitleScale,
            valueRange = 0.5f..3f,
            onValueChangeFinished = { onChange(settings.copy(subtitleScale = it)) },
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SliderRow(
            label = stringResource(R.string.line_spacing),
            valueText = String.format("%.2f", settings.lineSpacing),
            value = settings.lineSpacing,
            valueRange = 0.8f..2f,
            onValueChangeFinished = { onChange(settings.copy(lineSpacing = it)) },
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SwitchRow(
            label = stringResource(R.string.rtl_optimization),
            description = stringResource(R.string.rtl_optimization_desc),
            checked = settings.rtlOptimization,
            onCheckedChange = { onChange(settings.copy(rtlOptimization = it)) },
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SwitchRow(
            label = stringResource(R.string.unicode_optimization),
            description = stringResource(R.string.unicode_optimization_desc),
            checked = settings.unicodeOptimization,
            onCheckedChange = { onChange(settings.copy(unicodeOptimization = it)) },
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════
// Reusable building blocks
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
        letterSpacing = 1.5.sp,
    )
}

@Composable
private fun SectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
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
            content()
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChangeFinished: (Float) -> Unit,
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value) }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
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

@Composable
private fun ColorSwatchRow(
    label: String,
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SUBTITLE_COLOR_SWATCHES.forEach { color ->
                val selected = currentColor == color
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color)
                        .then(
                            if (selected) {
                                Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            } else {
                                Modifier
                            }
                        )
                        .clickable { onColorSelected(color) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.subtitle_selected_cd),
                            tint = if (color == Color.White || color == Color.Yellow)
                                Color.Black else Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    current: String,
    options: List<Pair<String, Int>>,
    onSelected: (String) -> Unit,
    valueLabel: (String) -> String,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = valueLabel(current),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = stringResource(R.string.open_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (value, resId) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(resId)) },
                        onClick = {
                            onSelected(value)
                            expanded = false
                        },
                        trailingIcon = {
                            if (value == current) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PerVideoResetHint(
    label: String? = null,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(top = 4.dp, start = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Sync,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label ?: stringResource(R.string.per_video_override_active),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onReset, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
            Text(stringResource(R.string.use_global), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SegmentedRow(
    label: String,
    options: List<Pair<Pair<SubtitleAlignment, String>, androidx.compose.ui.graphics.vector.ImageVector>>,
    current: SubtitleAlignment,
    onSelected: (SubtitleAlignment) -> Unit,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                val value = option.first.first
                val lbl = option.first.second
                val icon = option.second
                SegmentedButton(
                    selected = current == value,
                    onClick = { onSelected(value) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    icon = { Icon(icon, contentDescription = lbl, modifier = Modifier.size(18.dp)) },
                    label = { Text(lbl) },
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// Dialogs
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun SavePresetDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.save_current_as_preset)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.preset_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onSave(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun SubtitleDownloadDialog(
    onDismiss: () -> Unit,
    onChoose: (String) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.subtitle_download)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.subtitle_download_auto_search_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(stringResource(R.string.manual_search)) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.preferred_language), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SUBTITLE_LANGUAGES.take(4).forEach { (code, resId) ->
                        AssistChip(
                            onClick = { onChoose(code) },
                            label = { Text(stringResource(resId), style = MaterialTheme.typography.labelSmall) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onChoose("auto") },
                enabled = searchQuery.isNotBlank(),
            ) { Text(stringResource(R.string.auto_search)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// ══════════════════════════════════════════════════════════════════════════
// Constants
// ══════════════════════════════════════════════════════════════════════════

private val SUBTITLE_LANGUAGES = listOf(
    "auto" to R.string.lang_auto,
    "en" to R.string.lang_en,
    "fa" to R.string.lang_fa,
    "ar" to R.string.lang_ar,
    "tr" to R.string.lang_tr,
    "es" to R.string.lang_es,
    "ja" to R.string.lang_ja,
    "ko" to R.string.lang_ko,
)

private val SUBTITLE_ENCODINGS = listOf(
    "UTF-8" to R.string.encoding_utf8,
    "UTF-16" to R.string.encoding_utf16,
    "Windows-1256" to R.string.encoding_win1256,
    "Windows-1252" to R.string.encoding_win1252,
    "ISO-8859-1" to R.string.encoding_iso8859,
    "GBK" to R.string.encoding_gbk,
)

private val SUBTITLE_COLOR_SWATCHES = listOf(
    Color.White,
    Color.Black,
    Color.Yellow,
    Color.Red,
    Color.Blue,
    Color.Green,
    Color(0xFFFFC53D),
    Color(0xFF2DD4BF),
    Color(0xFFA78BFA),
)
