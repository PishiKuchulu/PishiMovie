package com.pishimovie.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.pishimovie.R
import com.pishimovie.ui.theme.LocalThemeSettings
import com.pishimovie.ui.theme.ThemeType

/**
 * Phase 1: shared M3 top app bar that replaces the previous bottom navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PishiTopAppBar(
    navController: NavController,
    currentRoute: String?,
) {
    var showOverflow by remember { mutableStateOf(false) }
    val themeSettings = LocalThemeSettings.current
    val isPishiTheme = themeSettings.themeType == ThemeType.PISHI

    fun navigateTo(route: String) {
        if (currentRoute == route) return
        navController.navigate(route) {
            launchSingleTop = true
            restoreState = true
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isPishiTheme) {
                    Modifier.background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        )
                    )
                } else Modifier
            )
    ) {
        TopAppBar(
            modifier = Modifier.fillMaxWidth(),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = if (isPishiTheme) Color.Transparent else MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                titleContentColor = if (isPishiTheme) Color.Black else MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = if (isPishiTheme) Color.Black else MaterialTheme.colorScheme.onSurface,
            ),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.app_logo),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = if (isPishiTheme) Color.Black else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            },
            actions = {
                // Primary destinations
                IconButton(onClick = { navigateTo(AppScreens.Home.route) }) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = stringResource(R.string.home),
                        tint = if (currentRoute == AppScreens.Home.route)
                            if (isPishiTheme) Color.White else MaterialTheme.colorScheme.primary
                        else if (isPishiTheme) Color.Black.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { navigateTo(AppScreens.Search.route) }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.search),
                        tint = if (currentRoute == AppScreens.Search.route)
                            if (isPishiTheme) Color.White else MaterialTheme.colorScheme.primary
                        else if (isPishiTheme) Color.Black.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { navigateTo(AppScreens.DiscoveryHub.route) }) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = stringResource(R.string.discover),
                        tint = if (currentRoute == AppScreens.DiscoveryHub.route)
                            if (isPishiTheme) Color.White else MaterialTheme.colorScheme.primary
                        else if (isPishiTheme) Color.Black.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { navigateTo(AppScreens.Favorites.route) }) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = stringResource(R.string.favorites),
                        tint = if (currentRoute == AppScreens.Favorites.route)
                            if (isPishiTheme) Color.White else MaterialTheme.colorScheme.primary
                        else if (isPishiTheme) Color.Black.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Overflow
                Box {
                    IconButton(onClick = { showOverflow = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.more),
                            tint = if (isPishiTheme) Color.Black.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded = showOverflow,
                        onDismissRequest = { showOverflow = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.profile)) },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            onClick = {
                                showOverflow = false
                                navigateTo(AppScreens.Profile.route)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.watch_history)) },
                            leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                            onClick = {
                                showOverflow = false
                                navigateTo(AppScreens.WatchHistory.route)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings)) },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            onClick = {
                                showOverflow = false
                                navigateTo(AppScreens.Settings.route)
                            },
                        )
                    }
                }
            },
        )
    }
}
