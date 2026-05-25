package com.rajmani7584.payloaddumper.model

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore("settings")

class SettingsRepo(context: Context) {

    private val dataStore: DataStore<Preferences> = context.dataStore

    private val _settings = MutableStateFlow(SettingsState.Snapshot())
    val settings: StateFlow<SettingsState.Snapshot> = _settings.asStateFlow()

    suspend fun load() {
        val defaults = SettingsState.Snapshot()
        _settings.value = dataStore.data.first().let { pref ->
            SettingsState.Snapshot(
                concurrency  = pref[Keys.CONCURRENCY_KEY]   ?: defaults.concurrency,
                darkTheme    = DarkMode.fromCode(pref[Keys.DARK_THEME_KEY] ?: defaults.darkTheme.code),
                colorTheme   = ColorTheme.fromCode(pref[Keys.COLOR_THEME_KEY] ?: defaults.colorTheme.code),
                autoDelete   = pref[Keys.AUTO_DELETE_KEY]   ?: defaults.autoDelete,
                overwrite   = pref[Keys.OVERWRITE_KEY]   ?: defaults.overwrite,
                verifyHash   = pref[Keys.VERIFY_HASH_KEY]   ?: defaults.verifyHash,
                bufferSize   = BufSize.fromCode(pref[Keys.BUF_SIZE_KEY]   ?: defaults.bufferSize.code),
            )
        }
    }

    suspend fun updateAndSave(update: (SettingsState.Snapshot) -> SettingsState.Snapshot): SettingsState.Snapshot {
        val new = update(_settings.value)
        _settings.value = new
        dataStore.edit { pref ->
            pref[Keys.CONCURRENCY_KEY]   = new.concurrency
            pref[Keys.DARK_THEME_KEY]    = new.darkTheme.code
            pref[Keys.COLOR_THEME_KEY]   = new.colorTheme.code
            pref[Keys.AUTO_DELETE_KEY]   = new.autoDelete
            pref[Keys.AUTO_DELETE_KEY]   = new.autoDelete
            pref[Keys.OVERWRITE_KEY]     = new.overwrite
            pref[Keys.BUF_SIZE_KEY]      = new.bufferSize.code
        }
        return new
    }

    private object Keys {
        val CONCURRENCY_KEY   = intPreferencesKey("concurrency")
        val DARK_THEME_KEY    = intPreferencesKey("dark_theme")
        val COLOR_THEME_KEY     = intPreferencesKey("app_theme")
        val AUTO_DELETE_KEY   = booleanPreferencesKey("auto_delete")
        val OVERWRITE_KEY   = booleanPreferencesKey("overwrite")
        val VERIFY_HASH_KEY   = booleanPreferencesKey("verify_hash")
        val BUF_SIZE_KEY      = intPreferencesKey("buffer_size")
    }
}

object SettingsState {
    data class Snapshot(
        val concurrency:  Int        = SettingParam().concurrency,
        val darkTheme:    DarkMode   = DarkMode.LIGHT,
        val colorTheme:   ColorTheme = ColorTheme.APP,
        val autoDelete:   Boolean    = SettingParam().autoDelete,
        val overwrite:    Boolean    = SettingParam().overwrite,
        val verifyHash:   Boolean    = SettingParam().verifyHash,
        val bufferSize:   BufSize    = BufSize.BUF_256KB
    )
}
data class SettingParam(val concurrency: Int = minOf(4, Runtime.getRuntime().availableProcessors()), val verifyHash: Boolean = true, val autoDelete: Boolean = true, val overwrite: Boolean = true, val bufSize: BufSize = BufSize.BUF_256KB)
object SettingDeps {
    private lateinit var settingsRepo: SettingsRepo

    fun init(context: Context) {
        settingsRepo = SettingsRepo(context.applicationContext)
    }

    val repo: SettingsRepo
        get() {
            check(::settingsRepo.isInitialized) { "Setting Repo not initialized yet" }
            return settingsRepo
        }
}

enum class DarkMode(val code: Int) {
    AUTO(0), LIGHT(1), DARK(2);

    companion object {
        fun fromCode(value: Int) = DarkMode.entries.find { it.code == value } ?: AUTO
    }
}

enum class ColorTheme(val code: Int) {
    APP(0), SYSTEM(1);

    companion object {
        fun fromCode(value: Int) = ColorTheme.entries.find { it.code == value } ?: APP
    }
}

enum class BufSize(val code: Int) {
    BUF_256KB(256), BUF_512KB(512), BUF_1MB(1024), BUF_4MB(4096);

    companion object {
        fun fromCode(value: Int) = BufSize.entries.find { it.code == value } ?: BUF_256KB
    }
}