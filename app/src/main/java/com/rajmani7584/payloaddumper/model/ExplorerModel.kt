package com.rajmani7584.payloaddumper.model

import android.os.Environment
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rajmani7584.payloaddumper.ui.screens.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class ExplorerModel: ViewModel() {

    val externalStorage: String = Environment.getExternalStorageDirectory().absolutePath

    private val _canGoBack = mutableStateOf(false)
    val canGoBack: State<Boolean> = _canGoBack
    private val _canWrite = mutableStateOf(false)
    val canWrite: State<Boolean> = _canWrite
    private val _invalidPath = mutableStateOf(false)
    val invalidPath: State<Boolean> = _invalidPath

    private val _lastDirectory = mutableStateOf(externalStorage)
    val lastDirectory: State<String> = _lastDirectory

    fun setLastDirectory(s: String) {
        _lastDirectory.value = s
        openDirectory(s)
    }

    private var _listInner = mutableStateOf(listOf<FileData>())
    private var _list = mutableStateOf(listOf<FileData>())
    val list: State<List<FileData>> = _list

    private val _showAll = mutableStateOf(false)
//    val showAll: State<Boolean> = _showAll

    fun setShowAll(value: Boolean) {
        _showAll.value = value
        if (value) {
            _list.value = _listInner.value
        } else {
            _list.value = _listInner.value.filter {
                when (it) {
                    is FileData.File -> it.name.endsWith(".zip") || it.name.endsWith(".bin")
                    is FileData.Folder -> true
                }
            }
        }
    }

    fun openDirectory(path: String) {
        val file = File(path)
        _invalidPath.value = !file.exists()
        if (_invalidPath.value) LogManager.error("Error: ${file.absolutePath} doesn't exists")
        _canWrite.value = file.canWrite()
        if (!_canWrite.value) LogManager.error("Error: not write access to ${file.absolutePath}")
        _listInner.value = emptyList()
        if (_invalidPath.value) return
        _canGoBack.value = _lastDirectory.value != externalStorage

        viewModelScope.launch(Dispatchers.IO) {
            val allFiles = file.listFiles()
            if (allFiles != null) {
                var folders = listOf<FileData>()
                var files = listOf<FileData>()

                allFiles.forEach { file ->
                    if (file.isDirectory) {
                        folders = folders + FileData.Folder(file.name)
                    } else files = files + FileData.File(file.name)
                }
                _listInner.value += folders.sortedBy { it.name } + files.sortedBy { it.name }
                delay(20)
                setShowAll(_showAll.value)
            }
        }
    }
}

sealed class FileData {
    abstract val name: String
    data class File(override val name: String): FileData()
    data class Folder(override val name: String): FileData()
}