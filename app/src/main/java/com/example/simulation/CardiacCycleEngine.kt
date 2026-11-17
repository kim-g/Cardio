package com.example.simulation

import com.example.model.CardiacPhase
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * Physiological cardiac cycle state evaluated every frame.
 *
 * Cycle timeline [0.0 .. 1.0):
 *  - 0.00 .. 0.14 : Atrial Systole (P wave) — atria contract, AV valves wide open, booster filling into ventricles
 *  - 0.14 .. 0.44 : Ventricular Systole (QRS -> T wave) — AV valves snap shut, ventricles contract & twist,
 *                   semilunar valves burst open, rapid pulsatile ejection into Aorta & Pulmonary Artery
 *  - 0.44 .. 1.00 : Diastole — semilunar valves close, ventricles & atria relax, venous & pulmonary inflow fills atria,
 *                   AV valves reopen for passive ventricular filling
 */
data class CardiacCycleState(
    val phaseNormalized: Float,
    val phaseEnum: CardiacPhase,
    val atrialScale: Float,          // >1.0 when filling in diastole, <1.0 when contracting in atrial systole
    val ventricularScale: Float,     // <1.0 strongly during ventricular systole, >1.0 slightly when filled
    val ventricularTwistRad: Float,  // Systolic apical torsion
    val vesselPulse: Float,          // Elastic Windkessel expansion of Aorta & Pulmonary Trunk
    val avValveOpen: Float,          // 0 = closed, 1 = wide open (Tricuspid & Mitral)
    val semilunarValveOpen: Float,   // 0 = closed, 1 = wide open (Aortic & Pulmonary)
    // Non-uniform zone blood flow speeds (in spline-units / sec at 72 BPM baseline)
    val venousInflowSpeed: Float,    // Vena Cava & Pulmonary Veins -> Atria (highest in Diastole)
    val atrialPushSpeed: Float,      // Inside Atria -> AV Orifice (surges in Atrial Systole!)
    val avValveSpeed: Float,         // Through Tricuspid/Mitral (0.01 when closed, surges in Atrial Systole)
    val ventricularPushSpeed: Float, // Inside Ventricles -> Outflow tract (surges in Ventricular Systole!)
    val semilunarValveSpeed: Float,  // Through Aortic/Pulmonary valves (0.01 when closed, jet in Ventricular Systole)
    val arterialOutflowSpeed: Float, // Aorta & Pulmonary Arteries (high systolic jet + elastic diastolic coast)
    val ecgSample: Float
)

class CardiacCycleEngine {

    private var phase: Float = 0.55f // Start in mid-diastole so user immediately sees filling -> atrial kick -> systolic jet!
    private var ecgTimeAccumulator: Float = 0f
    private val ecgRing = ArrayDeque<Float>(ECG_HISTORY_SIZE)

    init {
        // Pre-populate ECG strip at 72 BPM ending cleanly at initial phase
        val initStepPhase = ECG_BIN_DT * (72f / 60f)
        var pBack = phase - initStepPhase * ECG_HISTORY_SIZE
        while (pBack < 0f) pBack += 1f
        repeat(ECG_HISTORY_SIZE) {
            val nextP = pBack + initStepPhase
            ecgRing.addLast(sampleEcgExtremum(pBack % 1f, nextP % 1f, initStepPhase))
            pBack = nextP % 1f
        }
    }

    fun step(dtSeconds: Float, bpm: Int, isPlaying: Boolean): CardiacCycleState {
        val clampedDt = dtSeconds.coerceIn(0f, 0.05f)
        val beatsPerSecond = bpm.coerceIn(30, 180) / 60f
        val prevPhase = phase
        if (isPlaying) {
            phase = (phase + clampedDt * beatsPerSecond) % 1f

            // Fixed-time horizontal sweep with sub-stepped extremum detection (no averaging, no missed QRS spikes)
            ecgTimeAccumulator += clampedDt
            val phasePerBin = ECG_BIN_DT * beatsPerSecond
            var cursorPhase = (phase - ecgTimeAccumulator * beatsPerSecond)
            while (cursorPhase < 0f) cursorPhase += 1f

            while (ecgTimeAccumulator >= ECG_BIN_DT) {
                val binEndPhase = (cursorPhase + phasePerBin) % 1f
                val extremum = sampleEcgExtremum(cursorPhase, binEndPhase, phasePerBin)
                if (ecgRing.size >= ECG_HISTORY_SIZE) {
                    ecgRing.removeFirst()
                }
                ecgRing.addLast(extremum)
                cursorPhase = binEndPhase
                ecgTimeAccumulator -= ECG_BIN_DT
            }
        }

        val p = phase

        val phaseEnum = when {
            p < 0.14f -> CardiacPhase.ATRIAL_SYSTOLE
            p < 0.44f -> CardiacPhase.VENTRICULAR_SYSTOLE
            else -> CardiacPhase.DIASTOLE
        }

        // 1. Atrial contraction curve (peaks around p = 0.07) & diastolic filling expansion (0.44..0.95)
        val atrialContract = gaussian(p, center = 0.075f, width = 0.045f)
        val atrialFill = SplineMath.smoothstep(0.35f, 0.78f, p) * (1f - SplineMath.smoothstep(0.95f, 1.0f, p))
        val atrialScale = 1.0f - 0.17f * atrialContract + 0.08f * atrialFill

        // 2. Ventricular contraction curve (isovolumetric onset at 0.14, peak ejection 0.24, ends ~0.43)
        val ventRise = SplineMath.smoothstep(0.14f, 0.23f, p)
        val ventFall = 1f - SplineMath.smoothstep(0.34f, 0.46f, p)
        val ventContract = (ventRise * ventFall).coerceIn(0f, 1f)
        val ventFill = SplineMath.smoothstep(0.50f, 0.92f, p) * (1f - SplineMath.smoothstep(0.12f, 0.16f, p))
        val ventricularScale = 1.0f - 0.16f * ventContract + 0.05f * ventFill
        val ventricularTwistRad = 0.12f * ventContract

        // 3. Great vessel elastic Windkessel pulse (lags ventricular ejection slightly)
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

        // 5. Zone-specific non-uniform blood flow speeds (scaled smoothly with BPM)
        val rateFactor = (bpm / 72f).coerceIn(0.6f, 2.2f)

        val venousInflowSpeed = (0.11f + 0.22f * SplineMath.smoothstep(0.32f, 0.72f, p) *
            (1f - 0.65f * atrialContract)) * rateFactor

        val atrialPushSpeed = (0.06f + 0.34f * atrialContract + 0.10f * avOpenDiastole) * rateFactor

        val avValveSpeed = if (avValveOpen < 0.05f) {
            0.006f
        } else {
            (0.08f + 0.40f * atrialContract + 0.12f * avOpenDiastole) * avValveOpen * rateFactor
        }

        val ejectionCurve = (SplineMath.smoothstep(0.16f, 0.23f, p) *
            (1f - SplineMath.smoothstep(0.34f, 0.43f, p))).coerceIn(0f, 1f)
        val ventricularPushSpeed = (0.05f + 0.14f * atrialContract + 0.46f * ejectionCurve) * rateFactor

        val semilunarValveSpeed = if (semilunarValveOpen < 0.05f) {
            0.005f
        } else {
            (0.12f + 0.68f * ejectionCurve) * semilunarValveOpen * rateFactor
        }

        val diastolicCoast = 0.11f * exp(-((p - 0.42f).coerceAtLeast(0f)) * 2.2f)
        val arterialOutflowSpeed = (0.08f + 0.62f * ejectionCurve + diastolicCoast) * rateFactor

        val ecg = if (isPlaying) {
            sampleEcgExtremum(prevPhase, p, (p - prevPhase).let { if (it < 0f) it + 1f else it })
        } else {
            computeEcgWave(p)
        }

        return CardiacCycleState(
            phaseNormalized = p,
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
     * Sub-samples the phase interval and returns the extremum (largest absolute deviation
     * from the isoelectric baseline) instead of averaging, preserving sharp Q, R, and S spikes.
     */
    private fun sampleEcgExtremum(startPhase: Float, @Suppress("UNUSED_PARAMETER") endPhase: Float, span: Float): Float {
        val steps = 16
        var best = computeEcgWave(startPhase)
        var bestAbs = kotlin.math.abs(best)
        for (i in 1..steps) {
            val subP = (startPhase + span * (i.toFloat() / steps)) % 1f
            val v = computeEcgWave(subP)
            val a = kotlin.math.abs(v)
            if (a > bestAbs) {
                best = v
                bestAbs = a
            }
        }
        return best
    }

    /**
     * Synthesizes a clinical P-QRS-T waveform with a clean flat isoelectric baseline:
     *  - P wave: 0.02..0.10 (Atrial depolarization -> Atrial Systole)
     *  - Q dip:  0.144
     *  - R peak: 0.164 (Ventricular depolarization -> Ventricular Systole onset)
     *  - S dip:  0.185
     *  - T wave: 0.31..0.42 (Ventricular repolarization)
     */
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
        const val ECG_BIN_DT = 0.005f      // 5 ms per sample bin (200 Hz effective ECG rate)
        const val ECG_HISTORY_SIZE = 440   // 2.2 seconds fixed horizontal sweep
    }
}
