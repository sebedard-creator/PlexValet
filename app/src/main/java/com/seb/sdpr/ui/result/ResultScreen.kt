package com.seb.sdpr.ui.result

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.seb.sdpr.ui.filter.FilterState
import com.seb.sdpr.ui.theme.AudioGreen
import com.seb.sdpr.ui.theme.ErrorRed
import com.seb.sdpr.ui.theme.PlexBackground
import com.seb.sdpr.ui.theme.PlexBorderLight
import com.seb.sdpr.ui.theme.PlexOrange
import com.seb.sdpr.ui.theme.PlexSurface
import com.seb.sdpr.ui.theme.PlexSurface2
import com.seb.sdpr.ui.theme.PlexTextMuted
import com.seb.sdpr.ui.theme.PlexTextPrimary
import com.seb.sdpr.ui.theme.PlexTextSecondary
import com.seb.sdpr.ui.theme.RTRed
import com.seb.sdpr.util.toDisplayLanguage
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ResultScreen(
    filterState: FilterState?,
    viewModel: ResultViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onReroll: () -> Unit,
    showRerollButton: Boolean = true
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val posterFullscreen = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val showCastDialog = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    LaunchedEffect(filterState) {
        viewModel.setFilterState(filterState)
    }

    Scaffold(
        containerColor = PlexBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        ui.movie?.title ?: "Film",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 16.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PlexBackground,
                    titleContentColor = PlexTextPrimary,
                    navigationIconContentColor = PlexTextSecondary
                )
            )
        }
    ) { padding ->
        when {
            ui.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PlexOrange)
            }

            ui.error != null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(ui.error!!, color = ErrorRed)
            }

            else -> {
                val movie = ui.movie!!
                val posterUrl = "http://${ui.serverIp}:32400${movie.thumbPath}?X-Plex-Token=${ui.plexToken}"

                if (ui.castError != null) {
                    BasicAlertDialog(onDismissRequest = { viewModel.clearCastError() }) {
                        Box(
                            Modifier
                                .background(PlexSurface, RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Text(ui.castError!!, color = ErrorRed)
                        }
                    }
                }

                if (showCastDialog.value) {
                    BasicAlertDialog(onDismissRequest = { showCastDialog.value = false }) {
                        Column(
                            modifier = Modifier
                                .background(PlexSurface, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Caster sur...", fontSize = 18.sp, color = PlexTextPrimary, fontWeight = FontWeight.Bold)
                            if (ui.isLoadingClients) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = PlexOrange,
                                        strokeWidth = 2.dp
                                    )
                                    Text("Recherche des lecteurs Plex…", color = PlexTextSecondary)
                                }
                            } else if (ui.clientsError != null) {
                                Text(ui.clientsError!!, color = ErrorRed)
                                ActionButton("Réessayer", PlexSurface2, PlexOrange, Modifier.fillMaxWidth()) {
                                    viewModel.refreshClients()
                                }
                            } else if (ui.clients.isEmpty()) {
                                Text("Aucun lecteur Plex détecté sur le réseau.", color = PlexTextSecondary)
                            } else {
                                ui.clients.forEach { client ->
                                    val clientName = client.name ?: "Lecteur Inconnu"
                                    val product = client.product ?: ""
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(PlexSurface2)
                                            .clickable {
                                                showCastDialog.value = false
                                                client.machineIdentifier?.let { viewModel.castToClient(it) }
                                            }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(clientName, color = PlexTextPrimary, fontSize = 15.sp)
                                            if (product.isNotBlank()) {
                                                Text(product, color = PlexTextMuted, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                            ActionButton("Annuler", PlexSurface2, PlexTextSecondary, Modifier.fillMaxWidth()) {
                                showCastDialog.value = false
                            }
                        }
                    }
                }

                if (posterFullscreen.value) {
                    BasicAlertDialog(
                        onDismissRequest = { posterFullscreen.value = false }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.96f))
                                .clickable { posterFullscreen.value = false },
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(posterUrl).crossfade(true).build(),
                                contentDescription = movie.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(posterUrl).crossfade(true).build(),
                            contentDescription = movie.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clickable { posterFullscreen.value = true },
                            contentScale = ContentScale.Crop
                        )

                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(movie.title, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = PlexTextPrimary)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    movie.year?.let { MetaBadge("$it") }
                                    movie.contentRating?.let { MetaBadge(it) }
                                    MetaBadge(movie.durationDisplay())
                                    movie.resolution?.let { MetaBadge(it) }
                                    movie.hdrFormat?.let { MetaBadge(it) }
                                    movie.bitrateMbpsDisplay?.let { MetaBadge(it) }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ScoreCard(
                                    label = "Rotten Tomatoes\nCritiques",
                                    value = movie.rottenTomatoesPercent?.let { "$it%" } ?: "N/A",
                                    accent = percentScoreColor(movie.rottenTomatoesPercent?.toFloat()),
                                    modifier = Modifier.weight(1f)
                                )
                                ScoreCard(
                                    label = "IMDb\nPublic",
                                    value = movie.imdbRating?.let { String.format(Locale.US, "%.1f / 10", it) } ?: "N/A",
                                    accent = tenPointScoreColor(movie.imdbRating),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (movie.genres.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    movie.genres.forEach { TagPill(it) }
                                }
                            }

                            if (!movie.director.isNullOrBlank()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    SectionLabel("Realisateur")
                                    Text(movie.director, fontSize = 13.sp, color = PlexTextSecondary, lineHeight = 20.sp)
                                }
                            }

                            if (!movie.summary.isNullOrBlank()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    SectionLabel("Synopsis")
                                    Text(movie.summary, fontSize = 13.sp, color = PlexTextSecondary, lineHeight = 20.sp)
                                }
                            }

                            if (movie.audioLanguages.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    SectionLabel("Pistes audio")
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        movie.audioLanguages.forEach {
                                            TagPill(it.toDisplayLanguage())
                                        }
                                    }
                                }
                            }

                            if (movie.subtitleLanguages.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    SectionLabel("Sous-titres")
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        movie.subtitleLanguages.forEach {
                                            TagPill(it.toDisplayLanguage())
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PlexSurface)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (showRerollButton) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ActionButton("← Filtres", PlexSurface2, PlexTextSecondary, Modifier.weight(1f), onBack)
                                ActionButton("↺ Autre film", PlexSurface2, PlexOrange, Modifier.weight(1f)) {
                                    viewModel.reroll()
                                    onReroll()
                                }
                            }
                        } else {
                            ActionButton(
                                label = "← Retour a la playlist",
                                bg = PlexSurface2,
                                fg = PlexTextSecondary,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = onBack
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionButton("📱 Plex", PlexSurface2, PlexTextPrimary, Modifier.weight(1f)) {
                                clipboard.setText(AnnotatedString(movie.title))
                                val preplayUri = Uri.parse("plex://preplay/?metadataKey=%2Flibrary%2Fmetadata%2F${movie.ratingKey}&server=${ui.machineId}")
                                val playIntent = Intent(Intent.ACTION_VIEW, preplayUri).apply {
                                    setPackage("com.plexapp.android")
                                }
                                try {
                                    context.startActivity(playIntent)
                                } catch (_: ActivityNotFoundException) {
                                    val launchIntent = context.packageManager.getLaunchIntentForPackage("com.plexapp.android")
                                    if (launchIntent != null) {
                                        context.startActivity(launchIntent)
                                    } else {
                                        try {
                                            context.startActivity(
                                                Intent(
                                                    Intent.ACTION_VIEW,
                                                    Uri.parse("market://details?id=com.plexapp.android")
                                                )
                                            )
                                        } catch (_: ActivityNotFoundException) {
                                            // Ignore
                                        }
                                    }
                                }
                            }
                            ActionButton(
                                label = if (ui.isCasting) "..." else "📺 Caster",
                                bg = PlexOrange.copy(alpha = 0.15f),
                                fg = PlexOrange,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (!ui.isCasting) {
                                    viewModel.refreshClients()
                                    showCastDialog.value = true
                                }
                            }
                            ActionButton("▶ Trailer", Color(0x22FF0000), Color(0xFFFF4444), Modifier.weight(1f)) {
                                val query = Uri.encode("${movie.title} ${movie.year ?: ""} trailer")
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://www.youtube.com/results?search_query=$query")
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


private fun percentScoreColor(percentScore: Float?): Color = when {
    percentScore == null -> PlexTextPrimary
    percentScore < 50f -> RTRed
    percentScore < 80f -> PlexTextPrimary
    else -> AudioGreen
}

private fun tenPointScoreColor(tenPointScore: Float?): Color = when {
    tenPointScore == null -> PlexTextPrimary
    tenPointScore < 5f -> RTRed
    tenPointScore < 8f -> PlexTextPrimary
    else -> AudioGreen
}

@Composable
private fun ScoreCard(label: String, value: String, accent: Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(PlexSurface, RoundedCornerShape(10.dp))
            .height(86.dp)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(label, fontSize = 10.sp, color = PlexTextMuted, textAlign = TextAlign.Center, lineHeight = 13.sp)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = accent)
    }
}

@Composable
private fun TagPill(text: String) {
    Box(
        modifier = Modifier
            .background(PlexSurface2, RoundedCornerShape(6.dp))
            .border(0.5.dp, PlexBorderLight, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, fontSize = 11.sp, color = PlexTextSecondary)
    }
}

@Composable
private fun MetaBadge(text: String) {
    Box(
        modifier = Modifier
            .border(0.5.dp, PlexBorderLight, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(text, fontSize = 11.sp, color = PlexTextMuted)
    }
}

@Composable
private fun ResoBadge(text: String) {
    Box(
        modifier = Modifier
            .background(PlexOrange.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(text, fontSize = 11.sp, color = PlexOrange, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = PlexTextMuted, letterSpacing = 0.8.sp)
}

@Composable
private fun ActionButton(
    label: String,
    bg: Color,
    fg: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 13.sp, color = fg, fontWeight = FontWeight.Medium)
    }
}
