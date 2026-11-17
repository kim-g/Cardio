package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AnatomyStructureId
import com.example.model.BloodCircuitType
import com.example.model.ProjectedLabel
import com.example.ui.theme.ArterialCrimsonGlow
import com.example.ui.theme.EcgMint
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioGlassElevated
import com.example.ui.theme.StudioGlassSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VenousCobaltGlow
import kotlin.math.roundToInt

@Composable
fun AnatomyLabelsOverlay(
    labels: List<ProjectedLabel>,
    showLabels: Boolean,
    selectedStructure: AnatomyStructureId?,
    onSelectStructure: (AnatomyStructureId) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!showLabels) return

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        // Sort back-to-front so frontmost anatomical labels stay above rear ones
        val ordered = labels.sortedByDescending { it.ndcDepth }

        ordered.forEach { label ->
            val isSelected = label.id == selectedStructure
            val accent = when {
                isSelected -> EcgMint
                label.id.circuitType == BloodCircuitType.VENOUS -> VenousCobaltGlow
                else -> ArterialCrimsonGlow
            }
            val xPx = (label.normX * widthPx).roundToInt()
            val yPx = (label.normY * heightPx).roundToInt()

            Box(
                modifier = Modifier
                    .offset { IntOffset(xPx - 44, yPx - 20) }
                    .alpha(if (isSelected) 1f else label.alpha)
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelectStructure(label.id) }
                    .testTag("label_${label.id.name.lowercase()}")
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) StudioGlassElevated else StudioGlassSurface.copy(alpha = 0.82f),
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) EcgMint else StudioBorder
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(accent)
                        )
                        Text(
                            text = stringResource(label.id.shortRes),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isSelected) EcgMint else TextPrimary,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StructureDetailCard(
    structure: AnatomyStructureId?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = structure != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier
    ) {
        structure?.let { info ->
            val accent = when (info.circuitType) {
                BloodCircuitType.VENOUS -> VenousCobaltGlow
                BloodCircuitType.ARTERIAL -> ArterialCrimsonGlow
                BloodCircuitType.VALVE_OR_MIXED -> EcgMint
            }
            val circuitLabel = when (info.circuitType) {
                BloodCircuitType.VENOUS -> stringResource(R.string.legend_venous)
                BloodCircuitType.ARTERIAL -> stringResource(R.string.legend_arterial)
                BloodCircuitType.VALVE_OR_MIXED -> ""
            }

            Surface(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .testTag("structure_detail_card"),
                shape = RoundedCornerShape(18.dp),
                color = StudioGlassElevated,
                border = BorderStroke(1.dp, accent.copy(alpha = 0.55f))
            ) {
                Column(
                    modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(accent)
                            )
                            Text(
                                text = stringResource(info.nameRes),
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            if (circuitLabel.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = accent.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = circuitLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = accent,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("close_detail_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.cd_close_info),
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Text(
                        text = stringResource(info.descRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                }
            }
        }
    }
}
