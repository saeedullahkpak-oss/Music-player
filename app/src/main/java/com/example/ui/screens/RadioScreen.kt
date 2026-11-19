package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.NetworkMode
import com.example.data.model.NowPlayingItem
import com.example.data.model.PlaybackState
import com.example.data.model.RadioStation
import com.example.data.network.RadioRepository
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ObsidianNight
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SignalEmeraldContainer
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCardElevated
import com.example.ui.viewmodel.RadioUiState

@Composable
fun RadioScreen(
    radioState: RadioUiState,
    networkMode: NetworkMode,
    favoriteIds: Set<String>,
    playbackState: PlaybackState,
    onToggleSlowInternet: (Boolean) -> Unit,
    onSearchChange: (String) -> Unit,
    onSelectCountry: (String?) -> Unit,
    onSelectGenre: (String?) -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onToggleFavoriteStation: (String) -> Unit,
    onRefresh: () -> Unit,
    onPlayStation: (RadioStation) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSlowNetEnabled = networkMode == NetworkMode.SLOW_INTERNET
    val activeStationId = (playbackState.currentItem as? NowPlayingItem.Radio)?.station?.stationUuid
    val displayedStations = if (radioState.showFavoritesOnly) {
        radioState.stations.filter { it.stationUuid in favoriteIds }
    } else {
        radioState.stations
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, StudioBorder),
                color = StudioCard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_radio_globe),
                        contentDescription = "Global FM Radio Waves",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(22.dp))
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        ObsidianNight.copy(alpha = 0.45f),
                                        ObsidianNight.copy(alpha = 0.92f)
                                    )
                                )
                            )
                    )
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = LiveCoral.copy(alpha = 0.22f),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Public,
                                        contentDescription = null,
                                        tint = LiveCoral,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "WORLDWIDE LIVE FM",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = LiveCoral
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Global FM • Zero-Stutter",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Live radio stations from Pakistan, India, Afghanistan, Arab world, Europe & USA—engineered to stream smoothly even on slow 2G/3G networks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.82f)
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Surface(
                            color = StudioCard.copy(alpha = 0.88f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isSlowNetEnabled) SignalEmerald.copy(alpha = 0.6f) else StudioBorder
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SignalCellularAlt,
                                    contentDescription = null,
                                    tint = if (isSlowNetEnabled) SignalEmerald else StudioAmber
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Slow Internet Mode",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = networkMode.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = isSlowNetEnabled,
                                    onCheckedChange = onToggleSlowInternet,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = ObsidianNight,
                                        checkedTrackColor = SignalEmerald
                                    ),
                                    modifier = Modifier.testTag("slow_internet_switch")
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Quick Tune Low-Bitrate World FM",
                    style = MaterialTheme.typography.titleSmall,
                    color = StudioAmber
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(radioState.quickTuneStations, key = { it.stationUuid }) { station ->
                        val isPlaying = station.stationUuid == activeStationId
                        QuickTuneCard(
                            station = station,
                            isActive = isPlaying,
                            onClick = { onPlayStation(station) }
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = radioState.searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Search FM station or city...") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = StudioAmber,
                        unfocusedBorderColor = StudioBorder,
                        focusedContainerColor = StudioCard,
                        unfocusedContainerColor = StudioCard
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("radio_search_input")
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (radioState.showFavoritesOnly) LiveCoral.copy(alpha = 0.2f) else StudioCard,
                    border = BorderStroke(
                        1.dp,
                        if (radioState.showFavoritesOnly) LiveCoral else StudioBorder
                    )
                ) {
                    IconButton(
                        onClick = onToggleFavoritesOnly,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("radio_favorites_filter_button")
                    ) {
                        Icon(
                            imageVector = if (radioState.showFavoritesOnly) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Filter favorite stations",
                            tint = if (radioState.showFavoritesOnly) LiveCoral else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioRepository.countryFilters.forEach { filter ->
                        val selected = filter.code == radioState.selectedCountryCode
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectCountry(filter.code) },
                            label = { Text(filter.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = StudioAmber.copy(alpha = 0.2f),
                                selectedLabelColor = StudioAmber
                            ),
                            modifier = Modifier.testTag("country_chip_${filter.label}")
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioRepository.genreFilters.forEach { genre ->
                        val selected = genre.tag == radioState.selectedGenreTag
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectGenre(genre.tag) },
                            label = { Text(genre.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricCyan.copy(alpha = 0.16f),
                                selectedLabelColor = ElectricCyan
                            ),
                            modifier = Modifier.testTag("genre_chip_${genre.label}")
                        )
                    }
                }
            }
        }

        if (radioState.isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = StudioAmber)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Tuning global FM frequencies...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else if (radioState.errorMessage != null && displayedStations.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = StudioCard,
                    border = BorderStroke(1.dp, LiveCoral.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = radioState.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = LiveCoral
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(onClick = onRefresh) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry Directory")
                        }
                    }
                }
            }
        } else {
            items(displayedStations, key = { it.stationUuid }) { station ->
                val isCurrent = station.stationUuid == activeStationId
                val isFav = station.stationUuid in favoriteIds
                RadioStationCard(
                    station = station,
                    isCurrent = isCurrent,
                    isPlaying = isCurrent && playbackState.isPlaying,
                    isFavorite = isFav,
                    onToggleFavorite = { onToggleFavoriteStation(station.stationUuid) },
                    onClick = { onPlayStation(station) }
                )
            }
        }
    }
}

@Composable
private fun QuickTuneCard(
    station: RadioStation,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(184.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("quick_tune_${station.stationUuid}"),
        shape = RoundedCornerShape(16.dp),
        color = if (isActive) StudioCardElevated else StudioCard,
        border = BorderStroke(1.dp, if (isActive) StudioAmber else StudioBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = SignalEmeraldContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${station.bitrate}k ${station.codec.ifBlank { "AAC" }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SignalEmerald,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Icon(
                    imageVector = if (isActive) Icons.Rounded.GraphicEq else Icons.Rounded.Radio,
                    contentDescription = null,
                    tint = if (isActive) StudioAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = station.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = station.country.ifBlank { "Global" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun RadioStationCard(
    station: RadioStation,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("station_card_${station.stationUuid}"),
        shape = RoundedCornerShape(16.dp),
        color = if (isCurrent) StudioCardElevated else StudioCard,
        border = BorderStroke(1.dp, if (isCurrent) StudioAmber.copy(alpha = 0.8f) else StudioBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(StudioCardElevated),
                contentAlignment = Alignment.Center
            ) {
                if (station.favicon.isNotBlank()) {
                    AsyncImage(
                        model = station.favicon,
                        contentDescription = station.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp)
                    )
                } else {
                    Icon(
                        imageVector = if (isCurrent && isPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.Radio,
                        contentDescription = station.name,
                        tint = if (isCurrent) StudioAmber else ElectricCyan
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCurrent) StudioAmber else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(station.country.ifBlank { "Global" })
                        if (station.tags.isNotBlank()) {
                            append(" • ")
                            append(station.tags.split(',').take(2).joinToString(", "))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (station.isSlowNetFriendly) {
                        Surface(
                            color = SignalEmeraldContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "SLOW-NET OK",
                                style = MaterialTheme.typography.labelSmall,
                                color = SignalEmerald,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (station.bitrate > 0 || station.codec.isNotBlank()) {
                        Surface(
                            color = StudioAmber.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = buildString {
                                    if (station.bitrate > 0) append("${station.bitrate} kbps ")
                                    if (station.codec.isNotBlank()) append(station.codec.uppercase())
                                }.trim(),
                                style = MaterialTheme.typography.labelSmall,
                                color = StudioAmber,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("fav_station_${station.stationUuid}")
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "Favorite ${station.name}",
                    tint = if (isFavorite) LiveCoral else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
