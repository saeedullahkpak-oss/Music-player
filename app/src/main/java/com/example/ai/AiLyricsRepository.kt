package com.example.ai

import android.content.Context
import android.util.Log
import com.example.data.model.CaughtLyrics
import com.example.data.model.LyricLine
import com.example.data.model.TranslationLanguage
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "AiLyricsRepo"

class AiLyricsRepository(private val context: Context) {

    private val memoryCache = ConcurrentHashMap<String, CaughtLyrics>()

    private val lyricsSchema = Schema.obj(
        properties = mapOf(
            "detectedTitle" to Schema.string(description = "Detected song title"),
            "detectedArtist" to Schema.string(description = "Detected artist or singer name"),
            "detectedLanguage" to Schema.string(description = "Original language of the song lyrics"),
            "culturalNote" to Schema.string(description = "Brief 1-sentence mood or poetic meaning summary"),
            "lines" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "timestampSec" to Schema.integer(
                            description = "Approximate timestamp in seconds from start of audio",
                            nullable = true
                        ),
                        "original" to Schema.string(description = "Lyric line in original script/language"),
                        "translated" to Schema.string(
                            description = "Natural poetic translation in target language, or null if target is Original",
                            nullable = true
                        )
                    ),
                    optionalProperties = listOf("timestampSec", "translated")
                ),
                description = "Sequential lyric lines of the song"
            )
        ),
        optionalProperties = listOf("culturalNote")
    )

    private val structuredModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = lyricsSchema
                temperature = 0.3f
            }
        )
    }

    fun getCached(trackKey: String, targetLanguage: TranslationLanguage): CaughtLyrics? {
        val cacheKey = buildCacheKey(trackKey, targetLanguage)
        memoryCache[cacheKey]?.let { return it }

        val file = cacheFileFor(cacheKey)
        if (file.exists()) {
            runCatching {
                val json = JSONObject(file.readText())
                val parsed = deserializeCachedLyrics(json, trackKey, targetLanguage)
                memoryCache[cacheKey] = parsed
                return parsed
            }
        }
        return null
    }

    suspend fun catchLyricsFromAudio(
        trackKey: String,
        titleHint: String,
        artistHint: String,
        audioBytes: ByteArray?,
        mimeType: String?,
        targetLanguage: TranslationLanguage
    ): CaughtLyrics = withContext(Dispatchers.IO) {
        getCached(trackKey, targetLanguage)?.let { return@withContext it }

        val targetLangInstruction = if (targetLanguage == TranslationLanguage.ORIGINAL) {
            "Set 'translated' to null for every line."
        } else {
            "Also translate every lyric line accurately and poetically into ${targetLanguage.englishName} (${targetLanguage.nativeLabel}) using authentic native script."
        }

        val sourceLabel = if (audioBytes != null && mimeType != null) "Audio AI Ear" else "Metadata AI"

        val promptText = buildString {
            appendLine("You are an expert multilingual music transcriber and poetic translator.")
            if (audioBytes != null) {
                appendLine("Listen carefully to the attached audio track.")
                appendLine("Transcribe all sung/spoken lyrics accurately in their original language and native script, with approximate timestampSec values.")
            } else {
                appendLine("Identify the song and provide its accurate lyrics based on the track information.")
            }
            appendLine("Track hint: Title='$titleHint', Artist='$artistHint'.")
            appendLine("If the audio is purely instrumental with no vocals, return 3-5 descriptive atmospheric lines in 'lines' describing the instrumentation and mood.")
            appendLine(targetLangInstruction)
        }

        try {
            val response = withTimeout(60_000L) {
                if (audioBytes != null && mimeType != null) {
                    structuredModel.generateContent(
                        content {
                            inlineData(audioBytes, mimeType)
                            text(promptText)
                        }
                    )
                } else {
                    structuredModel.generateContent(promptText)
                }
            }

            val rawJson = response.text
                ?: throw IllegalStateException("AI returned an empty lyrics response.")
            val result = parseStructuredResponse(
                rawJson = rawJson,
                trackKey = trackKey,
                sourceMethod = sourceLabel,
                targetLanguage = targetLanguage
            )
            saveToCache(result)
            result
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI lyrics catching timed out", e)
            throw IllegalStateException("AI lyrics detection timed out after 60s. Please retry.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI lyrics catching failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Failed to catch song lyrics.", e)
        }
    }

    suspend fun catchLyricsFromRadioStream(
        trackKey: String,
        streamTitle: String,
        stationName: String,
        stationCountry: String,
        stationLanguage: String,
        targetLanguage: TranslationLanguage
    ): CaughtLyrics = withContext(Dispatchers.IO) {
        getCached(trackKey, targetLanguage)?.let { return@withContext it }

        val targetLangInstruction = if (targetLanguage == TranslationLanguage.ORIGINAL) {
            "Set 'translated' to null for every line."
        } else {
            "Translate every line naturally and poetically into ${targetLanguage.englishName} (${targetLanguage.nativeLabel}) in authentic native script."
        }

        val prompt = buildString {
            appendLine("You are an expert global FM radio music assistant and lyricist.")
            appendLine("A live FM radio stream is currently playing:")
            appendLine("Now Playing Metadata / Stream Title: '$streamTitle'")
            appendLine("Radio Station: '$stationName' ($stationCountry, language: $stationLanguage)")
            appendLine("Identify the song and singer from the stream metadata.")
            appendLine("Provide the song's complete key lyrics (verse, chorus, bridge) in its original language/script.")
            appendLine("If the stream title indicates a talk show, news broadcast, or station ID rather than a named song, explain what is airing and provide helpful bilingual context lines.")
            appendLine(targetLangInstruction)
        }

        try {
            val response = withTimeout(60_000L) {
                structuredModel.generateContent(prompt)
            }
            val rawJson = response.text
                ?: throw IllegalStateException("AI returned an empty response for radio stream.")
            val result = parseStructuredResponse(
                rawJson = rawJson,
                trackKey = trackKey,
                sourceMethod = "FM Stream AI",
                targetLanguage = targetLanguage
            )
            saveToCache(result)
            result
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Radio lyrics catching timed out", e)
            throw IllegalStateException("Radio lyrics identification timed out.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Radio lyrics catching failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Failed to catch FM lyrics.", e)
        }
    }

    suspend fun translateLyrics(
        existing: CaughtLyrics,
        targetLanguage: TranslationLanguage
    ): CaughtLyrics = withContext(Dispatchers.IO) {
        if (targetLanguage == TranslationLanguage.ORIGINAL) {
            return@withContext existing.copy(
                targetLanguage = TranslationLanguage.ORIGINAL,
                lines = existing.lines.map { it.copy(translated = null) }
            )
        }

        getCached(existing.trackKey, targetLanguage)?.let { return@withContext it }

        val numberedLines = existing.lines.mapIndexed { idx, line ->
            "${idx + 1}. [${line.timestampSec ?: -1}] ${line.original}"
        }.joinToString("\n")

        val prompt = buildString {
            appendLine("Translate the following song lyrics into ${targetLanguage.englishName} (${targetLanguage.nativeLabel}).")
            appendLine("Song: '${existing.detectedTitle}' by '${existing.detectedArtist}' (Original language: ${existing.detectedLanguage}).")
            appendLine("Keep exact line-by-line correspondence (${existing.lines.size} lines), preserve original text in 'original', preserve timestampSec, and write natural poetic ${targetLanguage.englishName} in authentic native script in 'translated'.")
            appendLine("Also provide a brief 1-sentence poetic summary in ${targetLanguage.englishName} in 'culturalNote'.")
            appendLine("Lyrics:")
            appendLine(numberedLines)
        }

        try {
            val response = withTimeout(45_000L) {
                structuredModel.generateContent(prompt)
            }
            val rawJson = response.text
                ?: throw IllegalStateException("Translation returned empty response.")
            val translated = parseStructuredResponse(
                rawJson = rawJson,
                trackKey = existing.trackKey,
                sourceMethod = existing.sourceMethod,
                targetLanguage = targetLanguage
            )
            saveToCache(translated)
            translated
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Lyrics translation timed out", e)
            throw IllegalStateException("Translation timed out. Please try again.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Lyrics translation failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Failed to translate lyrics.", e)
        }
    }

    private fun parseStructuredResponse(
        rawJson: String,
        trackKey: String,
        sourceMethod: String,
        targetLanguage: TranslationLanguage
    ): CaughtLyrics {
        val json = JSONObject(rawJson)
        val detectedTitle = json.optString("detectedTitle", "Unknown Track")
        val detectedArtist = json.optString("detectedArtist", "Unknown Artist")
        val detectedLanguage = json.optString("detectedLanguage", "Auto")
        val culturalNote = json.optString("culturalNote").takeIf { it.isNotBlank() && it != "null" }
        val linesArray = json.optJSONArray("lines") ?: JSONArray()

        val lines = buildList {
            for (i in 0 until linesArray.length()) {
                val item = linesArray.optJSONObject(i) ?: continue
                val orig = item.optString("original").trim()
                if (orig.isBlank()) continue
                val ts = if (item.has("timestampSec") && !item.isNull("timestampSec")) {
                    item.optInt("timestampSec").takeIf { it >= 0 }
                } else null
                val trans = if (targetLanguage != TranslationLanguage.ORIGINAL &&
                    item.has("translated") && !item.isNull("translated")
                ) {
                    item.optString("translated").takeIf { it.isNotBlank() && it != "null" }
                } else null
                add(LyricLine(timestampSec = ts, original = orig, translated = trans))
            }
        }

        return CaughtLyrics(
            trackKey = trackKey,
            detectedTitle = detectedTitle,
            detectedArtist = detectedArtist,
            detectedLanguage = detectedLanguage,
            sourceMethod = sourceMethod,
            targetLanguage = targetLanguage,
            lines = lines,
            culturalNote = culturalNote
        )
    }

    private fun saveToCache(lyrics: CaughtLyrics) {
        val cacheKey = buildCacheKey(lyrics.trackKey, lyrics.targetLanguage)
        memoryCache[cacheKey] = lyrics
        runCatching {
            val json = JSONObject().apply {
                put("detectedTitle", lyrics.detectedTitle)
                put("detectedArtist", lyrics.detectedArtist)
                put("detectedLanguage", lyrics.detectedLanguage)
                put("sourceMethod", lyrics.sourceMethod)
                put("culturalNote", lyrics.culturalNote ?: JSONObject.NULL)
                put("cachedAtMs", lyrics.cachedAtMs)
                put("lines", JSONArray().apply {
                    lyrics.lines.forEach { line ->
                        put(JSONObject().apply {
                            put("timestampSec", line.timestampSec ?: JSONObject.NULL)
                            put("original", line.original)
                            put("translated", line.translated ?: JSONObject.NULL)
                        })
                    }
                })
            }
            cacheFileFor(cacheKey).writeText(json.toString())
        }.onFailure {
            Log.w(TAG, "Failed to persist lyrics cache", it)
        }
    }

    private fun deserializeCachedLyrics(
        json: JSONObject,
        trackKey: String,
        targetLanguage: TranslationLanguage
    ): CaughtLyrics {
        val linesArray = json.optJSONArray("lines") ?: JSONArray()
        val lines = buildList {
            for (i in 0 until linesArray.length()) {
                val obj = linesArray.optJSONObject(i) ?: continue
                add(
                    LyricLine(
                        timestampSec = if (!obj.isNull("timestampSec")) obj.optInt("timestampSec") else null,
                        original = obj.optString("original"),
                        translated = if (!obj.isNull("translated")) obj.optString("translated") else null
                    )
                )
            }
        }
        return CaughtLyrics(
            trackKey = trackKey,
            detectedTitle = json.optString("detectedTitle"),
            detectedArtist = json.optString("detectedArtist"),
            detectedLanguage = json.optString("detectedLanguage"),
            sourceMethod = json.optString("sourceMethod", "Cached AI"),
            targetLanguage = targetLanguage,
            lines = lines,
            culturalNote = if (!json.isNull("culturalNote")) json.optString("culturalNote") else null,
            cachedAtMs = json.optLong("cachedAtMs", System.currentTimeMillis())
        )
    }

    private fun buildCacheKey(trackKey: String, language: TranslationLanguage): String {
        return "${trackKey.hashCode()}_${language.name}"
    }

    private fun cacheFileFor(cacheKey: String): File {
        val dir = File(context.filesDir, "lyrics_cache").apply { mkdirs() }
        return File(dir, "$cacheKey.json")
    }
}
