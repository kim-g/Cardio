package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gl.HeartGlSurfaceView
import com.example.ui.components.AnatomyLabelsOverlay
import com.example.ui.components.ControlDeck
import com.example.ui.components.StructureDetailCard
import com.example.ui.components.TelemetryHeader

@Composable
fun Cardio3DScreen(
    viewModel: HeartSimulationViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 680.dp

        // Full-bleed interactive OpenGL ES 3.0 3D Heart Viewport
        HeartGlSurfaceView(
            uiState = uiState,
            onTelemetryUpdate = viewModel::onTelemetryUpdate,
            modifier = Modifier.fillMaxSize()
        )

        // 3D-projected floating anatomical labels
        AnatomyLabelsOverlay(
            labels = telemetry.projectedLabels,
            showLabels = uiState.showLabels,
            selectedStructure = uiState.selectedStructure,
            onSelectStructure = viewModel::selectStructure,
            modifier = Modifier.fillMaxSize()
        )

        if (isWideScreen) {
            // Adaptive Supporting Pane layout for tablets / landscape / unfolded devices
            TelemetryHeader(
                bpm = uiState.bpm,
                telemetry = telemetry,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .widthIn(max = 460.dp)
            )

            StructureDetailCard(
                structure = uiState.selectedStructure,
                onDismiss = { viewModel.selectStructure(null) },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .widthIn(max = 440.dp)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .width(340.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                ControlDeck(
                    uiState = uiState,
                    onBpmChange = viewModel::setBpm,
                    onRatePresetSelect = viewModel::selectRatePreset,
                    onOpacityChange = viewModel::setOpacity,
                    onTransparencyModeSelect = viewModel::selectTransparencyMode,
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onToggleLabels = viewModel::toggleLabels,
                    onResetCamera = viewModel::resetCamera
                )
            }
        } else {
            // Compact portrait layout
            TelemetryHeader(
                bpm = uiState.bpm,
                telemetry = telemetry,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StructureDetailCard(
                    structure = uiState.selectedStructure,
                    onDismiss = { viewModel.selectStructure(null) }
                )

                ControlDeck(
                    uiState = uiState,
                    onBpmChange = viewModel::setBpm,
                    onRatePresetSelect = viewModel::selectRatePreset,
                    onOpacityChange = viewModel::setOpacity,
                    onTransparencyModeSelect = viewModel::selectTransparencyMode,
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onToggleLabels = viewModel::toggleLabels,
                    onResetCamera = viewModel::resetCamera
                )
            }
        }
    }
}
