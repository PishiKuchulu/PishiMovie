package com.pishimovie.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pishimovie.R
import com.pishimovie.data.model.FilterType
import com.pishimovie.data.model.Genre

@Composable
fun GenreFilterSection(
    genres: List<Genre>,
    selectedGenreId: Int,
    selectedFilterType: FilterType,
    onGenreSelected: (Int) -> Unit,
    onFilterTypeSelected: (FilterType) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.filters),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        // Filter row with filter type on left and genre selector on right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Filter type selector on the left
            FilterTypeSelector(
                modifier = Modifier.weight(1f),
                selectedFilterType = selectedFilterType,
                onFilterTypeSelected = onFilterTypeSelected
            )
            
            // Genre selector on the right
            GenreSelector(
                modifier = Modifier.weight(1f),
                genres = genres,
                selectedGenreId = selectedGenreId,
                onGenreSelected = onGenreSelected
            )
        }
    }
}

@Composable
fun FilterTypeSelector(
    modifier: Modifier = Modifier,
    selectedFilterType: FilterType,
    onFilterTypeSelected: (FilterType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        onClick = { expanded = true },
        modifier = modifier
            .height(36.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (selectedFilterType) {
                        FilterType.DEFAULT -> stringResource(R.string.sort_default)
                        FilterType.BY_YEAR -> stringResource(R.string.sort_year)
                        FilterType.BY_IMDB -> stringResource(R.string.sort_imdb)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = stringResource(R.string.genre_filter_options_cd),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.height(16.dp)
                )
            }
            
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.default_filter)) },
                    onClick = {
                        onFilterTypeSelected(FilterType.DEFAULT)
                        expanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.by_year)) },
                    onClick = {
                        onFilterTypeSelected(FilterType.BY_YEAR)
                        expanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.by_imdb_alt)) },
                    onClick = {
                        onFilterTypeSelected(FilterType.BY_IMDB)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun GenreSelector(
    modifier: Modifier = Modifier,
    genres: List<Genre>,
    selectedGenreId: Int,
    onGenreSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    // Find the selected genre title
    val selectedGenreTitle = if (selectedGenreId == 0) {
        stringResource(R.string.all_genres)
    } else {
        genres.find { it.id == selectedGenreId }?.title ?: stringResource(R.string.all_genres)
    }
    
    Card(
        onClick = { expanded = true },
        modifier = modifier
            .height(36.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondary
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedGenreTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondary,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = stringResource(R.string.genre_options_cd),
                    tint = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.height(16.dp)
                )
            }
            
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.all_genres)) },
                    onClick = {
                        onGenreSelected(0)
                        expanded = false
                    }
                )
                
                genres.forEach { genre ->
                    DropdownMenuItem(
                        text = { Text(genre.title) },
                        onClick = {
                            onGenreSelected(genre.id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}