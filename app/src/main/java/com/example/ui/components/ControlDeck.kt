package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.LabelOff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.HeartRatePreset
import com.example.model.HeartUiState
import com.example.model.TransparencyMode
import com.example.ui.theme.ArterialCrimson
import com.example.ui.theme.ArterialCrimsonGlow
import com.example.ui.theme.EcgMint
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioBorderBright
import com.example.ui.theme.StudioDeepNavy
import com.example.ui.theme.StudioGlassSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VenousCobaltGlow
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlDeck(
    uiState: HeartUiState,
    onBpmChange: (Int) -> Unit,
    onRatePresetSelect: (HeartRatePreset) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onTransparencyModeSelect: (TransparencyMode) -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleLabels: () -> Unit,
    onResetCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(true) }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = StudioGlassSurface,
        border = BorderStroke(1.dp, StudioBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Quick Toolbar Row: Play/Pause, Labels, Reset Camera, Collapse toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onTogglePlayPause,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (uiState.isPlaying) {
                            ArterialCrimson.copy(alpha = 0.22f)
                        } else {
                            EcgMint.copy(alpha = 0.22f)
                        },
                        contentColor = if (uiState.isPlaying) ArterialCrimsonGlow else EcgMint
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(40.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("play_pause_button")
                ) {
                    Icon(
                        imageVector = if (uiState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.cd_play_pause),
                        modifier = Modifier.size(18.dp)
                    )
                }

                OutlinedButton(
                    onClick = onToggleLabels,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (uiState.showLabels) EcgMint.copy(alpha = 0.6f) else StudioBorder
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (uiState.showLabels) EcgMint.copy(alpha = 0.12f) else StudioDeepNavy,
                        contentColor = if (uiState.showLabels) EcgMint else TextSecondary
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(40.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("toggle_labels_button")
                ) {
                    Icon(
                        imageVector = if (uiState.showLabels) Icons.AutoMirrored.Filled.Label else Icons.AutoMirrored.Filled.LabelOff,
                        contentDescription = stringResource(R.string.cd_toggle_labels),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = stringResource(R.string.action_labels),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                OutlinedButton(
                    onClick = onResetCamera,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, StudioBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = StudioDeepNavy,
                        contentColor = TextSecondary
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(40.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("reset_camera_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.cd_reset_camera),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.action_reset_view),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("expand_controls_button")
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.cd_expand_controls),
                        tint = TextSecondary
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Section 1: Transparency & Cutaway Presets + Opacity Slider
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.control_transparency),
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Text(
                                text = "${(uiState.myocardiumOpacity * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = VenousCobaltGlow
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TransparencyMode.entries.forEach { mode ->
                                val active = uiState.transparencyMode == mode
                                ModeChip(
                                    label = stringResource(mode.labelRes),
                                    selected = active,
                                    accentColor = VenousCobaltGlow,
                                    onClick = { onTransparencyModeSelect(mode) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("mode_${mode.name.lowercase()}")
                                )
                            }
                        }

                        Slider(
                            value = uiState.myocardiumOpacity,
                            onValueChange = onOpacityChange,
                            valueRange = 0.08f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = VenousCobaltGlow,
                                activeTrackColor = VenousCobaltGlow,
                                inactiveTrackColor = StudioBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .testTag("opacity_slider")
                        )
                    }

                    // Section 2: Heart Rate (BPM) Slider + Rhythm Presets
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.control_heart_rate),
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Text(
                                text = "${uiState.bpm} ${stringResource(R.string.telemetry_bpm_unit)}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = ArterialCrimsonGlow
                            )
                        }

                        Slider(
                            value = uiState.bpm.toFloat(),
                            onValueChange = { onBpmChange(it.roundToInt()) },
                            valueRange = 30f..180f,
                            colors = SliderDefaults.colors(
                                thumbColor = ArterialCrimsonGlow,
                                activeTrackColor = ArterialCrimson,
                                inactiveTrackColor = StudioBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .testTag("bpm_slider")
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            HeartRatePreset.entries.forEach { preset ->
                                val active = uiState.bpm == preset.bpm
                                ModeChip(
                                    label = "${stringResource(preset.labelRes)} ${preset.bpm}",
                                    selected = active,
                                    accentColor = ArterialCrimsonGlow,
                                    onClick = { onRatePresetSelect(preset) },
                                    modifier = Modifier.testTag("preset_${preset.name.lowercase()}")
                                )
                            }
                        }
                    }

                    Text(
                        text = stringResource(R.string.hint_drag_rotate),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                        color = TextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    accentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(34.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) accentColor.copy(alpha = 0.20f) else StudioDeepNavy,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) accentColor else StudioBorderBright.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = if (selected) accentColor else TextSecondary,
                maxLines = 1
            )
        }
    }
}
