package com.rajmani7584.payloaddumper.model

import android.app.Activity
import android.app.Application
import android.os.Environment
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.rajmani7584.payloaddumper.MainActivity
import com.rajmani7584.payloaddumper.engine.chromeos_update_engine.UpdateMetadata
import com.rajmani7584.payloaddumper.nativeHelper.PayloadDumper
import com.rajmani7584.payloaddumper.ui.screens.Screens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DataModel(application: Application): AndroidViewModel(application) {

    private val settingsDataStore = SettingsData(application)
    val externalStorage: String = Environment.getExternalStorageDirectory().absolutePath


    val isDarkTheme =
        settingsDataStore.darkTheme.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val isDynamicColor =
        settingsDataStore.dynamicColor.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val concurrency =
        settingsDataStore.concurrency.stateIn(viewModelScope, SharingStarted.Eagerly, 4)
    val autoDelete =
        settingsDataStore.autoDelete.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _hasPermission = mutableStateOf<Boolean?>(null)
    val hasPermission: State<Boolean?> = _hasPermission

    private val _url =
        mutableStateOf("")
    val remoteUrl: State<String> = _url

    fun setURL(value: String) {
        _url.value = value
    }

    fun setDarkTheme(value: Boolean) {
        viewModelScope.launch {
            settingsDataStore.saveDarkTheme(value)
        }
    }

    fun setDynamicColor(value: Boolean) {
        viewModelScope.launch {
            settingsDataStore.saveDynamicColor(value)
        }
    }

    fun setConcurrency(value: Int) {
        viewModelScope.launch {
            settingsDataStore.saveConcurrency(value)
        }
    }

    fun setAutoDelete(value: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setAutoDelete(value)
        }
    }

    fun setPermission(activity: MainActivity) {
        _hasPermission.value = Utils.hasPermission(activity)
    }

    fun requestPermission(activity: Activity?) {
        if (activity == null) return
        Utils.requestPermission(activity, this)
        _hasPermission.value = Utils.hasPermission(activity)
    }

    private val _lastDirectory = mutableStateOf(externalStorage)
    val lastDirectory: State<String> = _lastDirectory


    private val _outputDirectory = mutableStateOf("$externalStorage/PayloadDumper")
    val outputDirectory: State<String> = _outputDirectory

    fun setLastDirectory(path: String) {
        _lastDirectory.value = path
    }

    fun setOutputDirectory(currentPath: String) {
        _outputDirectory.value = currentPath
    }

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading
    private val _error = mutableStateOf<String?>(null)
    val error: State<String?> = _error

    private val _payload = mutableStateOf<Payload?>(null)
    val payload: State<Payload?> = _payload

    suspend fun initPayload(
        payloadType: PayloadType,
        homeNavController: NavHostController
    ) {
        _isLoading.value = true
        _payload.value = null
        _error.value = null
        try {
            val data = PayloadDumper.init(payloadType)
            val name = payloadType.getPathString().split("/").last()
            try {
                val manifest = UpdateMetadata.DeltaArchiveManifest.parseFrom(data)
                _payload.value = Payload(name, payloadType, manifest)
            } catch (e: Exception) {
                _error.value = "Can't parse buffer data: ${e.message}"
            }
        } catch (e: Exception) {
            _error.value = "Rust error: ${e.message}"
        } finally {
            delay(200)
            if (payload.value != null)
                viewModelScope.launch (Dispatchers.Main) {
                    homeNavController.navigate(Screens.Extract.route) {
                        popUpTo(Screens.Home.route) {
                            saveState = true
                        }
                        restoreState = true
                        launchSingleTop = false
                    }
                }
            _isLoading.value = false
        }
    }

    fun init(payloadType: PayloadType, homeNavController: NavHostController) {
        viewModelScope.launch(Dispatchers.IO) {
            initPayload(payloadType, homeNavController)
        }
    }
}