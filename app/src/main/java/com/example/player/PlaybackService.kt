package com.example.player

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity
import com.example.data.model.EqPreset
import com.example.data.model.NetworkMode
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

private const val TAG = "PlaybackService"

object AudioFxBridge {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var currentPreset: EqPreset = EqPreset.FLAT
    var activeNetworkMode: NetworkMode = NetworkMode.SLOW_INTERNET
    var onNetworkModeRebuildRequested: ((NetworkMode) -> Unit)? = null

    fun attachAudioSession(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId == 0) return
        releaseEffects()
        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
            }
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = true
            }
            applyPreset(currentPreset)
        } catch (e: Exception) {
            Log.w(TAG, "AudioFx not supported on this device/session: ${e.message}")
        }
    }

    fun applyPreset(preset: EqPreset) {
        currentPreset = preset
        val eq = equalizer ?: return
        try {
            val bands = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            val min = range[0].toInt()
            val max = range[1].toInt()
            val span = (max - min) / 2

            val profile = when (preset) {
                EqPreset.FLAT -> listOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
                EqPreset.BASS_BOOST -> listOf(0.75f, 0.45f, 0.0f, 0.1f, 0.2f)
                EqPreset.VOCAL_CLARITY -> listOf(-0.2f, 0.1f, 0.65f, 0.55f, 0.2f)
                EqPreset.ROCK -> listOf(0.5f, 0.2f, -0.15f, 0.35f, 0.6f)
                EqPreset.JAZZ -> listOf(0.35f, 0.15f, 0.3f, 0.25f, 0.4f)
                EqPreset.NIGHT_FM -> listOf(0.3f, 0.1f, 0.35f, 0.15f, -0.1f)
            }

            for (band in 0 until bands) {
                val factor = profile.getOrElse(band.coerceAtMost(profile.lastIndex)) { 0f }
                val level = (factor * span).toInt().coerceIn(min, max).toShort()
                eq.setBandLevel(band.toShort(), level)
            }

            bassBoost?.let { bb ->
                if (bb.strengthSupported) {
                    bb.setStrength(preset.bassBoostPermille)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply EQ preset ${preset.name}: ${e.message}")
        }
    }

    fun releaseEffects() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        equalizer = null
        bassBoost = null
    }
}

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val player = buildPlayer(AudioFxBridge.activeNetworkMode)
        exoPlayer = player

        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()

        AudioFxBridge.onNetworkModeRebuildRequested = { newMode ->
            if (AudioFxBridge.activeNetworkMode != newMode) {
                AudioFxBridge.activeNetworkMode = newMode
                rebuildPlayerPreservingMedia(newMode)
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun buildPlayer(networkMode: NetworkMode): ExoPlayer {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("Icy-MetaData", "1")
                    .header("User-Agent", "AuraWaveAndroid/1.0")
                    .build()
                chain.proceed(req)
            }
            .build()

        val httpFactory = OkHttpDataSource.Factory(okHttpClient)
        val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)

        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(6))

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                networkMode.minBufferMs,
                networkMode.maxBufferMs,
                networkMode.bufferForPlaybackMs,
                networkMode.bufferForPlaybackAfterRebufferMs
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        return ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                addListener(object : Player.Listener {
                    override fun onAudioSessionIdChanged(audioSessionId: Int) {
                        AudioFxBridge.attachAudioSession(audioSessionId)
                    }
                })
                AudioFxBridge.attachAudioSession(audioSessionId)
            }
    }

    @OptIn(UnstableApi::class)
    private fun rebuildPlayerPreservingMedia(networkMode: NetworkMode) {
        val oldPlayer = exoPlayer ?: return
        val currentIdx = oldPlayer.currentMediaItemIndex
        val currentPos = oldPlayer.currentPosition
        val wasPlaying = oldPlayer.playWhenReady
        val mediaItems = (0 until oldPlayer.mediaItemCount).map { oldPlayer.getMediaItemAt(it) }

        val newPlayer = buildPlayer(networkMode)
        if (mediaItems.isNotEmpty()) {
            newPlayer.setMediaItems(mediaItems, currentIdx.coerceAtLeast(0), currentPos)
            newPlayer.prepare()
            newPlayer.playWhenReady = wasPlaying
        }

        mediaSession?.player = newPlayer
        exoPlayer = newPlayer
        oldPlayer.release()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        AudioFxBridge.onNetworkModeRebuildRequested = null
        AudioFxBridge.releaseEffects()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        exoPlayer = null
        super.onDestroy()
    }
}
