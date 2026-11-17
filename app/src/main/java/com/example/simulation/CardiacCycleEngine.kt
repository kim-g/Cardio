package com.example.simulation

import com.example.model.CardiacPhase
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow

data class PhaseDurationsSec(
    val periodSec: Float,
    val atrialSystoleSec: Float,
    val ventricularSystoleSec: Float,
    val diastoleSec: Float
) {
    val atrialEndFraction: Float get() = (atrialSystoleSec / periodSec).coerceIn(0.02f, 0.45f)
    val systoleEndFraction: Float get() = ((atrialSystoleSec + ventricularSystoleSec) / periodSec).coerceIn(atrialEndFraction + 0.05f, 0.88f)
}

/**
 * Physiological cardiac cycle state evaluated every frame.
 *
 * Non-proportional phase durations by BPM:
 *  - Normal (72 BPM, 833 ms): Atrial systole ~110 ms, Ventricular systole ~270 ms, Diastole ~453 ms.
 *  - Bradycardia (< 72 BPM): Systole durations stay constant at normal (110 ms + 270 ms),
 *    while Diastole expands to absorb the full extra cycle time.
 *  - Exercise / Tachycardia (> 72 BPM): Systole shortens modestly, while Diastole shortens
 *    much more steeply.
 */
data class CardiacCycleState(
    val phaseNormalized: Float,
    val phaseEnum: CardiacPhase,
    val atrialScale: Float,
    val ventricularScale: Float,
    val ventricularTwistRad: Float,
    val vesselPulse: Float,
    val avValveOpen: Float,
    val semilunarValveOpen: Float,
    val venousInflowSpeed: Float,
    val atrialPushSpeed: Float,
    val avValveSpeed: Float,
    val ventricularPushSpeed: Float,
    val semilunarValveSpeed: Float,
    val arterialOutflowSpeed: Float,
    val ecgSample: Float
)

class CardiacCycleEngine {

    private var phase: Float = 0.55f // Start in diastole so filling -> atrial kick -> systolic jet is immediately visible
    private var ecgTimeAccumulator: Float = 0f
    private val ecgRing = ArrayDeque<Float>(ECG_HISTORY_SIZE)

    init {
        val initBpm = 72
        val initStepPhase = ECG_BIN_DT * (initBpm / 60f)
        var pBack = phase - initStepPhase * ECG_HISTORY_SIZE
        while (pBack < 0f) pBack += 1f
        repeat(ECG_HISTORY_SIZE) {
            ecgRing.addLast(sampleEcgExtremum(pBack % 1f, initStepPhase, initBpm))
            pBack = (pBack + initStepPhase) % 1f
        }
    }

    fun step(dtSeconds: Float, bpm: Int, isPlaying: Boolean): CardiacCycleState {
        val clampedBpm = bpm.coerceIn(30, 180)
        val clampedDt = dtSeconds.coerceIn(0f, 0.05f)
        val beatsPerSecond = clampedBpm / 60f
        val prevPhase = phase

        if (isPlaying) {
            phase = (phase + clampedDt * beatsPerSecond) % 1f

            ecgTimeAccumulator += clampedDt
            val phasePerBin = ECG_BIN_DT * beatsPerSecond
            var cursorPhase = phase - ecgTimeAccumulator * beatsPerSecond
            while (cursorPhase < 0f) cursorPhase += 1f

            while (ecgTimeAccumulator >= ECG_BIN_DT) {
                val extremum = sampleEcgExtremum(cursorPhase, phasePerBin, clampedBpm)
                if (ecgRing.size >= ECG_HISTORY_SIZE) {
                    ecgRing.removeFirst()
                }
                ecgRing.addLast(extremum)
                cursorPhase = (cursorPhase + phasePerBin) % 1f
                ecgTimeAccumulator -= ECG_BIN_DT
            }
        }

        val rawPhase = phase
        val durations = computePhaseDurations(clampedBpm)
        val p = mapToCanonicalPhase(rawPhase, durations)

        val phaseEnum = when {
            rawPhase < durations.atrialEndFraction -> CardiacPhase.ATRIAL_SYSTOLE
            rawPhase < durations.systoleEndFraction -> CardiacPhase.VENTRICULAR_SYSTOLE
            else -> CardiacPhase.DIASTOLE
        }

        // 1. Atrial contraction curve (peaks in atrial systole) & diastolic filling expansion
        val atrialContract = gaussian(p, center = 0.075f, width = 0.045f)
        val atrialFill = SplineMath.smoothstep(0.38f, 0.78f, p) * (1f - SplineMath.smoothstep(0.95f, 1.0f, p))
        val atrialScale = 1.0f - 0.17f * atrialContract + 0.08f * atrialFill

        // 2. Ventricular contraction curve & apical torsion
        val ventRise = SplineMath.smoothstep(0.14f, 0.23f, p)
        val ventFall = 1f - SplineMath.smoothstep(0.34f, 0.45f, p)
        val ventContract = (ventRise * ventFall).coerceIn(0f, 1f)
        val ventFill = SplineMath.smoothstep(0.50f, 0.92f, p) * (1f - SplineMath.smoothstep(0.12f, 0.16f, p))
        val ventricularScale = 1.0f - 0.16f * ventContract + 0.05f * ventFill
        val ventricularTwistRad = 0.12f * ventContract

        // 3. Great vessel elastic Windkessel pulse
        val vesselPulse = gaussian(p, center = 0.27f, width = 0.09f) * 0.085f

        // 4. Valve kinematics
        val avOpenAtrialKick = 1f - SplineMath.smoothstep(0.12f, 0.155f, p)
        val avOpenDiastole = SplineMath.smoothstep(0.45f, 0.54f, p) * 0.72f +
            SplineMath.smoothstep(0.93f, 0.99f, p) * 0.28f
        val avValveOpen = when {
            p < 0.155f -> avOpenAtrialKick
            p < 0.45f -> 0f
            else -> avOpenDiastole.coerceIn(0f, 1f)
        }

        val semiRise = SplineMath.smoothstep(0.16f, 0.20f, p)
        val semiFall = 1f - SplineMath.smoothstep(0.37f, 0.425f, p)
        val semilunarValveOpen = (semiRise * semiFall).coerceIn(0f, 1f)

        // 5. Non-proportional flow speeds: systole speed scales with 1/systoleDuration,
        //    so bradycardia keeps normal crisp systolic ejection velocity while tachycardia accelerates.
        val baselineSystoleSec = REST_ATRIAL_SYSTOLE_SEC + REST_VENT_SYSTOLE_SEC
        val currentSystoleSec = durations.atrialSystoleSec + durations.ventricularSystoleSec
        val systoleSpeedFactor = (baselineSystoleSec / currentSystoleSec).coerceIn(0.95f, 1.65f)
        val diastoleSpeedFactor = (REST_DIASTOLE_SEC / durations.diastoleSec).coerceIn(0.65f, 2.4f)

        val venousInflowSpeed = (0.11f + 0.22f * SplineMath.smoothstep(0.32f, 0.72f, p) *
            (1f - 0.65f * atrialContract)) * diastoleSpeedFactor

        val atrialPushSpeed = (0.06f + 0.34f * atrialContract + 0.10f * avOpenDiastole) * systoleSpeedFactor

        val avValveSpeed = if (avValveOpen < 0.05f) {
            0.006f
        } else {
            (0.08f + 0.40f * atrialContract + 0.12f * avOpenDiastole) * avValveOpen * systoleSpeedFactor
        }

        val ejectionCurve = (SplineMath.smoothstep(0.16f, 0.23f, p) *
            (1f - SplineMath.smoothstep(0.34f, 0.43f, p))).coerceIn(0f, 1f)
        val ventricularPushSpeed = (0.05f + 0.14f * atrialContract + 0.46f * ejectionCurve) * systoleSpeedFactor

        val semilunarValveSpeed = if (semilunarValveOpen < 0.05f) {
            0.005f
        } else {
            (0.12f + 0.68f * ejectionCurve) * semilunarValveOpen * systoleSpeedFactor
        }

        val diastolicCoast = 0.11f * exp(-((p - 0.42f).coerceAtLeast(0f)) * 2.2f)
        val arterialOutflowSpeed = (0.08f + 0.62f * ejectionCurve + diastolicCoast) * systoleSpeedFactor

        val ecg = if (isPlaying) {
            sampleEcgExtremum(prevPhase, (rawPhase - prevPhase).let { if (it < 0f) it + 1f else it }, clampedBpm)
        } else {
            computeEcgWave(p)
        }

        return CardiacCycleState(
            phaseNormalized = rawPhase,
            phaseEnum = phaseEnum,
            atrialScale = atrialScale,
            ventricularScale = ventricularScale,
            ventricularTwistRad = ventricularTwistRad,
            vesselPulse = vesselPulse,
            avValveOpen = avValveOpen,
            semilunarValveOpen = semilunarValveOpen,
            venousInflowSpeed = venousInflowSpeed,
            atrialPushSpeed = atrialPushSpeed,
            avValveSpeed = avValveSpeed,
            ventricularPushSpeed = ventricularPushSpeed,
            semilunarValveSpeed = semilunarValveSpeed,
            arterialOutflowSpeed = arterialOutflowSpeed,
            ecgSample = ecg
        )
    }

    fun getEcgHistorySnapshot(): List<Float> = ecgRing.toList()

    /**
     * Maps real cycle progress [0..1) into canonical wave coordinates [0..1) where:
     *  - [0.00..0.14) = Atrial Systole
     *  - [0.14..0.44) = Ventricular Systole
     *  - [0.44..1.00) = Diastole
     * Thus systole maintains physiological real-time duration while diastole expands/shrinks steeply.
     */
    private fun mapToCanonicalPhase(rawPhase: Float, d: PhaseDurationsSec): Float {
        val atrialEnd = d.atrialEndFraction
        val systoleEnd = d.systoleEndFraction
        return when {
            rawPhase < atrialEnd -> (rawPhase / atrialEnd) * 0.14f
            rawPhase < systoleEnd -> 0.14f + ((rawPhase - atrialEnd) / (systoleEnd - atrialEnd)) * 0.30f
            else -> 0.44f + ((rawPhase - systoleEnd) / (1f - systoleEnd)) * 0.56f
        }
    }

    private fun sampleEcgExtremum(startRawPhase: Float, span: Float, bpm: Int): Float {
        val durations = computePhaseDurations(bpm)
        val steps = 16
        var best = computeEcgWave(mapToCanonicalPhase(startRawPhase, durations))
        var bestAbs = kotlin.math.abs(best)
        for (i in 1..steps) {
            val subRaw = (startRawPhase + span * (i.toFloat() / steps)) % 1f
            val canonical = mapToCanonicalPhase(subRaw, durations)
            val v = computeEcgWave(canonical)
            val a = kotlin.math.abs(v)
            if (a > bestAbs) {
                best = v
                bestAbs = a
            }
        }
        return best
    }

    private fun computeEcgWave(p: Float): Float {
        val pWave = 0.18f * gaussian(p, 0.06f, 0.024f)
        val qDip = -0.16f * gaussian(p, 0.144f, 0.007f)
        val rPeak = 1.00f * gaussian(p, 0.164f, 0.0085f)
        val sDip = -0.28f * gaussian(p, 0.185f, 0.0085f)
        val tWave = 0.30f * gaussian(p, 0.365f, 0.036f)
        return (pWave + qDip + rPeak + sDip + tWave).coerceIn(-0.40f, 1.08f)
    }

    private fun gaussian(x: Float, center: Float, width: Float): Float {
        val d = (x - center) / width
        return exp(-(d * d).pow(1f))
    }

    companion object {
        const val ECG_BIN_DT = 0.005f
        const val ECG_HISTORY_SIZE = 440

        private const val REST_BPM = 72f
        private const val MAX_BPM = 180f
        private const val REST_ATRIAL_SYSTOLE_SEC = 0.110f
        private const val REST_VENT_SYSTOLE_SEC = 0.270f
        private const val REST_DIASTOLE_SEC = (60f / REST_BPM) - (REST_ATRIAL_SYSTOLE_SEC + REST_VENT_SYSTOLE_SEC)

        fun computePhaseDurations(bpm: Int): PhaseDurationsSec {
            val clamped = bpm.coerceIn(30, 180).toFloat()
            val period = 60f / clamped
            // At or below normal resting BPM (<= 72), systole durations remain fixed at normal values
            // and diastole absorbs the entire increase in cycle period.
            // Above 72 BPM, systole shortens only modestly (~36% max at 180 BPM) while diastole shortens steeply (~80%).
            val load = ((clamped - REST_BPM) / (MAX_BPM - REST_BPM)).coerceIn(0f, 1f)
            val systoleScale = 1f - 0.36f * load.pow(0.85f)
            val atrialSec = REST_ATRIAL_SYSTOLE_SEC * systoleScale
            val ventSec = REST_VENT_SYSTOLE_SEC * systoleScale
            val diastoleSec = (period - atrialSec - ventSec).coerceAtLeast(0.060f)
            return PhaseDurationsSec(
                periodSec = period,
                atrialSystoleSec = atrialSec,
                ventricularSystoleSec = ventSec,
                diastoleSec = diastoleSec
            )
        }
    }
}
