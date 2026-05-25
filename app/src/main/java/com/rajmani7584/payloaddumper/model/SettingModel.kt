package com.rajmani7584.payloaddumper.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class SettingModel(private val repo: SettingsRepo) : ViewModel() {

    companion object {
        val Factory = viewModelFactory {
            initializer { SettingModel(SettingDeps.repo) }
        }
    }

    val settings: StateFlow<SettingsState.Snapshot> = repo.settings

    val processors = 8

    init {
        viewModelScope.launch {
            repo.load()
        }
    }


    suspend fun setConcurrency(value: Int) {
        withContext(Dispatchers.IO) {
            repo.updateAndSave { it.copy(concurrency = value) }
        }
    }
    suspend fun setDarkTheme(value: Int) {
        withContext(Dispatchers.IO) {
            repo.updateAndSave { it.copy(darkTheme = DarkMode.fromCode(value)) }
        }
    }
    suspend fun setColorTheme(value: Int) {
        withContext(Dispatchers.IO) {
            repo.updateAndSave { it.copy(colorTheme = ColorTheme.fromCode(value)) }
        }
    }
    suspend fun setAutoDelete(value: Boolean) {
        withContext(Dispatchers.IO) {
            repo.updateAndSave { it.copy(autoDelete = value) }
        }
    }
    suspend fun setOverWrite(value: Boolean) {
        withContext(Dispatchers.IO) {
            repo.updateAndSave { it.copy(overwrite = value) }
        }
    }
    suspend fun setVerifyHash(value: Boolean) {
        withContext(Dispatchers.IO) {
            repo.updateAndSave { it.copy(verifyHash = value) }
        }
    }
    suspend fun setBufferSize(value: Int) {
        withContext(Dispatchers.IO) {
            repo.updateAndSave { it.copy(bufferSize = BufSize.fromCode(value)) }
        }
    }
}