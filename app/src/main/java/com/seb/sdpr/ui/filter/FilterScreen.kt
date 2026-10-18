package com.seb.sdpr.ui.filter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.seb.sdpr.domain.model.LibrarySyncProgress
import com.seb.sdpr.domain.model.LibrarySyncStage
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seb.sdpr.R
import com.seb.sdpr.ui.theme.ErrorRed
import com.seb.sdpr.ui.theme.PlexBackground
import com.seb.sdpr.ui.theme.PlexBorderLight
import com.seb.sdpr.ui.theme.PlexOrange
import com.seb.sdpr.ui.theme.PlexOrangeDim
import com.seb.sdpr.ui.theme.PlexSurface
import com.seb.sdpr.ui.theme.PlexSurface2
import com.seb.sdpr.ui.theme.PlexTextHint
import com.seb.sdpr.ui.theme.PlexTextMuted
import com.seb.sdpr.ui.theme.PlexTextPrimary
import com.seb.sdpr.util.toDisplayLanguage
import java.util.Locale
import kotlin.math.roundToInt

@Composable
private fun SyncStatus(progress: LibrarySyncProgress) {
    val label = when (progress.stage) {
        LibrarySyncStage.CONNECTING -> "Connexion…"
        LibrarySyncStage.MOVIES -> "Films"
        LibrarySyncStage.COLLECTIONS -> "Collections"
        LibrarySyncStage.SAVING -> "Enregistrement…"
        LibrarySyncStage.CATALOG -> "Catalogue"
        LibrarySyncStage.FINISHING -> "Finalisation…"
    }
    Column(
        modifier = Modifier.width(108.dp).padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = if (progress.sectionCount > 1) "$label · ${progress.sectionIndex}/${progress.sectionCount}" else label,
            color = PlexTextMuted,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        progress.total?.let { total ->
            Text(
                text = "${progress.completed} / $total",
                color = PlexOrange,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
        progress.fraction?.let { fraction ->
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = PlexOrange,
                trackColor = PlexOrangeDim
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FilterScreen(
    viewModel: FilterViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit,
    onNavigateToResult: (String, FilterState) -> Unit,
    onNavigateToPlaylist: (FilterState) -> Unit,
    onOpenMovie: (String) -> Unit
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()

    if (ui.noResults) {
        AlertDialog(
            onDismissRequest = viewModel::dismissNoResults,
            title = { Text("Aucun resultat") },
            text = { Text(ui.noResultsMessage ?: "Aucun film ne correspond aux filtres actuels.") },
            confirmButton = {
                TextButton(onClick = viewModel::dismissNoResults) {
                    Text("OK", color = PlexOrange)
                }
            },
            containerColor = PlexSurface,
            titleContentColor = PlexTextPrimary,
            textContentColor = PlexTextMuted
        )
    }

    Scaffold(
        containerColor = PlexBackground,
        topBar = {
            val aiEnabled = ui.isConfigured && ui.filterState != FilterState()
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_name),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PlexOrange,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    if (ui.isSyncing) {
                        SyncStatus(ui.syncProgress ?: LibrarySyncProgress())
                        Box(
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = PlexOrange,
                                strokeWidth = 2.dp
                            )
                        }
                    } else {
                        IconButton(onClick = { viewModel.sync() }, enabled = ui.isConfigured) {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = "Synchroniser",
                                tint = if (ui.isConfigured) PlexOrange else PlexTextHint
                            )
                        }
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Parametres", tint = PlexTextHint)
                    }
                    AiActionChip(
                        enabled = aiEnabled,
                        onClick = viewModel::openAiDialog
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PlexBackground)
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PlexSurface)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = viewModel::reset,
                    modifier = Modifier
                        .width(56.dp)
                        .height(56.dp),
                    border = BorderStroke(0.5.dp, PlexBorderLight),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Reinitialiser",
                        tint = PlexOrange,
                        modifier = Modifier.size(38.dp)
                    )
                }
                Button(
                    onClick = { viewModel.generateMovie(onNavigateToResult) },
                    enabled = !ui.isGeneratingMovie && !ui.isGeneratingPlaylist,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PlexOrange.copy(alpha = 0.86f),
                        contentColor = Color(0xFF111111)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (ui.isGeneratingMovie) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color(0xFF111111),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Casino, contentDescription = "Generer un film", modifier = Modifier.size(34.dp))
                    }
                }
                Button(
                    onClick = { viewModel.generatePlaylist(onNavigateToPlaylist) },
                    enabled = !ui.isGeneratingMovie && !ui.isGeneratingPlaylist,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PlexOrange.copy(alpha = 0.86f),
                        contentColor = Color(0xFF111111)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (ui.isGeneratingPlaylist) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color(0xFF111111),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = "Generer une playlist", modifier = Modifier.size(34.dp))
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (ui.syncError != null || !ui.isConfigured) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PlexSurface, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!ui.isConfigured) {
                        Text("Serveur Plex non configure", fontSize = 12.sp, color = ErrorRed)
                        TextButton(onClick = onNavigateToSettings, contentPadding = PaddingValues(0.dp)) {
                            Text("Configurer", color = PlexOrange, fontSize = 12.sp)
                        }
                    }
                    ui.syncError?.let {
                        Text(it, fontSize = 11.sp, color = ErrorRed)
                    }
                }
            }

            FilterSection(label = "Recherche manuelle") {
                OutlinedTextField(
                    value = ui.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Tape au moins 2 lettres", color = PlexTextHint) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = PlexTextHint)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = PlexBorderLight,
                        focusedBorderColor = PlexOrange,
                        unfocusedTextColor = PlexTextPrimary,
                        focusedTextColor = PlexTextPrimary,
                        unfocusedContainerColor = PlexSurface2,
                        focusedContainerColor = PlexSurface2,
                        cursorColor = PlexOrange
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                if (ui.searchQuery.trim().length >= 2) {
                    if (ui.searchResults.isEmpty()) {
                        Text("Aucun film trouve", fontSize = 12.sp, color = PlexTextMuted)
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(PlexSurface2, RoundedCornerShape(10.dp))
                                .border(0.5.dp, PlexBorderLight, RoundedCornerShape(10.dp))
                        ) {
                            ui.searchResults.forEachIndexed { index, movie ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.clearSearch()
                                            onOpenMovie(movie.ratingKey)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        movie.title,
                                        color = PlexTextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    movie.year?.let {
                                        Text("$it", color = PlexTextMuted, fontSize = 12.sp)
                                    }
                                }
                                if (index < ui.searchResults.lastIndex) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp)
                                            .height(1.dp)
                                            .background(PlexBorderLight.copy(alpha = 0.6f))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            FilterSection(label = "Genre") {
                PlexDropdown(ui.filterState.selectedGenre, ui.availableGenres, "Tous les genres", onSelect = viewModel::setGenre)
            }

            FilterSection(label = "Collections") {
                PlexDropdown(ui.filterState.selectedCollection, ui.availableCollections, "Toutes les collections", onSelect = viewModel::setCollection)
            }

            FilterSection(label = "Classification") {
                MultiSelectChipRow(
                    options = ui.availableRatings,
                    selected = ui.filterState.selectedContentRatings,
                    onToggle = viewModel::toggleRating
                )
            }

            if (ui.availableDecades.isNotEmpty()) {
                FilterSection(label = "Decennie") {
                    SelectChipRow(ui.availableDecades, ui.filterState.selectedDecade, onSelect = viewModel::setDecade)
                }
            }

            FilterSection(label = "Score Rotten Tomatoes") {
                val lo = ui.filterState.rtScoreMin.toFloat()
                val hi = ui.filterState.rtScoreMax.toFloat()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${lo.toInt()}%", fontSize = 12.sp, color = PlexOrange, fontWeight = FontWeight.Medium)
                    Text("${hi.toInt()}%", fontSize = 12.sp, color = PlexOrange, fontWeight = FontWeight.Medium)
                }
                RangeSlider(
                    value = lo..hi,
                    onValueChange = { range ->
                        val newLo = (range.start / 5f).roundToInt() * 5
                        val newHi = (range.endInclusive / 5f).roundToInt() * 5
                        viewModel.setRTScore(newLo, newHi.coerceAtLeast(newLo))
                    },
                    valueRange = 0f..100f,
                    steps = 19,
                    colors = SliderDefaults.colors(
                        thumbColor = PlexOrange,
                        activeTrackColor = PlexOrange,
                        inactiveTrackColor = PlexBorderLight
                    )
                )
            }

            FilterSection(label = "Langue audio") {
                PlexDropdown(
                    selected = ui.filterState.selectedLanguage,
                    options = ui.availableLanguages,
                    placeholder = "Toutes les langues",
                    displayTransform = { it.toDisplayLanguage() },
                    onSelect = viewModel::setLanguage
                )
            }

            FilterSection(label = "Duree maximale") {
                val duration = ui.filterState.maxDurationMinutes
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        if (duration >= 240) "Aucune limite" else "${duration / 60}h${(duration % 60).toString().padStart(2, '0')}",
                        fontSize = 12.sp,
                        color = PlexOrange,
                        fontWeight = FontWeight.Medium
                    )
                }
                Slider(
                    value = duration.toFloat(),
                    onValueChange = { viewModel.setDuration(it.roundToInt()) },
                    valueRange = 60f..240f,
                    steps = 5,
                    colors = SliderDefaults.colors(
                        thumbColor = PlexOrange,
                        activeTrackColor = PlexOrange,
                        inactiveTrackColor = PlexBorderLight
                    )
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("1h", "1h30", "2h", "2h30", "3h", "3h30", "Inf").forEach { label ->
                        Text(label, fontSize = 10.sp, color = PlexTextHint)
                    }
                }
            }

            FilterSection(label = "Resolution") {
                SelectChipRow(
                    options = listOf("SD", "720p", "1080p", "4K"),
                    selected = ui.filterState.selectedResolution,
                    onSelect = viewModel::setResolution
                )
            }

            FilterSection(label = "Visionnement") {
                SelectChipRow(
                    options = listOf("Tous", "Non vu", "Deja vu"),
                    selected = when (ui.filterState.watchedFilter) {
                        WatchedFilter.ALL -> "Tous"
                        WatchedFilter.UNWATCHED -> "Non vu"
                        WatchedFilter.WATCHED -> "Deja vu"
                    },
                    onSelect = { label ->
                        viewModel.setWatched(
                            when (label) {
                                "Non vu" -> WatchedFilter.UNWATCHED
                                "Deja vu" -> WatchedFilter.WATCHED
                                else -> WatchedFilter.ALL
                            }
                        )
                    },
                    allowDeselect = false
                )
            }

            Spacer(modifier = Modifier.height(60.dp))
        }
    }

    if (ui.isAiDialogOpen) {
        AiChatDialog(
            messages = ui.aiMessages,
            draft = ui.aiDraft,
            isLoading = ui.isAiLoading,
            candidateCount = ui.aiCandidateCount,
            onDraftChange = viewModel::setAiDraft,
            onSend = viewModel::sendAiMessage,
            onDismiss = viewModel::closeAiDialog
        )
    }
}

@Composable
private fun FilterSection(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PlexSurface, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            label.uppercase(Locale.CANADA_FRENCH),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = PlexTextMuted,
            letterSpacing = 0.8.sp
        )
        content()
    }
}

@Composable
private fun AiActionChip(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(start = 4.dp, end = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) PlexOrangeDim else PlexSurface2)
            .border(
                width = 0.5.dp,
                color = if (enabled) PlexOrange else PlexBorderLight,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = "AI",
                tint = if (enabled) PlexOrange else PlexTextHint,
                modifier = Modifier.size(15.dp)
            )
            Text(
                "AI",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) PlexOrange else PlexTextHint
            )
        }
    }
}

@Composable
private fun PlexDropdown(
    selected: String?,
    options: List<String>,
    placeholder: String,
    displayTransform: (String) -> String = { it },
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(PlexSurface2)
                .border(1.dp, if (expanded) PlexOrange else PlexBorderLight, RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selected?.let(displayTransform) ?: placeholder,
                fontSize = 13.sp,
                color = if (selected != null) PlexTextPrimary else PlexTextHint,
                modifier = Modifier.weight(1f)
            )
            Box(modifier = Modifier.padding(start = 10.dp)) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = if (expanded) PlexOrange else PlexTextHint
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(PlexSurface2)
        ) {
            DropdownMenuItem(
                text = { Text(placeholder, color = PlexTextMuted, fontSize = 13.sp) },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(displayTransform(option), color = PlexTextPrimary, fontSize = 13.sp) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                    leadingIcon = if (option == selected) {
                        { Icon(Icons.Default.Check, contentDescription = null, tint = PlexOrange, modifier = Modifier.size(14.dp)) }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectChipRow(
    options: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    allowDeselect: Boolean = true
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) PlexOrangeDim else PlexSurface2)
                    .border(
                        width = if (isSelected) 1.dp else 0.5.dp,
                        color = if (isSelected) PlexOrange else PlexBorderLight,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelect(if (isSelected && allowDeselect) null else option) }
                    .padding(horizontal = 13.dp, vertical = 7.dp)
            ) {
                Text(
                    option,
                    fontSize = 12.sp,
                    color = if (isSelected) PlexOrange else PlexTextMuted,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiSelectChipRow(
    options: List<String>,
    selected: List<String>,
    onToggle: (String) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        options.forEach { option ->
            val isSelected = selected.any { it.equals(option, ignoreCase = true) }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) PlexOrangeDim else PlexSurface2)
                    .border(
                        width = if (isSelected) 1.dp else 0.5.dp,
                        color = if (isSelected) PlexOrange else PlexBorderLight,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onToggle(option) }
                    .padding(horizontal = 13.dp, vertical = 7.dp)
            ) {
                Text(
                    option,
                    fontSize = 12.sp,
                    color = if (isSelected) PlexOrange else PlexTextMuted,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}
