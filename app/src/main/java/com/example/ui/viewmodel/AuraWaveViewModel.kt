package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiLyricsRepository
import com.example.data.local.AuraPreferences
import com.example.data.local.LocalMusicRepository
import com.example.data.local.UserSettings
import com.example.data.model.CaughtLyrics
import com.example.data.model.EqPreset
import com.example.data.model.LyricsUiState
import com.example.data.model.MusicTrack
import com.example.data.model.NetworkMode
import com.example.data.model.NowPlayingItem
import com.example.data.model.PlaybackState
import com.example.data.model.RadioStation
import com.example.data.model.TranslationLanguage
import com.example.data.network.RadioRepository
import com.example.player.AudioFxBridge
import com.example.player.PlayerController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "AuraWaveViewModel"

data class RadioUiState(
    val stations: List<RadioStation> = emptyList(),
    val quickTuneStations: List<RadioStation> = RadioRepository.curatedGlobalStations,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val selectedCountryCode: String? = null,
    val selectedGenreTag: String? = null,
    val showFavoritesOnly: Boolean = false
)

data class LibraryUiState(
    val tracks: List<MusicTrack> = emptyList(),
    val isScanning: Boolean = false,
    val searchQuery: String = "",
    val selectedFormat: String = "ALL",
    val hasScannedDevice: Boolean = false
)

class AuraWaveViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val preferences = AuraPreferences(context)
    private val localMusicRepo = LocalMusicRepository(context)
    private val radioRepo = RadioRepository()
    private val aiLyricsRepo = AiLyricsRepository(context)
    private val playerController = PlayerController(context)

    val settings: StateFlow<UserSettings> = preferences.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserSettings()
    )

    val playbackState: StateFlow<PlaybackState> = playerController.playbackState

    private val _libraryState = MutableStateFlow(LibraryUiState())
    val libraryState: StateFlow<LibraryUiState> = _libraryState.asStateFlow()

    private val _radioState = MutableStateFlow(RadioUiState())
    val radioState: StateFlow<RadioUiState> = _radioState.asStateFlow()

    private val _lyricsState = MutableStateFlow<LyricsUiState>(LyricsUiState.Idle)
    val lyricsState: StateFlow<LyricsUiState> = _lyricsState.asStateFlow()

    private var lyricsJob: Job? = null
    private var lastAutoCaughtKey: String? = null

    init {
        playerController.connect()
        playerController.onTrackOrStreamMetadataChanged = { item, icyStreamTitle ->
            handlePlaybackContextChanged(item, icyStreamTitle)
        }

        viewModelScope.launch {
            val initialSettings = preferences.settingsFlow.first()
            AudioFxBridge.activeNetworkMode = initialSettings.networkMode
            AudioFxBridge.applyPreset(initialSettings.eqPreset)
            restoreImportedAndCustomMedia(initialSettings)
            refreshRadioStations(slowInternetOnly = initialSettings.networkMode == NetworkMode.SLOW_INTERNET)
        }
    }

    private suspend fun restoreImportedAndCustomMedia(initialSettings: UserSettings) {
        val restoredTracks = mutableListOf<MusicTrack>()
        initialSettings.importedAudioUris.forEach { uriStr ->
            localMusicRepo.loadTrackFromUri(uriStr)?.let { restoredTracks.add(it) }
        }
        initialSettings.customStreamUrls.forEach { entry ->
            val parts = entry.split("|", limit = 2)
            val title = parts.getOrNull(0).orEmpty().ifBlank { "Custom Audio Stream" }
            val url = parts.getOrNull(1).orEmpty()
            if (url.isNotBlank()) {
                restoredTracks.add(
                    MusicTrack(
                        id = "url_${url.hashCode()}",
                        title = title,
                        artist = "Network Audio",
                        album = "Stream URL",
                        durationMs = 0L,
                        uriString = url,
                        mimeType = "audio/*",
                        formatLabel = "STREAM",
                        isRemoteUrl = true
                    )
                )
            }
        }
        if (restoredTracks.isNotEmpty()) {
            _libraryState.update { state ->
                val merged = (restoredTracks + state.tracks).distinctBy { it.id }
                state.copy(tracks = merged)
            }
        }
    }

    fun scanDeviceMusic() {
        viewModelScope.launch {
            _libraryState.update { it.copy(isScanning = true) }
            val scanned = localMusicRepo.scanDeviceAudio()
            _libraryState.update { state ->
                val merged = (scanned + state.tracks).distinctBy { it.id }
                state.copy(
                    tracks = merged,
                    isScanning = false,
                    hasScannedDevice = true
                )
            }
        }
    }

    fun importAudioUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val persisted = mutableListOf<String>()
            val loaded = mutableListOf<MusicTrack>()
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                val uriStr = uri.toString()
                persisted.add(uriStr)
                localMusicRepo.loadTrackFromUri(uriStr)?.let { loaded.add(it) }
            }
            preferences.addImportedAudioUris(persisted)
            if (loaded.isNotEmpty()) {
                _libraryState.update { state ->
                    val merged = (loaded + state.tracks).distinctBy { it.id }
                    state.copy(tracks = merged)
                }
                playTrack(loaded.first())
            }
        }
    }

    fun addAndPlayCustomAudioUrl(title: String, url: String) {
        val cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) return
        val cleanTitle = title.trim().ifBlank { "Custom Audio Stream" }
        val track = MusicTrack(
            id = "url_${cleanUrl.hashCode()}",
            title = cleanTitle,
            artist = "Network Stream",
            album = "Direct URL",
            durationMs = 0L,
            uriString = cleanUrl,
            mimeType = "audio/*",
            formatLabel = cleanUrl.substringAfterLast('.', "STREAM").uppercase().take(6),
            isRemoteUrl = true
        )
        viewModelScope.launch {
            preferences.addCustomStreamUrl("$cleanTitle|$cleanUrl")
            _libraryState.update { state ->
                val merged = (listOf(track) + state.tracks).distinctBy { it.id }
                state.copy(tracks = merged)
            }
            playTrack(track)
        }
    }

    fun updateLibrarySearch(query: String) {
        _libraryState.update { it.copy(searchQuery = query) }
    }

    fun updateLibraryFormatFilter(format: String) {
        _libraryState.update { it.copy(selectedFormat = format) }
    }

    fun playTrack(track: MusicTrack) {
        val currentList = filteredLibraryTracks().ifEmpty { listOf(track) }
        val idx = currentList.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playerController.playTracks(currentList, idx)
    }

    fun filteredLibraryTracks(): List<MusicTrack> {
        val state = _libraryState.value
        val q = state.searchQuery.trim()
        return state.tracks.filter { track ->
            val matchesQuery = q.isBlank() ||
                track.title.contains(q, ignoreCase = true) ||
                track.artist.contains(q, ignoreCase = true) ||
                track.album.contains(q, ignoreCase = true)
            val matchesFormat = when (state.selectedFormat) {
                "ALL" -> true
                "FLAC/WAV" -> track.formatLabel in listOf("FLAC", "WAV", "ALAC")
                "AAC/M4A" -> track.formatLabel in listOf("AAC", "M4A")
                "OGG" -> track.formatLabel in listOf("OGG", "OPUS")
                else -> track.formatLabel.equals(state.selectedFormat, ignoreCase = true)
            }
            matchesQuery && matchesFormat
        }
    }

    fun refreshRadioStations(
        query: String = _radioState.value.searchQuery,
        countryCode: String? = _radioState.value.selectedCountryCode,
        genreTag: String? = _radioState.value.selectedGenreTag,
        slowInternetOnly: Boolean = settings.value.networkMode == NetworkMode.SLOW_INTERNET
    ) {
        viewModelScope.launch {
            _radioState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    searchQuery = query,
                    selectedCountryCode = countryCode,
                    selectedGenreTag = genreTag
                )
            }
            val result = radioRepo.fetchStations(
                query = query,
                countryCode = countryCode,
                genreTag = genreTag,
                slowInternetOnly = slowInternetOnly
            )
            result.onSuccess { list ->
                _radioState.update {
                    it.copy(stations = list, isLoading = false, errorMessage = null)
                }
            }.onFailure { err ->
                _radioState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.localizedMessage ?: "Failed to load FM stations."
                    )
                }
            }
        }
    }

    fun toggleFavoritesFilter() {
        _radioState.update { it.copy(showFavoritesOnly = !it.showFavoritesOnly) }
    }

    fun toggleFavoriteStation(stationId: String) {
        viewModelScope.launch {
            preferences.toggleFavoriteStation(stationId)
        }
    }

    fun playRadioStation(station: RadioStation) {
        playerController.playRadioStation(station)
    }

    fun togglePlayPause() = playerController.togglePlayPause()
    fun seekTo(positionMs: Long) = playerController.seekTo(positionMs)
    fun skipNext() = playerController.skipNext()
    fun skipPrevious() = playerController.skipPrevious()
    fun toggleShuffle() = playerController.toggleShuffle()
    fun cycleRepeatMode() = playerController.cycleRepeatMode()
    fun startSleepTimer(minutes: Int?) = playerController.startSleepTimer(minutes)
    fun clearPlaybackError() = playerController.clearError()

    fun setNetworkMode(mode: NetworkMode) {
        viewModelScope.launch {
            preferences.setNetworkMode(mode)
            playerController.setNetworkMode(mode)
            refreshRadioStations(slowInternetOnly = mode == NetworkMode.SLOW_INTERNET)
        }
    }

    fun setSlowInternetMode(enabled: Boolean) {
        val next = if (enabled) NetworkMode.SLOW_INTERNET else NetworkMode.BALANCED
        setNetworkMode(next)
    }

    fun setEqPreset(preset: EqPreset) {
        viewModelScope.launch {
            preferences.setEqPreset(preset)
            playerController.applyEqPreset(preset)
        }
    }

    fun setAutoCatchLyrics(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAutoCatchLyrics(enabled)
        }
    }

    fun setBilingualLyrics(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setBilingualLyrics(enabled)
        }
    }

    fun selectTranslationLanguage(language: TranslationLanguage) {
        viewModelScope.launch {
            preferences.setPreferredLanguage(language)
            val currentLyricsState = _lyricsState.value
            val existing = when (currentLyricsState) {
                is LyricsUiState.Ready -> currentLyricsState.lyrics
                is LyricsUiState.Translating -> currentLyricsState.existing
                else -> null
            }
            if (existing != null) {
                translateCurrentLyrics(existing, language)
            } else if (playbackState.value.currentItem != null) {
                catchLyricsForCurrentItem(forceRefresh = false, languageOverride = language)
            }
        }
    }

    private fun handlePlaybackContextChanged(item: NowPlayingItem, icyStreamTitle: String?) {
        val trackKey = buildTrackKey(item, icyStreamTitle)
        val lang = settings.value.preferredLanguage

        // Show instant cached lyrics if already detected before
        aiLyricsRepo.getCached(trackKey, lang)?.let { cached ->
            _lyricsState.value = LyricsUiState.Ready(cached)
            lastAutoCaughtKey = trackKey
            return
        }

        if (!settings.value.autoCatchLyrics) {
            _lyricsState.value = LyricsUiState.Idle
            return
        }

        // Avoid redundant calls on identical radio station before ICY metadata arrives if already caught
        if (lastAutoCaughtKey == trackKey) return
        lastAutoCaughtKey = trackKey

        // For live FM radio without ICY title yet, still catch station/show context or wait if already catching
        catchLyricsInternal(item, icyStreamTitle, lang)
    }

    fun catchLyricsForCurrentItem(
        forceRefresh: Boolean = true,
        languageOverride: TranslationLanguage? = null
    ) {
        val item = playbackState.value.currentItem ?: return
        val icyTitle = playbackState.value.detectedStreamTitle
        val lang = languageOverride ?: settings.value.preferredLanguage
        if (!forceRefresh) {
            val key = buildTrackKey(item, icyTitle)
            aiLyricsRepo.getCached(key, lang)?.let {
                _lyricsState.value = LyricsUiState.Ready(it)
                return
            }
        }
        catchLyricsInternal(item, icyTitle, lang)
    }

    private fun catchLyricsInternal(
        item: NowPlayingItem,
        icyStreamTitle: String?,
        targetLanguage: TranslationLanguage
    ) {
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch {
            val trackKey = buildTrackKey(item, icyStreamTitle)
            try {
                when (item) {
                    is NowPlayingItem.LocalTrack -> {
                        val track = item.track
                        val stageLabel = if (!track.isRemoteUrl) {
                            "Listening to audio & catching lyrics..."
                        } else {
                            "Catching song & generating lyrics..."
                        }
                        _lyricsState.value = LyricsUiState.Catching(stageLabel)

                        val audioPair = if (!track.isRemoteUrl) {
                            localMusicRepo.readAudioBytesForAi(track.uriString)
                        } else null

                        val caught = aiLyricsRepo.catchLyricsFromAudio(
                            trackKey = trackKey,
                            titleHint = track.title,
                            artistHint = track.artist,
                            audioBytes = audioPair?.first,
                            mimeType = audioPair?.second,
                            targetLanguage = targetLanguage
                        )
                        _lyricsState.value = LyricsUiState.Ready(caught)
                    }

                    is NowPlayingItem.Radio -> {
                        val station = item.station
                        val titleToCatch = icyStreamTitle?.takeIf { it.isNotBlank() }
                            ?: "${station.name} (${station.tags.ifBlank { station.country }})"
                        _lyricsState.value = LyricsUiState.Catching(
                            if (!icyStreamTitle.isNullOrBlank()) {
                                "Caught \"$icyStreamTitle\" — fetching lyrics..."
                            } else {
                                "Tuning AI ear to ${station.name}..."
                            }
                        )
                        val caught = aiLyricsRepo.catchLyricsFromRadioStream(
                            trackKey = trackKey,
                            streamTitle = titleToCatch,
                            stationName = station.name,
                            stationCountry = station.country,
                            stationLanguage = station.language,
                            targetLanguage = targetLanguage
                        )
                        _lyricsState.value = LyricsUiState.Ready(caught)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error catching lyrics", e)
                _lyricsState.value = LyricsUiState.Error(
                    e.localizedMessage ?: "Unable to catch lyrics right now."
                )
            }
        }
    }

    private fun translateCurrentLyrics(
        existing: CaughtLyrics,
        targetLanguage: TranslationLanguage
    ) {
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch {
            _lyricsState.value = LyricsUiState.Translating(existing, targetLanguage)
            try {
                val translated = aiLyricsRepo.translateLyrics(existing, targetLanguage)
                _lyricsState.value = LyricsUiState.Ready(translated)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Translation failed", e)
                _lyricsState.value = LyricsUiState.Error(
                    e.localizedMessage ?: "Translation failed."
                )
            }
        }
    }

    private fun buildTrackKey(item: NowPlayingItem, icyTitle: String?): String {
        return when (item) {
            is NowPlayingItem.LocalTrack -> "track_${item.track.id}_${item.track.title}"
            is NowPlayingItem.Radio -> {
                val streamPart = icyTitle?.takeIf { it.isNotBlank() } ?: "station_intro"
                "radio_${item.station.stationUuid}_$streamPart"
            }
        }
    }

    override fun onCleared() {
        playerController.release()
        super.onCleared()
    }
}
