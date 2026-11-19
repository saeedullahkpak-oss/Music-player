package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.UserSettings
import com.example.data.model.CaughtLyrics
import com.example.data.model.LyricsUiState
import com.example.data.model.NetworkMode
import com.example.data.model.NowPlayingItem
import com.example.data.model.PlaybackState
import com.example.data.model.TranslationLanguage
import com.example.ui.components.StudioWaveformVisualizer
import com.example.ui.components.formatDurationMs
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ObsidianNight
import com.example.ui.theme.OnElectricCyan
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SignalEmeraldContainer
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCardElevated

@Composable
fun StudioScreen(
    playbackState: PlaybackState,
    lyricsState: LyricsUiState,
    settings: UserSettings,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onCatchLyrics: () -> Unit,
    onSelectLanguage: (TranslationLanguage) -> Unit,
    onToggleAutoCatch: (Boolean) -> Unit,
    onToggleBilingual: (Boolean) -> Unit,
    onJumpToLibrary: () -> Unit,
    onJumpToRadio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val item = playbackState.currentItem
    if (item == null) {
        EmptyStudioView(
            onJumpToLibrary = onJumpToLibrary,
            onJumpToRadio = onJumpToRadio,
            modifier = modifier
        )
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 700.dp
        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.46f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    PlayerDeckCard(
                        item = item,
                        playbackState = playbackState,
                        networkMode = settings.networkMode,
                        onTogglePlayPause = onTogglePlayPause,
                        onSeekTo = onSeekTo,
                        onSkipNext = onSkipNext,
                        onSkipPrevious = onSkipPrevious,
                        onToggleShuffle = onToggleShuffle,
                        onCycleRepeat = onCycleRepeat,
                        onOpenEqualizer = onOpenEqualizer,
                        onOpenSleepTimer = onOpenSleepTimer
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(0.54f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    AiLyricsTranslatorPanel(
                        lyricsState = lyricsState,
                        settings = settings,
                        currentPositionMs = playbackState.positionMs,
                        isLocalSeekable = !item.isLiveStream,
                        onCatchLyrics = onCatchLyrics,
                        onSelectLanguage = onSelectLanguage,
                        onToggleAutoCatch = onToggleAutoCatch,
                        onToggleBilingual = onToggleBilingual,
                        onSeekToSeconds = { sec -> onSeekTo(sec * 1000L) }
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PlayerDeckCard(
                    item = item,
                    playbackState = playbackState,
                    networkMode = settings.networkMode,
                    onTogglePlayPause = onTogglePlayPause,
                    onSeekTo = onSeekTo,
                    onSkipNext = onSkipNext,
                    onSkipPrevious = onSkipPrevious,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeat = onCycleRepeat,
                    onOpenEqualizer = onOpenEqualizer,
                    onOpenSleepTimer = onOpenSleepTimer
                )
                AiLyricsTranslatorPanel(
                    lyricsState = lyricsState,
                    settings = settings,
                    currentPositionMs = playbackState.positionMs,
                    isLocalSeekable = !item.isLiveStream,
                    onCatchLyrics = onCatchLyrics,
                    onSelectLanguage = onSelectLanguage,
                    onToggleAutoCatch = onToggleAutoCatch,
                    onToggleBilingual = onToggleBilingual,
                    onSeekToSeconds = { sec -> onSeekTo(sec * 1000L) }
                )
            }
        }
    }
}

@Composable
private fun EmptyStudioView(
    onJumpToLibrary: () -> Unit,
    onJumpToRadio: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = StudioCard,
            border = BorderStroke(1.dp, StudioBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_album_placeholder),
                    contentDescription = "AuraWave Studio",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(22.dp))
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "AuraWave AI Studio",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Play any song or tune into live Worldwide FM Radio. AuraWave automatically catches the lyrics and translates them into Urdu, Hindi, Pashto, Arabic, or English.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onJumpToLibrary,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = OnElectricCyan
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("studio_empty_music_button")
                    ) {
                        Icon(Icons.Rounded.LibraryMusic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("My Music")
                    }
                    OutlinedButton(
                        onClick = onJumpToRadio,
                        border = BorderStroke(1.dp, StudioAmber),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("studio_empty_radio_button")
                    ) {
                        Icon(Icons.Rounded.Radio, contentDescription = null, tint = StudioAmber, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("FM Radio", color = StudioAmber)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerDeckCard(
    item: NowPlayingItem,
    playbackState: PlaybackState,
    networkMode: NetworkMode,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSleepTimer: () -> Unit
) {
    val isRadio = item is NowPlayingItem.Radio
    val accent = if (isRadio) StudioAmber else ElectricCyan

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    val duration = playbackState.durationMs.coerceAtLeast(1L)
    val currentFraction = if (!item.isLiveStream && playbackState.durationMs > 0L) {
        (playbackState.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = StudioCard,
        border = BorderStroke(1.dp, StudioBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.12f),
                            StudioCard
                        )
                    )
                )
                .padding(18.dp)
        ) {
            // Top Status & Utility Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = if (isRadio) LiveCoral.copy(alpha = 0.18f) else SignalEmeraldContainer,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isRadio) Icons.Rounded.SignalCellularAlt else Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = if (isRadio) LiveCoral else SignalEmerald,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isRadio) {
                                "LIVE FM • ${networkMode.title.uppercase()}"
                            } else {
                                playbackState.codecInfo ?: "HI-RES AUDIO"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isRadio) LiveCoral else SignalEmerald
                        )
                    }
                }

                Row {
                    IconButton(
                        onClick = onOpenEqualizer,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("studio_eq_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Equalizer,
                            contentDescription = "Open Equalizer",
                            tint = ElectricCyan
                        )
                    }
                    IconButton(
                        onClick = onOpenSleepTimer,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("studio_sleep_timer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bedtime,
                            contentDescription = "Sleep Timer",
                            tint = if (playbackState.sleepTimerRemainingSec != null) StudioAmber else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Artwork + Title + Waveform
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(StudioCardElevated)
                ) {
                    if (!item.artworkUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = item.artworkUrl,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Image(
                            painter = painterResource(
                                id = if (isRadio) R.drawable.img_radio_globe else R.drawable.img_album_placeholder
                            ),
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playbackState.detectedStreamTitle ?: item.title,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isRadio && playbackState.detectedStreamTitle != null) {
                            "${item.title} • ${item.subtitle}"
                        } else {
                            item.subtitle
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (playbackState.codecInfo != null && isRadio) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (playbackState.isBuffering) "Anti-stutter buffering..." else "Stream: ${playbackState.codecInfo}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SignalEmerald
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            StudioWaveformVisualizer(
                isPlaying = playbackState.isPlaying,
                isLiveRadio = isRadio,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (!item.isLiveStream) {
                Slider(
                    value = if (isScrubbing) scrubFraction else currentFraction,
                    onValueChange = {
                        isScrubbing = true
                        scrubFraction = it
                    },
                    onValueChangeFinished = {
                        onSeekTo((scrubFraction * duration).toLong())
                        isScrubbing = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricCyan,
                        activeTrackColor = ElectricCyan,
                        inactiveTrackColor = StudioBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("studio_seek_slider")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val displayedPos = if (isScrubbing) (scrubFraction * duration).toLong() else playbackState.positionMs
                    Text(
                        text = formatDurationMs(displayedPos),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatDurationMs(playbackState.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Primary Playback Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isRadio) {
                    IconButton(
                        onClick = onToggleShuffle,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("studio_shuffle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (playbackState.shuffleEnabled) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onSkipPrevious,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("studio_previous_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = accent,
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onTogglePlayPause)
                        .testTag("studio_play_pause_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (playbackState.isBuffering) {
                            CircularProgressIndicator(
                                color = ObsidianNight,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(26.dp)
                            )
                        } else {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                tint = ObsidianNight,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                if (!isRadio) {
                    IconButton(
                        onClick = onSkipNext,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("studio_next_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Next",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    IconButton(
                        onClick = onCycleRepeat,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("studio_repeat_button")
                    ) {
                        Icon(
                            imageVector = if (playbackState.repeatMode == Player.REPEAT_MODE_ONE) {
                                Icons.Rounded.RepeatOne
                            } else {
                                Icons.Rounded.Repeat
                            },
                            contentDescription = "Repeat mode",
                            tint = if (playbackState.repeatMode != Player.REPEAT_MODE_OFF) {
                                ElectricCyan
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiLyricsTranslatorPanel(
    lyricsState: LyricsUiState,
    settings: UserSettings,
    currentPositionMs: Long,
    isLocalSeekable: Boolean,
    onCatchLyrics: () -> Unit,
    onSelectLanguage: (TranslationLanguage) -> Unit,
    onToggleAutoCatch: (Boolean) -> Unit,
    onToggleBilingual: (Boolean) -> Unit,
    onSeekToSeconds: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = StudioCard,
        border = BorderStroke(1.dp, StudioBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Translate,
                        contentDescription = null,
                        tint = ElectricCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "AI Lyrics & Translator",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Auto-catch vocals & translate instantly",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onCatchLyrics,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("refresh_lyrics_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Re-catch lyrics with AI",
                        tint = StudioAmber
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Auto-catch & Bilingual toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Auto-Catch",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = settings.autoCatchLyrics,
                        onCheckedChange = onToggleAutoCatch,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianNight,
                            checkedTrackColor = ElectricCyan
                        ),
                        modifier = Modifier.testTag("auto_catch_switch")
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Bilingual",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = settings.bilingualLyrics,
                        onCheckedChange = onToggleBilingual,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianNight,
                            checkedTrackColor = StudioAmber
                        ),
                        modifier = Modifier.testTag("bilingual_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 6-Language Selector Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TranslationLanguage.entries.forEach { lang ->
                    val selected = lang == settings.preferredLanguage
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectLanguage(lang) },
                        label = {
                            Text(
                                text = if (lang == TranslationLanguage.ORIGINAL || lang == TranslationLanguage.ENGLISH) {
                                    lang.englishName
                                } else {
                                    "${lang.nativeLabel} • ${lang.englishName}"
                                }
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricCyan.copy(alpha = 0.18f),
                            selectedLabelColor = ElectricCyan
                        ),
                        modifier = Modifier.testTag("lang_chip_${lang.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            when (lyricsState) {
                LyricsUiState.Idle -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button(
                            onClick = onCatchLyrics,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = OnElectricCyan
                            ),
                            modifier = Modifier.testTag("catch_lyrics_now_button")
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Catch Song Lyrics with AI")
                        }
                    }
                }

                is LyricsUiState.Catching -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = ElectricCyan)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = lyricsState.stage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ElectricCyan,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                is LyricsUiState.Translating -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                color = StudioAmber,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Translating into ${lyricsState.targetLanguage.englishName} (${lyricsState.targetLanguage.nativeLabel})...",
                                style = MaterialTheme.typography.labelLarge,
                                color = StudioAmber
                            )
                        }
                        LyricsContentList(
                            lyrics = lyricsState.existing,
                            bilingual = settings.bilingualLyrics,
                            currentPositionMs = currentPositionMs,
                            isLocalSeekable = isLocalSeekable,
                            onSeekToSeconds = onSeekToSeconds
                        )
                    }
                }

                is LyricsUiState.Ready -> {
                    LyricsContentList(
                        lyrics = lyricsState.lyrics,
                        bilingual = settings.bilingualLyrics,
                        currentPositionMs = currentPositionMs,
                        isLocalSeekable = isLocalSeekable,
                        onSeekToSeconds = onSeekToSeconds
                    )
                }

                is LyricsUiState.Error -> {
                    Surface(
                        color = LiveCoral.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, LiveCoral.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = lyricsState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = LiveCoral
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onCatchLyrics,
                                border = BorderStroke(1.dp, LiveCoral),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("Try Again", color = LiveCoral)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsContentList(
    lyrics: CaughtLyrics,
    bilingual: Boolean,
    currentPositionMs: Long,
    isLocalSeekable: Boolean,
    onSeekToSeconds: (Int) -> Unit
) {
    val currentSec = (currentPositionMs / 1000L).toInt()
    val activeLineIndex = remember(lyrics.lines, currentSec, isLocalSeekable) {
        if (!isLocalSeekable) -1
        else lyrics.lines.indexOfLast { (it.timestampSec ?: Int.MAX_VALUE) <= currentSec }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            color = StudioCardElevated,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${lyrics.detectedTitle} — ${lyrics.detectedArtist}",
                        style = MaterialTheme.typography.titleSmall,
                        color = ElectricCyan,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = ElectricCyan.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${lyrics.detectedLanguage} • ${lyrics.sourceMethod}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (!lyrics.culturalNote.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val noteDirection = if (lyrics.targetLanguage.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                    CompositionLocalProvider(LocalLayoutDirection provides noteDirection) {
                        Text(
                            text = lyrics.culturalNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        lyrics.lines.forEachIndexed { index, line ->
            val isActive = index == activeLineIndex
            val canSeek = isLocalSeekable && line.timestampSec != null

            Surface(
                color = if (isActive) ElectricCyan.copy(alpha = 0.14f) else StudioCardElevated.copy(alpha = 0.65f),
                shape = RoundedCornerShape(14.dp),
                border = if (isActive) BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.7f)) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .then(
                        if (canSeek) {
                            Modifier.clickable { line.timestampSec?.let(onSeekToSeconds) }
                        } else Modifier
                    )
                    .testTag("lyric_line_$index")
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        val showOriginal = bilingual || line.translated.isNullOrBlank()
                        if (showOriginal) {
                            Text(
                                text = line.original,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                color = if (isActive) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (line.timestampSec != null && isLocalSeekable) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = formatDurationMs(line.timestampSec * 1000L),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isActive) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!line.translated.isNullOrBlank()) {
                        if (bilingual) Spacer(modifier = Modifier.height(6.dp))
                        val rtlDir = if (lyrics.targetLanguage.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                        CompositionLocalProvider(LocalLayoutDirection provides rtlDir) {
                            Text(
                                text = line.translated,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = if (lyrics.targetLanguage.isRtl) 18.sp else 15.sp,
                                    lineHeight = if (lyrics.targetLanguage.isRtl) 28.sp else 22.sp,
                                    fontWeight = if (!bilingual || isActive) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                color = StudioAmber,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
