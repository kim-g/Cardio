package com.example.ui

import androidx.lifecycle.ViewModel
import com.example.model.AnatomyStructureId
import com.example.model.HeartRatePreset
import com.example.model.HeartUiState
import com.example.model.TelemetrySnapshot
import com.example.model.TransparencyMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class HeartSimulationViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HeartUiState())
    val uiState: StateFlow<HeartUiState> = _uiState.asStateFlow()

    private val _telemetry = MutableStateFlow(TelemetrySnapshot())
    val telemetry: StateFlow<TelemetrySnapshot> = _telemetry.asStateFlow()

    fun setBpm(bpm: Int) {
        _uiState.update { it.copy(bpm = bpm.coerceIn(30, 180)) }
    }

    fun selectRatePreset(preset: HeartRatePreset) {
        _uiState.update { it.copy(bpm = preset.bpm) }
    }

    fun setOpacity(opacity: Float) {
        val clamped = opacity.coerceIn(0.05f, 1.0f)
        _uiState.update { state ->
            val updatedMode = when {
                state.transparencyMode == TransparencyMode.CUTAWAY -> TransparencyMode.CUTAWAY
                clamped >= 0.96f -> TransparencyMode.SOLID
                else -> TransparencyMode.TRANSLUCENT
            }
            state.copy(
                myocardiumOpacity = clamped,
                transparencyMode = updatedMode
            )
        }
    }

    fun selectTransparencyMode(mode: TransparencyMode) {
        _uiState.update {
            it.copy(
                transparencyMode = mode,
                myocardiumOpacity = mode.defaultOpacity
            )
        }
    }

    fun togglePlayPause() {
        _uiState.update { it.copy(isPlaying = !it.isPlaying) }
    }

    fun toggleLabels() {
        _uiState.update { it.copy(showLabels = !it.showLabels) }
    }

    fun selectStructure(structure: AnatomyStructureId?) {
        _uiState.update { state ->
            val next = if (state.selectedStructure == structure) null else structure
            state.copy(selectedStructure = next)
        }
    }

    fun resetCamera() {
        _uiState.update { it.copy(cameraResetToken = it.cameraResetToken + 1) }
    }

    fun onTelemetryUpdate(snapshot: TelemetrySnapshot) {
        _telemetry.value = snapshot
    }
}
