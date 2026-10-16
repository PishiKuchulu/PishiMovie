package com.pishimovie.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pishimovie.data.repository.DiscoveryRepository
import com.pishimovie.ui.theme.LocalPishiDimens
import com.pishimovie.ui.theme.ShapeCardDefault

/**
 * Year Explorer (Phase 5) — shows decade tiles. Tapping a decade navigates
 * to [DiscoveryScreen] in "year" mode with that decade's label.
 */
@Composable
fun YearExplorerScreen(
    navController: NavController,
) {
    val dimens = LocalPishiDimens.current
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val repo = remember { DiscoveryRepository() }
    val decades = remember { repo.getDecades() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = dimens.lg,
            end = dimens.lg,
            top = statusBarPadding + dimens.lg,
            bottom = dimens.bottomNavHeight + dimens.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(dimens.sm),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    text = stringResource(R.string.year_explorer),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        items(decades, key = { it.label }) { decade ->
            DecadeTile(
                label = decade.label,
                range = "${decade.from}–${decade.to}",
                onClick = { navController.navigate("discovery/year/${decade.label}") },
            )
        }
    }
}

@Composable
private fun DecadeTile(label: String, range: String, onClick: () -> Unit) {
    val dimens = LocalPishiDimens.current
    val primaryColor = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCardDefault)
            .background(
                Brush.linearGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.15f), MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                )
            )
            .clickable { onClick() }
            .padding(dimens.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.md),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .background(primaryColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label.take(2),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = if (primaryColor.luminance() > 0.5f) Color.Black else Color.White,
            )
        }
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = range,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
