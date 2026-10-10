package fr.schwanx.vox.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.schwanx.vox.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vox_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val SELECTED_MODEL_ID = stringPreferencesKey("selected_model_id")
        val DEFAULT_LANGUAGE = stringPreferencesKey("default_language")
        val NUM_THREADS = intPreferencesKey("num_threads")
        val KEEP_AUDIO_FILES = booleanPreferencesKey("keep_audio_files")
        val DARK_THEME_MODE = stringPreferencesKey("dark_theme_mode")
        val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    val userSettingsFlow: Flow<UserSettings> = context.dataStore.data.map { preferences ->
        UserSettings(
            selectedModelId = preferences[Keys.SELECTED_MODEL_ID] ?: "ggml-base-q5_1.bin",
            defaultLanguage = preferences[Keys.DEFAULT_LANGUAGE] ?: "auto",
            numThreads = preferences[Keys.NUM_THREADS] ?: 0,
            keepAudioFiles = preferences[Keys.KEEP_AUDIO_FILES] ?: true,
            darkThemeMode = preferences[Keys.DARK_THEME_MODE] ?: "system",
            dynamicColorEnabled = preferences[Keys.DYNAMIC_COLOR_ENABLED] ?: true,
            isOnboardingCompleted = preferences[Keys.ONBOARDING_COMPLETED] ?: false
        )
    }

    suspend fun setSelectedModelId(modelId: String) {
        context.dataStore.edit { preferences ->
            preferences[Keys.SELECTED_MODEL_ID] = modelId
        }
    }

    suspend fun setDefaultLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[Keys.DEFAULT_LANGUAGE] = language
        }
    }

    suspend fun setNumThreads(numThreads: Int) {
        context.dataStore.edit { preferences ->
            preferences[Keys.NUM_THREADS] = numThreads
        }
    }

    suspend fun setKeepAudioFiles(keep: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.KEEP_AUDIO_FILES] = keep
        }
    }

    suspend fun setDarkThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[Keys.DARK_THEME_MODE] = mode
        }
    }

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.DYNAMIC_COLOR_ENABLED] = enabled
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.ONBOARDING_COMPLETED] = completed
        }
    }
}
