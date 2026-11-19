package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.configureAppCheck
import com.example.data.model.NowPlayingItem
import com.example.ui.components.AddStreamUrlDialog
import com.example.ui.components.EqualizerBottomSheet
import com.example.ui.components.PersistentMiniPlayer
import com.example.ui.components.SleepTimerBottomSheet
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.RadioScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.theme.AuraWaveTheme
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianNight
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioCard
import com.example.ui.viewmodel.AuraWaveViewModel

private enum class AuraTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    MUSIC(R.string.nav_library, Icons.Rounded.LibraryMusic, Icons.Outlined.LibraryMusic),
    RADIO(R.string.nav_radio, Icons.Rounded.Radio, Icons.Outlined.Radio),
    STUDIO(R.string.nav_studio, Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure App Check with launch intent before rendering UI or creating ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        setContent {
            AuraWaveTheme(darkTheme = true) {
                AuraWaveApp()
            }
        }
    }
}

@Composable
private fun AuraWaveApp(
    viewModel: AuraWaveViewModel = viewModel()
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val libraryState by viewModel.libraryState.collectAsStateWithLifecycle()
    val radioState by viewModel.radioState.collectAsStateWithLifecycle()
    val lyricsState by viewModel.lyricsState.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(AuraTab.MUSIC) }
    var showEqualizerSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showStreamUrlDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.scanDeviceMusic()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Playback works regardless; notification shows if granted */ }

    val pickAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importAudioUris(uris)
            currentTab = AuraTab.STUDIO
        }
    }

    // Automatically scan local music if permission is already granted
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED) {
            viewModel.scanDeviceMusic()
        }
    }

    // Request notification permission politely on first playback on Android 13+
    LaunchedEffect(playbackState.isPlaying) {
        if (playbackState.isPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(playbackState.errorMessage) {
        val msg = playbackState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearPlaybackError()
    }

    if (currentTab != AuraTab.MUSIC) {
        BackHandler {
            currentTab = AuraTab.MUSIC
        }
    }

    val filteredTracks = remember(
        libraryState.tracks,
        libraryState.searchQuery,
        libraryState.selectedFormat
    ) {
        viewModel.filteredLibraryTracks()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = ObsidianNight,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavRail) {
                    Column(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                        if (currentTab != AuraTab.STUDIO && playbackState.currentItem != null) {
                            PersistentMiniPlayer(
                                playbackState = playbackState,
                                onOpenStudio = { currentTab = AuraTab.STUDIO },
                                onTogglePlayPause = viewModel::togglePlayPause,
                                onNext = viewModel::skipNext
                            )
                        }
                        NavigationBar(
                            containerColor = StudioCard,
                            windowInsets = WindowInsets(0.dp)
                        ) {
                            AuraTab.entries.forEach { tab ->
                                val selected = tab == currentTab
                                val activeColor = if (tab == AuraTab.RADIO) StudioAmber else ElectricCyan
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(tab.labelRes)) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = activeColor,
                                        selectedTextColor = activeColor,
                                        indicatorColor = activeColor.copy(alpha = 0.16f),
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.testTag("nav_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(containerColor = StudioCard) {
                        AuraTab.entries.forEach { tab ->
                            val selected = tab == currentTab
                            val activeColor = if (tab == AuraTab.RADIO) StudioAmber else ElectricCyan
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = activeColor,
                                    selectedTextColor = activeColor,
                                    indicatorColor = activeColor.copy(alpha = 0.16f)
                                ),
                                modifier = Modifier.testTag("rail_${tab.name.lowercase()}")
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        when (currentTab) {
                            AuraTab.MUSIC -> LibraryScreen(
                                tracks = filteredTracks,
                                isScanning = libraryState.isScanning,
                                searchQuery = libraryState.searchQuery,
                                selectedFormat = libraryState.selectedFormat,
                                playbackState = playbackState,
                                onSearchChange = viewModel::updateLibrarySearch,
                                onFormatChange = viewModel::updateLibraryFormatFilter,
                                onImportAudioFiles = {
                                    pickAudioLauncher.launch(arrayOf("audio/*"))
                                },
                                onScanDeviceMusic = {
                                    if (ContextCompat.checkSelfPermission(
                                            context,
                                            audioPermission
                                        ) == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        viewModel.scanDeviceMusic()
                                    } else {
                                        audioPermissionLauncher.launch(audioPermission)
                                    }
                                },
                                onOpenStreamUrlDialog = { showStreamUrlDialog = true },
                                onPlayTrack = { track ->
                                    viewModel.playTrack(track)
                                    currentTab = AuraTab.STUDIO
                                },
                                onJumpToRadio = { currentTab = AuraTab.RADIO }
                            )

                            AuraTab.RADIO -> RadioScreen(
                                radioState = radioState,
                                networkMode = settings.networkMode,
                                favoriteIds = settings.favoriteStationIds,
                                playbackState = playbackState,
                                onToggleSlowInternet = viewModel::setSlowInternetMode,
                                onSearchChange = { q ->
                                    viewModel.refreshRadioStations(query = q)
                                },
                                onSelectCountry = { code ->
                                    viewModel.refreshRadioStations(countryCode = code)
                                },
                                onSelectGenre = { tag ->
                                    viewModel.refreshRadioStations(genreTag = tag)
                                },
                                onToggleFavoritesOnly = viewModel::toggleFavoritesFilter,
                                onToggleFavoriteStation = viewModel::toggleFavoriteStation,
                                onRefresh = { viewModel.refreshRadioStations() },
                                onPlayStation = { station ->
                                    viewModel.playRadioStation(station)
                                    currentTab = AuraTab.STUDIO
                                }
                            )

                            AuraTab.STUDIO -> StudioScreen(
                                playbackState = playbackState,
                                lyricsState = lyricsState,
                                settings = settings,
                                onTogglePlayPause = viewModel::togglePlayPause,
                                onSeekTo = viewModel::seekTo,
                                onSkipNext = viewModel::skipNext,
                                onSkipPrevious = viewModel::skipPrevious,
                                onToggleShuffle = viewModel::toggleShuffle,
                                onCycleRepeat = viewModel::cycleRepeatMode,
                                onOpenEqualizer = { showEqualizerSheet = true },
                                onOpenSleepTimer = { showSleepTimerSheet = true },
                                onCatchLyrics = { viewModel.catchLyricsForCurrentItem(forceRefresh = true) },
                                onSelectLanguage = viewModel::selectTranslationLanguage,
                                onToggleAutoCatch = viewModel::setAutoCatchLyrics,
                                onToggleBilingual = viewModel::setBilingualLyrics,
                                onJumpToLibrary = { currentTab = AuraTab.MUSIC },
                                onJumpToRadio = { currentTab = AuraTab.RADIO }
                            )
                        }
                    }

                    if (useNavRail && currentTab != AuraTab.STUDIO && playbackState.currentItem != null) {
                        PersistentMiniPlayer(
                            playbackState = playbackState,
                            onOpenStudio = { currentTab = AuraTab.STUDIO },
                            onTogglePlayPause = viewModel::togglePlayPause,
                            onNext = viewModel::skipNext
                        )
                    }
                }
            }
        }
    }

    if (showEqualizerSheet) {
        EqualizerBottomSheet(
            selectedPreset = settings.eqPreset,
            onSelectPreset = viewModel::setEqPreset,
            onDismiss = { showEqualizerSheet = false }
        )
    }

    if (showSleepTimerSheet) {
        SleepTimerBottomSheet(
            remainingSeconds = playbackState.sleepTimerRemainingSec,
            onSelectMinutes = viewModel::startSleepTimer,
            onDismiss = { showSleepTimerSheet = false }
        )
    }

    if (showStreamUrlDialog) {
        AddStreamUrlDialog(
            onConfirm = { title, url ->
                viewModel.addAndPlayCustomAudioUrl(title, url)
                currentTab = AuraTab.STUDIO
            },
            onDismiss = { showStreamUrlDialog = false }
        )
    }
}
