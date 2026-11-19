package com.example.data.network

import android.util.Log
import com.example.data.model.RadioStation
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

private const val TAG = "RadioRepository"

data class CountryFilter(val code: String?, val label: String)
data class GenreFilter(val tag: String?, val label: String)

class RadioRepository {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "AuraWaveAndroid/1.0")
                .build()
            chain.proceed(request)
        }
        .build()

    private val mirrorUrls = listOf(
        "https://de1.api.radio-browser.info/",
        "https://fi1.api.radio-browser.info/",
        "https://at1.api.radio-browser.info/"
    )

    private fun createApi(baseUrl: String): RadioBrowserApi {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(RadioBrowserApi::class.java)
    }

    suspend fun fetchStations(
        query: String = "",
        countryCode: String? = null,
        genreTag: String? = null,
        slowInternetOnly: Boolean = true
    ): Result<List<RadioStation>> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim().takeIf { it.isNotBlank() }
        val maxBitrate = if (slowInternetOnly) 96 else null

        for (mirror in mirrorUrls) {
            try {
                val api = createApi(mirror)
                val results = api.searchStations(
                    name = cleanQuery,
                    countryCode = countryCode,
                    tag = genreTag,
                    bitrateMax = maxBitrate,
                    order = "votes",
                    reverse = true,
                    hideBroken = true,
                    limit = 60
                ).filter { it.streamUrl.startsWith("http") }

                val sorted = if (slowInternetOnly) {
                    results.sortedWith(
                        compareBy<RadioStation> {
                            when {
                                it.bitrate in 1..64 -> 0
                                it.bitrate in 65..96 -> 1
                                it.bitrate == 0 -> 2
                                else -> 3
                            }
                        }.thenByDescending { it.votes }
                    )
                } else {
                    results
                }

                if (sorted.isNotEmpty()) {
                    return@withContext Result.success(sorted)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Radio mirror failed ($mirror): ${e.message}")
            }
        }

        // Fallback to curated verified global low-bitrate streams if API mirrors are unreachable
        val fallback = filterCuratedFallback(query, countryCode, genreTag, slowInternetOnly)
        if (fallback.isNotEmpty()) {
            Result.success(fallback)
        } else {
            Result.failure(IllegalStateException("Could not reach global radio directory. Check your internet connection."))
        }
    }

    fun getCuratedQuickTune(): List<RadioStation> = curatedGlobalStations

    private fun filterCuratedFallback(
        query: String,
        countryCode: String?,
        genreTag: String?,
        slowInternetOnly: Boolean
    ): List<RadioStation> {
        return curatedGlobalStations.filter { station ->
            val matchesQuery = query.isBlank() ||
                station.name.contains(query, ignoreCase = true) ||
                station.country.contains(query, ignoreCase = true) ||
                station.tags.contains(query, ignoreCase = true)
            val matchesCountry = countryCode == null ||
                station.countryCode.equals(countryCode, ignoreCase = true)
            val matchesTag = genreTag == null ||
                station.tags.contains(genreTag, ignoreCase = true) ||
                station.language.contains(genreTag, ignoreCase = true)
            val matchesBitrate = !slowInternetOnly || station.bitrate <= 96
            matchesQuery && matchesCountry && matchesTag && matchesBitrate
        }
    }

    companion object {
        val countryFilters = listOf(
            CountryFilter(null, "Worldwide"),
            CountryFilter("PK", "Pakistan"),
            CountryFilter("IN", "India"),
            CountryFilter("AF", "Afghanistan"),
            CountryFilter("AE", "UAE"),
            CountryFilter("SA", "Saudi Arabia"),
            CountryFilter("TR", "Turkey"),
            CountryFilter("GB", "UK"),
            CountryFilter("US", "USA")
        )

        val genreFilters = listOf(
            GenreFilter(null, "All Genres"),
            GenreFilter("pop", "Pop"),
            GenreFilter("lofi", "Lo-Fi"),
            GenreFilter("chill", "Chill"),
            GenreFilter("bollywood", "Bollywood"),
            GenreFilter("urdu", "Urdu"),
            GenreFilter("pashto", "Pashto"),
            GenreFilter("arabic", "Arabic"),
            GenreFilter("news", "World News"),
            GenreFilter("classical", "Classical")
        )

        val curatedGlobalStations = listOf(
            RadioStation(
                stationUuid = "curated-somafm-groovesalad-64",
                name = "SomaFM Groove Salad (Low-Bitrate)",
                urlResolved = "https://ice2.somafm.com/groovesalad-64-aac",
                country = "United States",
                countryCode = "US",
                language = "english",
                tags = "ambient,chill,lofi,downtempo",
                bitrate = 64,
                codec = "AAC+",
                votes = 9500,
                isCuratedFallback = true
            ),
            RadioStation(
                stationUuid = "curated-somafm-lush-64",
                name = "SomaFM Lush Vocals (64k)",
                urlResolved = "https://ice2.somafm.com/lush-64-aac",
                country = "United States",
                countryCode = "US",
                language = "english",
                tags = "chill,vocals,electronic,indie",
                bitrate = 64,
                codec = "AAC+",
                votes = 8200,
                isCuratedFallback = true
            ),
            RadioStation(
                stationUuid = "curated-radioparadise-mellow-64",
                name = "Radio Paradise Mellow Mix (64k)",
                urlResolved = "https://stream.radioparadise.com/mellow-64",
                country = "Global",
                countryCode = "US",
                language = "english",
                tags = "rock,acoustic,chill,eclectic",
                bitrate = 64,
                codec = "AAC",
                votes = 9100,
                isCuratedFallback = true
            ),
            RadioStation(
                stationUuid = "curated-somafm-defcon-32",
                name = "SomaFM DEF CON Radio (Ultra-Low 32k)",
                urlResolved = "https://ice2.somafm.com/defcon-32-aac",
                country = "United States",
                countryCode = "US",
                language = "english",
                tags = "electronic,lofi,ambient",
                bitrate = 32,
                codec = "AAC+",
                votes = 7400,
                isCuratedFallback = true
            ),
            RadioStation(
                stationUuid = "curated-bbc-world",
                name = "BBC World Service",
                urlResolved = "https://stream.live.vc.bbcmedia.co.uk/bbc_world_service",
                country = "United Kingdom",
                countryCode = "GB",
                language = "english",
                tags = "news,world,talk",
                bitrate = 56,
                codec = "MP3",
                votes = 9800,
                isCuratedFallback = true
            ),
            RadioStation(
                stationUuid = "curated-fip-world",
                name = "FIP World Radio",
                urlResolved = "https://icecast.radiofrance.fr/fipworld-lofi.mp3",
                country = "France",
                countryCode = "FR",
                language = "multilingual",
                tags = "world,arabic,urdu,pop,jazz",
                bitrate = 32,
                codec = "MP3",
                votes = 8900,
                isCuratedFallback = true
            )
        )
    }
}
