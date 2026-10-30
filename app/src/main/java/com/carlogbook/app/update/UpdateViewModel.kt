package com.carlogbook.app.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val update: AvailableUpdate) : UpdateState
    data class Downloading(val update: AvailableUpdate, val progress: Float) : UpdateState
    data class ReadyToInstall(val update: AvailableUpdate, val apk: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    private var downloadJob: Job? = null

    /** Runs the check only once a day, and silently: errors do not bother the user. */
    fun autoCheck() {
        val ctx = getApplication<Application>()
        if (!UpdateChecker.shouldAutoCheck(ctx)) return
        viewModelScope.launch {
            try {
                val update = UpdateChecker.check(ctx)
                UpdateChecker.markChecked(ctx)
                if (update != null) _state.value = UpdateState.Available(update)
            } catch (_: Exception) {
                // Offline or GitHub unreachable: try again tomorrow.
            }
        }
    }

    /** User pressed "Check for updates": always runs and reports the outcome. */
    fun checkNow() {
        val ctx = getApplication<Application>()
        _state.value = UpdateState.Checking
        viewModelScope.launch {
            try {
                val update = UpdateChecker.check(ctx)
                UpdateChecker.markChecked(ctx)
                _state.value = if (update != null) UpdateState.Available(update) else UpdateState.UpToDate
            } catch (e: Exception) {
                _state.value = UpdateState.Failed(e.message ?: "Could not reach GitHub")
            }
        }
    }

    fun download(update: AvailableUpdate) {
        val ctx = getApplication<Application>()
        downloadJob?.cancel()
        _state.value = UpdateState.Downloading(update, 0f)
        downloadJob = viewModelScope.launch {
            try {
                val apk = UpdateInstaller.download(ctx, update) { p ->
                    _state.value = UpdateState.Downloading(update, p)
                }
                _state.value = UpdateState.ReadyToInstall(update, apk)
            } catch (e: Exception) {
                _state.value = UpdateState.Failed(e.message ?: "Download failed")
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        val current = _state.value
        if (current is UpdateState.Downloading) _state.value = UpdateState.Available(current.update)
    }

    fun install(apk: File) {
        UpdateInstaller.install(getApplication(), apk)
    }

    fun dismiss() {
        _state.value = UpdateState.Idle
    }
}
