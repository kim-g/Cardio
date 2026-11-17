package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.CardiacPhase
import com.example.model.TelemetrySnapshot
import com.example.ui.theme.ArterialCrimson
import com.example.ui.theme.ArterialCrimsonGlow
import com.example.ui.theme.EcgMint
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioGlassSurface
import com.example.ui.theme.SystoleAmber
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VenousCobalt
import com.example.ui.theme.VenousCobaltGlow

@Composable
fun TelemetryHeader(
    bpm: Int,
    telemetry: TelemetrySnapshot,
    modifier: Modifier = Modifier
) {
    val phaseColor by animateColorAsState(
        targetValue = when (telemetry.phase) {
            CardiacPhase.ATRIAL_SYSTOLE -> SystoleAmber
            CardiacPhase.VENTRICULAR_SYSTOLE -> ArterialCrimsonGlow
            CardiacPhase.DIASTOLE -> EcgMint
        },
        label = "phaseColor"
    )

    val pulseScale = if (telemetry.phase == CardiacPhase.VENTRICULAR_SYSTOLE) 1.18f else 1.0f

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = StudioGlassSurface,
        border = BorderStroke(1.dp, StudioBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Row: BPM + Live ECG Oscilloscope + Blood Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // BPM Display
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = ArterialCrimson,
                        modifier = Modifier.size((18 * pulseScale).dp)
                    )
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = bpm.toString(),
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp
                                ),
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = stringResource(R.string.telemetry_bpm_unit),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                    }
                }

                // Live ECG Waveform
                EcgOscilloscope(
                    samples = telemetry.ecgHistory,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                )

                // Venous / Arterial Legend
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    BloodLegendDot(
                        color = ArterialCrimsonGlow,
                        label = stringResource(R.string.legend_arterial)
                    )
                    BloodLegendDot(
                        color = VenousCobaltGlow,
                        label = stringResource(R.string.legend_venous)
                    )
                }
            }

            // Bottom Row: Active Cardiac Phase Pill + Description
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = phaseColor.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, phaseColor.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(phaseColor)
                        )
                        Text(
                            text = stringResource(telemetry.phase.titleRes),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = phaseColor,
                            maxLines = 1
                        )
                    }
                }

                Text(
                    text = stringResource(telemetry.phase.descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BloodLegendDot(
    color: Color,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
            color = TextSecondary,
            maxLines = 1
        )
    }
}

@Composable
private fun EcgOscilloscope(
    samples: List<Float>,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x66060C16))
    ) {
        val w = size.width
        val h = size.height
        val baselineY = h * 0.72f

        // Subtle medical grid
        val gridCol = TextMuted.copy(alpha = 0.18f)
        for (i in 1..6) {
            val gx = w * (i / 7f)
            drawLine(gridCol, Offset(gx, 0f), Offset(gx, h), strokeWidth = 1f)
        }
        drawLine(gridCol, Offset(0f, baselineY), Offset(w, baselineY), strokeWidth = 1f)

        if (samples.size < 2) return@Canvas

        fun sampleToY(v: Float): Float =
            (baselineY - v * (h * 0.62f)).coerceIn(2f, h - 2f)

        val path = Path()
        val count = samples.size
        val maxColumns = (w / 1.5f).toInt().coerceAtLeast(60)

        var lastDrawnY = sampleToY(samples.last())
        if (count <= maxColumns) {
            val stepX = w / (count - 1).coerceAtLeast(1)
            for (i in 0 until count) {
                val x = i * stepX
                val y = sampleToY(samples[i])
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
        } else {
            // Chronological min-max extremum decimation per pixel column (no averaging!)
            val stepX = w / maxColumns
            var started = false
            for (col in 0 until maxColumns) {
                val startIdx = (col * count) / maxColumns
                val endIdx = (((col + 1) * count) / maxColumns).coerceAtMost(count)
                if (startIdx >= endIdx) continue

                var minIdx = startIdx
                var maxIdx = startIdx
                var minVal = samples[startIdx]
                var maxVal = samples[startIdx]
                for (i in (startIdx + 1) until endIdx) {
                    val v = samples[i]
                    if (v < minVal) {
                        minVal = v
                        minIdx = i
                    }
                    if (v > maxVal) {
                        maxVal = v
                        maxIdx = i
                    }
                }

                val x = col * stepX
                val yMin = sampleToY(minVal)
                val yMax = sampleToY(maxVal)
                if (!started) {
                    val firstY = if (minIdx <= maxIdx) yMin else yMax
                    path.moveTo(x, firstY)
                    started = true
                }
                if (minIdx == maxIdx) {
                    path.lineTo(x, yMin)
                    lastDrawnY = yMin
                } else if (minIdx < maxIdx) {
                    path.lineTo(x, yMin)
                    path.lineTo(x, yMax)
                    lastDrawnY = yMax
                } else {
                    path.lineTo(x, yMax)
                    path.lineTo(x, yMin)
                    lastDrawnY = yMin
                }
            }
        }

        drawPath(
            path = path,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    EcgMint.copy(alpha = 0.14f),
                    EcgMint.copy(alpha = 0.78f),
                    EcgMint
                )
            ),
            style = Stroke(
                width = 1.7.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Bevel
            )
        )

        // Glowing leading phosphor dot
        drawCircle(
            color = EcgMint.copy(alpha = 0.34f),
            radius = 4.5.dp.toPx(),
            center = Offset(w - 2f, lastDrawnY)
        )
        drawCircle(
            color = Color.White,
            radius = 1.8.dp.toPx(),
            center = Offset(w - 2f, lastDrawnY)
        )
    }
}
