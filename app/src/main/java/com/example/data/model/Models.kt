package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uriString: String,
    val mimeType: String,
    val formatLabel: String,
    val bitrateKbps: Int = 0,
    val artworkUriString: String? = null,
    val sizeBytes: Long = 0L,
    val isRemoteUrl: Boolean = false
)

@JsonClass(generateAdapter = true)
data class RadioStation(
    @Json(name = "stationuuid") val stationUuid: String = "",
    @Json(name = "name") val name: String = "",
    @Json(name = "url_resolved") val urlResolved: String = "",
    @Json(name = "url") val url: String = "",
    @Json(name = "country") val country: String = "",
    @Json(name = "countrycode") val countryCode: String = "",
    @Json(name = "language") val language: String = "",
    @Json(name = "tags") val tags: String = "",
    @Json(name = "bitrate") val bitrate: Int = 0,
    @Json(name = "codec") val codec: String = "",
    @Json(name = "favicon") val favicon: String = "",
    @Json(name = "votes") val votes: Int = 0,
    val isCuratedFallback: Boolean = false
) {
    val streamUrl: String
        get() = urlResolved.ifBlank { url }

    val isSlowNetFriendly: Boolean
        get() = bitrate in 1..64
}

enum class TranslationLanguage(
    val englishName: String,
    val nativeLabel: String,
    val isRtl: Boolean
) {
    ORIGINAL("Original", "Original", false),
    URDU("Urdu", "اردو", true),
    HINDI("Hindi", "हिन्दी", false),
    PASHTO("Pashto", "پښتو", true),
    ARABIC("Arabic", "العربية", true),
    ENGLISH("English", "English", false)
}

enum class NetworkMode(
    val title: String,
    val subtitle: String,
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int
) {
    SLOW_INTERNET(
        title = "Slow Internet",
        subtitle = "≤64 kbps priority • 25s anti-stutter buffer",
        minBufferMs = 25_000,
        maxBufferMs = 60_000,
        bufferForPlaybackMs = 1_500,
        bufferForPlaybackAfterRebufferMs = 4_000
    ),
    BALANCED(
        title = "Balanced",
        subtitle = "Adaptive stream • 15s buffer",
        minBufferMs = 15_000,
        maxBufferMs = 35_000,
        bufferForPlaybackMs = 1_000,
        bufferForPlaybackAfterRebufferMs = 2_500
    ),
    HIGH_QUALITY(
        title = "Studio Hi-Fi",
        subtitle = "Instant start • 8s buffer",
        minBufferMs = 8_000,
        maxBufferMs = 25_000,
        bufferForPlaybackMs = 500,
        bufferForPlaybackAfterRebufferMs = 1_500
    )
}

enum class EqPreset(val displayName: String, val bassBoostPermille: Short) {
    FLAT("Studio Flat", 0),
    BASS_BOOST("Deep Bass", 650),
    VOCAL_CLARITY("Lyrics Clarity", 150),
    ROCK("Rock Live", 450),
    JAZZ("Warm Acoustic", 300),
    NIGHT_FM("Night FM", 250)
}

data class LyricLine(
    val timestampSec: Int? = null,
    val original: String,
    val translated: String? = null
)

data class CaughtLyrics(
    val trackKey: String,
    val detectedTitle: String,
    val detectedArtist: String,
    val detectedLanguage: String,
    val sourceMethod: String,
    val targetLanguage: TranslationLanguage,
    val lines: List<LyricLine>,
    val culturalNote: String? = null,
    val cachedAtMs: Long = System.currentTimeMillis()
)

sealed interface LyricsUiState {
    data object Idle : LyricsUiState
    data class Catching(val stage: String) : LyricsUiState
    data class Translating(val existing: CaughtLyrics, val targetLanguage: TranslationLanguage) : LyricsUiState
    data class Ready(val lyrics: CaughtLyrics) : LyricsUiState
    data class Error(val message: String) : LyricsUiState
}

sealed interface NowPlayingItem {
    val id: String
    val title: String
    val subtitle: String
    val artworkUrl: String?
    val isLiveStream: Boolean

    data class LocalTrack(val track: MusicTrack) : NowPlayingItem {
        override val id: String get() = track.id
        override val title: String get() = track.title
        override val subtitle: String get() = "${track.artist} • ${track.formatLabel}"
        override val artworkUrl: String? get() = track.artworkUriString
        override val isLiveStream: Boolean get() = track.isRemoteUrl && track.durationMs <= 0L
    }

    data class Radio(val station: RadioStation) : NowPlayingItem {
        override val id: String get() = station.stationUuid
        override val title: String get() = station.name
        override val subtitle: String
            get() = buildString {
                if (station.country.isNotBlank()) append(station.country)
                if (station.bitrate > 0) {
                    if (isNotEmpty()) append(" • ")
                    append("${station.bitrate} kbps")
                }
                if (station.codec.isNotBlank()) {
                    if (isNotEmpty()) append(" ")
                    append(station.codec.uppercase())
                }
            }.ifBlank { "Global Live FM" }
        override val artworkUrl: String? get() = station.favicon.takeIf { it.isNotBlank() }
        override val isLiveStream: Boolean get() = true
    }
}

data class PlaybackState(
    val currentItem: NowPlayingItem? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val detectedStreamTitle: String? = null,
    val codecInfo: String? = null,
    val errorMessage: String? = null,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = 0,
    val sleepTimerRemainingSec: Int? = null
)
