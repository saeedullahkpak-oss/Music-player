package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.EqPreset
import com.example.data.model.NetworkMode
import com.example.data.model.TranslationLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.auraDataStore: DataStore<Preferences> by preferencesDataStore(name = "aurawave_prefs")

data class UserSettings(
    val preferredLanguage: TranslationLanguage = TranslationLanguage.URDU,
    val autoCatchLyrics: Boolean = true,
    val bilingualLyrics: Boolean = true,
    val networkMode: NetworkMode = NetworkMode.SLOW_INTERNET,
    val eqPreset: EqPreset = EqPreset.FLAT,
    val favoriteStationIds: Set<String> = emptySet(),
    val importedAudioUris: Set<String> = emptySet(),
    val customStreamUrls: Set<String> = emptySet()
)

class AuraPreferences(private val context: Context) {
    private object Keys {
        val PREFERRED_LANGUAGE = stringPreferencesKey("preferred_language")
        val AUTO_CATCH_LYRICS = booleanPreferencesKey("auto_catch_lyrics")
        val BILINGUAL_LYRICS = booleanPreferencesKey("bilingual_lyrics")
        val NETWORK_MODE = stringPreferencesKey("network_mode")
        val EQ_PRESET = stringPreferencesKey("eq_preset")
        val FAVORITE_STATIONS = stringSetPreferencesKey("favorite_stations")
        val IMPORTED_URIS = stringSetPreferencesKey("imported_uris")
        val CUSTOM_STREAMS = stringSetPreferencesKey("custom_streams")
    }

    val settingsFlow: Flow<UserSettings> = context.auraDataStore.data.map { prefs ->
        UserSettings(
            preferredLanguage = prefs[Keys.PREFERRED_LANGUAGE]?.let { name ->
                TranslationLanguage.entries.firstOrNull { it.name == name }
            } ?: TranslationLanguage.URDU,
            autoCatchLyrics = prefs[Keys.AUTO_CATCH_LYRICS] ?: true,
            bilingualLyrics = prefs[Keys.BILINGUAL_LYRICS] ?: true,
            networkMode = prefs[Keys.NETWORK_MODE]?.let { name ->
                NetworkMode.entries.firstOrNull { it.name == name }
            } ?: NetworkMode.SLOW_INTERNET,
            eqPreset = prefs[Keys.EQ_PRESET]?.let { name ->
                EqPreset.entries.firstOrNull { it.name == name }
            } ?: EqPreset.FLAT,
            favoriteStationIds = prefs[Keys.FAVORITE_STATIONS] ?: emptySet(),
            importedAudioUris = prefs[Keys.IMPORTED_URIS] ?: emptySet(),
            customStreamUrls = prefs[Keys.CUSTOM_STREAMS] ?: emptySet()
        )
    }

    suspend fun setPreferredLanguage(language: TranslationLanguage) {
        context.auraDataStore.edit { it[Keys.PREFERRED_LANGUAGE] = language.name }
    }

    suspend fun setAutoCatchLyrics(enabled: Boolean) {
        context.auraDataStore.edit { it[Keys.AUTO_CATCH_LYRICS] = enabled }
    }

    suspend fun setBilingualLyrics(enabled: Boolean) {
        context.auraDataStore.edit { it[Keys.BILINGUAL_LYRICS] = enabled }
    }

    suspend fun setNetworkMode(mode: NetworkMode) {
        context.auraDataStore.edit { it[Keys.NETWORK_MODE] = mode.name }
    }

    suspend fun setEqPreset(preset: EqPreset) {
        context.auraDataStore.edit { it[Keys.EQ_PRESET] = preset.name }
    }

    suspend fun toggleFavoriteStation(stationId: String) {
        context.auraDataStore.edit { prefs ->
            val current = prefs[Keys.FAVORITE_STATIONS]?.toMutableSet() ?: mutableSetOf()
            if (!current.add(stationId)) {
                current.remove(stationId)
            }
            prefs[Keys.FAVORITE_STATIONS] = current
        }
    }

    suspend fun addImportedAudioUris(uris: Collection<String>) {
        context.auraDataStore.edit { prefs ->
            val current = prefs[Keys.IMPORTED_URIS]?.toMutableSet() ?: mutableSetOf()
            current.addAll(uris)
            prefs[Keys.IMPORTED_URIS] = current
        }
    }

    suspend fun addCustomStreamUrl(entry: String) {
        context.auraDataStore.edit { prefs ->
            val current = prefs[Keys.CUSTOM_STREAMS]?.toMutableSet() ?: mutableSetOf()
            current.add(entry)
            prefs[Keys.CUSTOM_STREAMS] = current
        }
    }
}
