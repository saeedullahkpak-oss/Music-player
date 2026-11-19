package com.example.data.local

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import com.example.data.model.MusicTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale

private const val TAG = "LocalMusicRepo"
private const val MAX_AI_AUDIO_BYTES = 8 * 1024 * 1024 // 8 MB safe inline limit for Gemini

class LocalMusicRepository(private val context: Context) {

    suspend fun scanDeviceAudio(): List<MusicTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<MusicTrack>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DISPLAY_NAME
        )
        val selection = "${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf("3000")
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val displayNameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(collection, id)
                    val albumId = cursor.getLong(albumIdCol)
                    val artworkUri = if (albumId > 0) {
                        Uri.parse("content://media/external/audio/albumart/$albumId").toString()
                    } else null
                    val mime = cursor.getString(mimeCol) ?: "audio/mpeg"
                    val displayName = cursor.getString(displayNameCol) ?: ""
                    val rawTitle = cursor.getString(titleCol).orEmpty()
                    val title = rawTitle.ifBlank {
                        displayName.substringBeforeLast('.').ifBlank { "Untitled Track" }
                    }
                    val artist = cursor.getString(artistCol)
                        ?.takeIf { it.isNotBlank() && it != "<unknown>" }
                        ?: "Unknown Artist"
                    val album = cursor.getString(albumCol)
                        ?.takeIf { it.isNotBlank() && it != "<unknown>" }
                        ?: "Single"
                    val durationMs = cursor.getLong(durationCol)
                    val sizeBytes = cursor.getLong(sizeCol)
                    val bitrateKbps = if (durationMs > 0 && sizeBytes > 0) {
                        ((sizeBytes * 8) / durationMs).toInt()
                    } else 0

                    tracks.add(
                        MusicTrack(
                            id = "ms_$id",
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = durationMs,
                            uriString = contentUri.toString(),
                            mimeType = mime,
                            formatLabel = deriveFormatLabel(mime, displayName),
                            bitrateKbps = bitrateKbps,
                            artworkUriString = artworkUri,
                            sizeBytes = sizeBytes,
                            isRemoteUrl = false
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Audio storage permission not granted yet", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning device audio", e)
        }
        tracks
    }

    suspend fun loadTrackFromUri(uriString: String): MusicTrack? = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriString)
            var fileName = "Audio Track"
            var fileSize = 0L
            var mimeType = context.contentResolver.getType(uri) ?: "audio/mpeg"

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) fileName = cursor.getString(nameIdx) ?: fileName
                    if (sizeIdx >= 0) fileSize = cursor.getLong(sizeIdx)
                }
            }

            var title = fileName.substringBeforeLast('.').ifBlank { "Imported Audio" }
            var artist = "Local Audio"
            var album = "Imported Library"
            var durationMs = 0L
            var bitrateKbps = 0

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?.takeIf { it.isNotBlank() }?.let { title = it }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?.takeIf { it.isNotBlank() }?.let { artist = it }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    ?.takeIf { it.isNotBlank() }?.let { album = it }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()?.let { durationMs = it }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                    ?.toIntOrNull()?.let { bitrateKbps = it / 1000 }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                    ?.takeIf { it.isNotBlank() }?.let { mimeType = it }
            } catch (e: Exception) {
                Log.w(TAG, "Metadata retriever fallback for $uriString", e)
            } finally {
                runCatching { retriever.release() }
            }

            MusicTrack(
                id = "uri_${uriString.hashCode()}",
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                uriString = uriString,
                mimeType = mimeType,
                formatLabel = deriveFormatLabel(mimeType, fileName),
                bitrateKbps = bitrateKbps,
                artworkUriString = null,
                sizeBytes = fileSize,
                isRemoteUrl = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load track from URI: $uriString", e)
            null
        }
    }

    suspend fun readAudioBytesForAi(uriString: String): Pair<ByteArray, String>? =
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(uriString)
                val rawMime = context.contentResolver.getType(uri) ?: "audio/mpeg"
                val normalizedMime = normalizeGeminiAudioMime(rawMime)

                context.contentResolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(16 * 1024)
                    val output = ByteArrayOutputStream()
                    var totalRead = 0
                    while (totalRead < MAX_AI_AUDIO_BYTES) {
                        val toRead = minOf(buffer.size, MAX_AI_AUDIO_BYTES - totalRead)
                        val read = input.read(buffer, 0, toRead)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        totalRead += read
                    }
                    val bytes = output.toByteArray()
                    if (bytes.isNotEmpty()) bytes to normalizedMime else null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to read audio bytes for AI analysis", e)
                null
            }
        }

    private fun normalizeGeminiAudioMime(mime: String): String {
        val lower = mime.lowercase(Locale.US)
        return when {
            lower.contains("mp3") || lower.contains("mpeg") -> "audio/mp3"
            lower.contains("wav") -> "audio/wav"
            lower.contains("flac") -> "audio/flac"
            lower.contains("ogg") || lower.contains("opus") -> "audio/ogg"
            lower.contains("aac") -> "audio/aac"
            lower.contains("mp4") || lower.contains("m4a") -> "audio/mp4"
            else -> "audio/mp3"
        }
    }

    private fun deriveFormatLabel(mime: String, fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").uppercase(Locale.US)
        if (ext in listOf("MP3", "FLAC", "WAV", "AAC", "M4A", "OGG", "OPUS", "WMA", "ALAC")) {
            return ext
        }
        return when {
            mime.contains("flac", true) -> "FLAC"
            mime.contains("wav", true) -> "WAV"
            mime.contains("ogg", true) -> "OGG"
            mime.contains("opus", true) -> "OPUS"
            mime.contains("aac", true) -> "AAC"
            mime.contains("mp4", true) || mime.contains("m4a", true) -> "M4A"
            else -> "MP3"
        }
    }
}
