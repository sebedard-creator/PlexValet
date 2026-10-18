package com.seb.sdpr.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.seb.sdpr.domain.model.Movie
import com.seb.sdpr.ui.filter.FilterState
import com.seb.sdpr.ui.theme.PlexBackground
import com.seb.sdpr.ui.theme.PlexOrange
import com.seb.sdpr.ui.theme.PlexSurface
import com.seb.sdpr.ui.theme.PlexSurface2
import com.seb.sdpr.ui.theme.PlexTextMuted
import com.seb.sdpr.ui.theme.PlexTextPrimary
import com.seb.sdpr.ui.theme.PlexTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    filterState: FilterState?,
    viewModel: PlaylistViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onOpenMovie: (String) -> Unit
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(filterState) {
        viewModel.load(filterState)
    }

    Scaffold(
        containerColor = PlexBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Playlist", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "${ui.movies.size} film${if (ui.movies.size > 1) "s" else ""}",
                            fontSize = 12.sp,
                            color = PlexTextMuted
                        )
                    }
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
                Text(ui.error!!, color = PlexTextSecondary)
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(ui.movies, key = { it.ratingKey }) { movie ->
                        PlaylistMovieCard(
                            movie = movie,
                            serverIp = ui.serverIp,
                            plexToken = ui.plexToken,
                            onClick = { onOpenMovie(movie.ratingKey) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistMovieCard(
    movie: Movie,
    serverIp: String,
    plexToken: String,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val posterUrl = if (movie.thumbPath.isNullOrBlank() || serverIp.isBlank() || plexToken.isBlank()) {
        null
    } else {
        "http://$serverIp:32400${movie.thumbPath}?X-Plex-Token=$plexToken"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = PlexSurface,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (posterUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(posterUrl).crossfade(true).build(),
                    contentDescription = movie.title,
                    modifier = Modifier
                        .width(86.dp)
                        .height(128.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .width(86.dp)
                        .height(128.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(PlexSurface2),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Movie,
                        contentDescription = null,
                        tint = PlexTextMuted,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    movie.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = PlexTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    movie.year?.let {
                        Text("$it", color = PlexTextMuted, fontSize = 13.sp)
                    }
                    Text(movie.durationDisplay(), color = PlexTextMuted, fontSize = 13.sp)
                    movie.contentRating?.let {
                        Text(it, color = PlexTextMuted, fontSize = 13.sp)
                    }
                    movie.resolution?.let {
                        Text(it, color = PlexTextMuted, fontSize = 13.sp)
                    }
                    movie.hdrFormat?.let {
                        Text(it, color = PlexTextMuted, fontSize = 13.sp)
                    }
                    movie.bitrateMbpsDisplay?.let {
                        Text(it, color = PlexTextMuted, fontSize = 13.sp)
                    }
                }
                if (movie.genres.isNotEmpty()) {
                    Text(
                        movie.genres.joinToString(" • "),
                        color = PlexTextSecondary,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                movie.summary?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        color = PlexTextMuted,
                        fontSize = 12.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
