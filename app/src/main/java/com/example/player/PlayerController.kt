package com.example.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.data.model.EqPreset
import com.example.data.model.MusicTrack
import com.example.data.model.NetworkMode
import com.example.data.model.NowPlayingItem
import com.example.data.model.PlaybackState
import com.example.data.model.RadioStation
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "PlayerController"

class PlayerController(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val itemRegistry = mutableMapOf<String, NowPlayingItem>()
    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null

    var onTrackOrStreamMetadataChanged: ((NowPlayingItem, String?) -> Unit)? = null

    fun connect() {
        if (controllerFuture != null) return
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                try {
                    val controller = future.get()
                    mediaController = controller
                    attachListener(controller)
                    startProgressTicker()
                    syncStateFromPlayer(controller)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to connect MediaController", e)
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    fun release() {
        progressJob?.cancel()
        sleepTimerJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
    }

    fun playTracks(tracks: List<MusicTrack>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val controller = mediaController ?: run {
            connect()
            return
        }

        val mediaItems = tracks.map { track ->
            val nowPlaying = NowPlayingItem.LocalTrack(track)
            itemRegistry[track.id] = nowPlaying

            val extras = Bundle().apply {
                putString("item_id", track.id)
                putString("format_label", track.formatLabel)
            }
            MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(Uri.parse(track.uriString))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .setArtworkUri(track.artworkUriString?.let { Uri.parse(it) })
                        .setExtras(extras)
                        .build()
                )
                .build()
        }

        val safeIndex = startIndex.coerceIn(0, mediaItems.lastIndex)
        controller.setMediaItems(mediaItems, safeIndex, 0L)
        controller.prepare()
        controller.play()

        val selected = itemRegistry[tracks[safeIndex].id]
        _playbackState.update {
            it.copy(
                currentItem = selected,
                detectedStreamTitle = null,
                errorMessage = null,
                codecInfo = "${tracks[safeIndex].formatLabel}${if (tracks[safeIndex].bitrateKbps > 0) " • ${tracks[safeIndex].bitrateKbps} kbps" else ""}"
            )
        }
        if (selected != null) {
            onTrackOrStreamMetadataChanged?.invoke(selected, null)
        }
    }

    fun playRadioStation(station: RadioStation) {
        val controller = mediaController ?: run {
            connect()
            return
        }
        val streamUrl = station.streamUrl
        if (streamUrl.isBlank()) {
            _playbackState.update { it.copy(errorMessage = "Station stream URL is empty.") }
            return
        }

        val item = NowPlayingItem.Radio(station)
        itemRegistry[item.id] = item

        val mediaItem = MediaItem.Builder()
            .setMediaId(item.id)
            .setUri(Uri.parse(streamUrl))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(station.name)
                    .setArtist("${station.country} • Live FM")
                    .setArtworkUri(station.favicon.takeIf { it.isNotBlank() }?.let { Uri.parse(it) })
                    .build()
            )
            .build()

        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()

        val codecDesc = buildString {
            if (station.bitrate > 0) append("${station.bitrate} kbps ")
            if (station.codec.isNotBlank()) append(station.codec.uppercase()) else append("LIVE FM")
        }

        _playbackState.update {
            it.copy(
                currentItem = item,
                detectedStreamTitle = null,
                errorMessage = null,
                codecInfo = codecDesc
            )
        }
        onTrackOrStreamMetadataChanged?.invoke(item, null)
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.playbackState == Player.STATE_IDLE && controller.currentMediaItem != null) {
                controller.prepare()
            }
            controller.play()
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun skipNext() {
        val controller = mediaController ?: return
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
        }
    }

    fun skipPrevious() {
        val controller = mediaController ?: return
        if (controller.currentPosition > 3_500L) {
            controller.seekTo(0L)
        } else if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
        } else {
            controller.seekTo(0L)
        }
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
        _playbackState.update { it.copy(shuffleEnabled = controller.shuffleModeEnabled) }
    }

    fun cycleRepeatMode() {
        val controller = mediaController ?: return
        val nextMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        controller.repeatMode = nextMode
        _playbackState.update { it.copy(repeatMode = nextMode) }
    }

    fun setNetworkMode(mode: NetworkMode) {
        AudioFxBridge.onNetworkModeRebuildRequested?.invoke(mode)
    }

    fun applyEqPreset(preset: EqPreset) {
        AudioFxBridge.applyPreset(preset)
    }

    fun startSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        if (minutes == null || minutes <= 0) {
            _playbackState.update { it.copy(sleepTimerRemainingSec = null) }
            return
        }
        sleepTimerJob = scope.launch {
            var remaining = minutes * 60
            while (remaining > 0 && isActive) {
                _playbackState.update { it.copy(sleepTimerRemainingSec = remaining) }
                delay(1_000L)
                remaining--
            }
            _playbackState.update { it.copy(sleepTimerRemainingSec = null) }
            mediaController?.pause()
        }
    }

    fun clearError() {
        _playbackState.update { it.copy(errorMessage = null) }
    }

    private fun attachListener(controller: MediaController) {
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.update { it.copy(isPlaying = isPlaying) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _playbackState.update {
                    it.copy(
                        isBuffering = playbackState == Player.STATE_BUFFERING,
                        durationMs = controller.duration.coerceAtLeast(0L)
                    )
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId ?: return
                val item = itemRegistry[id]
                _playbackState.update {
                    it.copy(
                        currentItem = item ?: it.currentItem,
                        detectedStreamTitle = null,
                        errorMessage = null,
                        durationMs = controller.duration.coerceAtLeast(0L)
                    )
                }
                if (item != null) {
                    onTrackOrStreamMetadataChanged?.invoke(item, null)
                }
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                val current = _playbackState.value.currentItem ?: return
                if (current is NowPlayingItem.Radio) {
                    val icyTitle = mediaMetadata.title?.toString()?.trim()
                    if (!icyTitle.isNullOrBlank() &&
                        !icyTitle.equals(current.station.name, ignoreCase = true) &&
                        icyTitle != _playbackState.value.detectedStreamTitle
                    ) {
                        _playbackState.update { it.copy(detectedStreamTitle = icyTitle) }
                        onTrackOrStreamMetadataChanged?.invoke(current, icyTitle)
                    }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                for (group in tracks.groups) {
                    if (group.isSelected && group.length > 0) {
                        val format = group.getTrackFormat(0)
                        val bitrate = format.bitrate
                        val sampleMime = format.sampleMimeType
                        if (bitrate > 0 || !sampleMime.isNullOrBlank()) {
                            val label = buildString {
                                if (!sampleMime.isNullOrBlank()) {
                                    append(sampleMime.substringAfter('/').uppercase())
                                }
                                if (bitrate > 0) {
                                    if (isNotEmpty()) append(" • ")
                                    append("${bitrate / 1000} kbps")
                                }
                            }
                            if (label.isNotBlank()) {
                                _playbackState.update { it.copy(codecInfo = label) }
                            }
                            break
                        }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Player error: ${error.errorCodeName}", error)
                val friendly = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                        "Weak or unavailable network. Try enabling Slow Internet Mode or picking a lower-bitrate FM station."
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                        "Stream or audio source unavailable right now. Please try another station or file."
                    else -> error.localizedMessage ?: "Playback failed."
                }
                _playbackState.update { it.copy(errorMessage = friendly, isBuffering = false) }
            }
        })
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                mediaController?.let { controller ->
                    _playbackState.update {
                        it.copy(
                            positionMs = controller.currentPosition.coerceAtLeast(0L),
                            durationMs = controller.duration.coerceAtLeast(0L),
                            bufferedPositionMs = controller.bufferedPosition.coerceAtLeast(0L)
                        )
                    }
                }
                delay(500L)
            }
        }
    }

    private fun syncStateFromPlayer(controller: MediaController) {
        val currentMediaId = controller.currentMediaItem?.mediaId
        val item = currentMediaId?.let { itemRegistry[it] }
        _playbackState.update {
            it.copy(
                currentItem = item ?: it.currentItem,
                isPlaying = controller.isPlaying,
                isBuffering = controller.playbackState == Player.STATE_BUFFERING,
                positionMs = controller.currentPosition.coerceAtLeast(0L),
                durationMs = controller.duration.coerceAtLeast(0L),
                bufferedPositionMs = controller.bufferedPosition.coerceAtLeast(0L),
                shuffleEnabled = controller.shuffleModeEnabled,
                repeatMode = controller.repeatMode
            )
        }
    }
}
