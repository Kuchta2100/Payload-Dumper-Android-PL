package com.rajmani7584.payloaddumper.model

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

class SettingsData(private val context: Context) {

    private val DARK_THEME_KEY = booleanPreferencesKey("dark_theme")
    private val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color")
    private val CONCURRENCY_KEY = intPreferencesKey("concurrency")
    private val AUTO_DELETE_KEY = booleanPreferencesKey("auto_delete")

    val darkTheme: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DARK_THEME_KEY] == true
    }

    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DYNAMIC_COLOR_KEY] == true
    }

    val concurrency: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[CONCURRENCY_KEY] ?: 4
    }

    val autoDelete: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_DELETE_KEY] == true
    }

    suspend fun saveDarkTheme(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DARK_THEME_KEY] = enabled
        }
    }

    suspend fun saveDynamicColor(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DYNAMIC_COLOR_KEY] = enabled
        }
    }

    suspend fun saveConcurrency(value: Int) {
        context.dataStore.edit { preferences ->
            preferences[CONCURRENCY_KEY] = value
        }
    }

    suspend fun setAutoDelete(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_DELETE_KEY] = value
        }
    }
}