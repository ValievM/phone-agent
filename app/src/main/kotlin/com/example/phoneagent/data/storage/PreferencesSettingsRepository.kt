package com.example.phoneagent.data.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.phoneagent.domain.storage.AppSettings
import com.example.phoneagent.domain.storage.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

public class PreferencesSettingsRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : SettingsRepository {
        override suspend fun load(): AppSettings {
            val preferences = dataStore.data.first()
            return AppSettings(
                useDynamicColors = preferences[USE_DYNAMIC_COLORS] ?: true,
                useDarkTheme = preferences[USE_DARK_THEME],
                model = preferences[MODEL],
            )
        }

        override suspend fun save(settings: AppSettings) {
            val useDarkTheme = settings.useDarkTheme
            val model = settings.model
            dataStore.edit { preferences ->
                preferences[USE_DYNAMIC_COLORS] = settings.useDynamicColors
                if (useDarkTheme == null) {
                    preferences.remove(USE_DARK_THEME)
                } else {
                    preferences[USE_DARK_THEME] = useDarkTheme
                }
                if (model == null) {
                    preferences.remove(MODEL)
                } else {
                    preferences[MODEL] = model
                }
            }
        }

        private companion object {
            val USE_DYNAMIC_COLORS = booleanPreferencesKey("use_dynamic_colors")
            val USE_DARK_THEME = booleanPreferencesKey("use_dark_theme")
            val MODEL = stringPreferencesKey("model")
        }
    }
