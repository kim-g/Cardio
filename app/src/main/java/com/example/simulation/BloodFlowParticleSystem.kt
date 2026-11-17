package com.example.simulation

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Simulates two physiological blood circuits (Venous & Arterial) along Catmull-Rom 3D splines
 * with dense volumetric fluid droplets and velocity-stretched tracer particles.
 */
class BloodFlowParticleSystem {

    private enum class PathBranch {
        SVC_TO_LEFT_PA,
        IVC_TO_RIGHT_PA,
        LEFT_PV_TO_DESC_AORTA,
        RIGHT_PV_TO_ARCH_BRANCHES
    }

    private class Particle(
        val branch: PathBranch,
        var t: Float,               // spline progress [0..1)
        val radialOffset: Float,    // 0..1 across vessel/chamber radius
        val radialAngle: Float,
        val swirlSpeed: Float,
        val sizeScale: Float,
        val isBodyBlob: Boolean,    // true = dense volumetric liquid body, false = bright fast tracer
        var currentSpeed: Float = 0.1f
    )

    private val svcToLeftPaSpline = listOf(
        Vec3(-0.54f, 1.28f, -0.04f),
        Vec3(-0.54f, 0.78f, -0.03f),
        Vec3(-0.54f, 0.38f, 0.00f),
        Vec3(-0.38f, 0.10f, 0.10f),
        Vec3(-0.22f, -0.24f, 0.24f),
        Vec3(0.06f, -0.58f, 0.33f),
        Vec3(-0.10f, -0.05f, 0.33f),
        Vec3(-0.06f, 0.36f, 0.34f),
        Vec3(-0.01f, 0.66f, 0.18f),
        Vec3(0.04f, 0.84f, -0.05f),
        Vec3(0.42f, 0.86f, -0.16f),
        Vec3(0.88f, 0.86f, -0.26f)
    )

    private val ivcToRightPaSpline = listOf(
        Vec3(-0.55f, -0.94f, -0.22f),
        Vec3(-0.54f, -0.30f, -0.16f),
        Vec3(-0.52f, 0.26f, -0.04f),
        Vec3(-0.38f, 0.10f, 0.10f),
        Vec3(-0.22f, -0.24f, 0.24f),
        Vec3(0.02f, -0.52f, 0.32f),
        Vec3(-0.10f, -0.04f, 0.33f),
        Vec3(-0.06f, 0.36f, 0.34f),
        Vec3(0.00f, 0.66f, 0.18f),
        Vec3(0.02f, 0.84f, -0.06f),
        Vec3(-0.42f, 0.82f, -0.16f),
        Vec3(-0.86f, 0.80f, -0.22f)
    )

    private val leftPvToDescAortaSpline = listOf(
        Vec3(0.96f, 0.56f, -0.34f),
        Vec3(0.56f, 0.48f, -0.31f),
        Vec3(0.32f, 0.42f, -0.26f),
        Vec3(0.24f, 0.18f, -0.12f),
        Vec3(0.28f, -0.24f, 0.04f),
        Vec3(0.42f, -0.82f, 0.20f),
        Vec3(0.22f, -0.24f, 0.05f),
        Vec3(0.08f, 0.24f, 0.02f),
        Vec3(-0.08f, 0.66f, 0.08f),
        Vec3(0.04f, 1.10f, -0.10f),
        Vec3(0.22f, 0.90f, -0.34f),
        Vec3(0.20f, 0.00f, -0.42f),
        Vec3(0.20f, -1.04f, -0.42f)
    )

    private val rightPvToArchBranchSpline = listOf(
        Vec3(-0.20f, 0.44f, -0.44f),
        Vec3(0.08f, 0.44f, -0.36f),
        Vec3(0.32f, 0.42f, -0.26f),
        Vec3(0.24f, 0.18f, -0.12f),
        Vec3(0.28f, -0.24f, 0.04f),
        Vec3(0.40f, -0.78f, 0.18f),
        Vec3(0.20f, -0.20f, 0.04f),
        Vec3(0.08f, 0.24f, 0.02f),
        Vec3(-0.08f, 0.66f, 0.08f),
        Vec3(-0.04f, 1.02f, -0.02f),
        Vec3(0.06f, 1.24f, -0.10f),
        Vec3(0.06f, 1.46f, -0.10f)
    )

    private val particles = mutableListOf<Particle>()
    val particleCount: Int get() = particles.size
    val vertexData: FloatArray

    private var simClock = 0f

    init {
        val rng = Random(42)
        // Dense liquid volume: 2,200 particles across both circuits
        val branchCounts = listOf(
            PathBranch.SVC_TO_LEFT_PA to 520,
            PathBranch.IVC_TO_RIGHT_PA to 520,
            PathBranch.LEFT_PV_TO_DESC_AORTA to 680,
            PathBranch.RIGHT_PV_TO_ARCH_BRANCHES to 480
        )
        branchCounts.forEach { (branch, count) ->
            repeat(count) { i ->
                val isBody = (i % 3 != 0) // 2/3 dense liquid body blobs, 1/3 bright fast tracers
                particles.add(
                    Particle(
                        branch = branch,
                        t = (i.toFloat() / count + rng.nextFloat() * 0.015f) % 1f,
                        radialOffset = if (isBody) 0.04f + 0.86f * rng.nextFloat() else 0.10f + 0.78f * rng.nextFloat(),
                        radialAngle = rng.nextFloat() * 2f * PI.toFloat(),
                        swirlSpeed = (if (rng.nextBoolean()) 1f else -1f) * (1.0f + rng.nextFloat() * 2.5f),
                        sizeScale = if (isBody) 1.35f + rng.nextFloat() * 0.75f else 0.75f + rng.nextFloat() * 0.45f,
                        isBodyBlob = isBody
                    )
                )
            }
        }
        vertexData = FloatArray(particles.size * 6 * 8)
    }

    fun update(
        dtSeconds: Float,
        cycle: CardiacCycleState,
        isPlaying: Boolean,
        cameraRight: Vec3,
        cameraUp: Vec3
    ) {
        val clampedDt = if (isPlaying) dtSeconds.coerceIn(0f, 0.04f) else 0f
        simClock += clampedDt

        for (idx in particles.indices) {
            val p = particles[idx]
            val spline = splineFor(p.branch)

            val targetSpeed = zoneSpeedFor(p.t, cycle)
            val blend = (clampedDt * 14f).coerceIn(0f, 1f)
            p.currentSpeed = if (isPlaying) {
                p.currentSpeed + (targetSpeed - p.currentSpeed) * blend
            } else {
                p.currentSpeed
            }

            if (isPlaying) {
                p.t += p.currentSpeed * clampedDt
                if (p.t >= 1f) p.t -= 1f
            }

            val basePos = SplineMath.evalCatmullRom(spline, p.t)
            val tangent = SplineMath.evalTangent(spline, p.t)
            val ref = if (kotlin.math.abs(tangent.y) < 0.9f) Vec3(0f, 1f, 0f) else Vec3(1f, 0f, 0f)
            val normA = tangent.cross(ref).normalized()
            val normB = tangent.cross(normA).normalized()

            val tubeRadius = chamberRadiusAt(p.t, cycle) * p.radialOffset
            val swirlTheta = p.radialAngle + simClock * p.swirlSpeed
            val pos = basePos + (normA * cos(swirlTheta) + normB * sin(swirlTheta)) * tubeRadius

            val speedNorm = (p.currentSpeed / 0.65f).coerceIn(0f, 1.4f)
            val halfWidth = (if (p.isBodyBlob) 0.032f else 0.021f) * p.sizeScale
            val stretch = if (p.isBodyBlob) (1.35f + 1.85f * speedNorm) else (1.30f + 2.95f * speedNorm)
            val halfLength = halfWidth * stretch

            val dir = tangent
            var side = dir.cross(cameraRight.cross(cameraUp)).normalized()
            if (side.length() < 0.01f) side = cameraRight

            val v0 = pos - dir * halfLength - side * halfWidth
            val v1 = pos + dir * halfLength - side * halfWidth
            val v2 = pos + dir * halfLength + side * halfWidth
            val v3 = pos - dir * halfLength + side * halfWidth

            val isArterial = if (p.branch == PathBranch.LEFT_PV_TO_DESC_AORTA ||
                p.branch == PathBranch.RIGHT_PV_TO_ARCH_BRANCHES
            ) 1f else 0f

            val edgeFade = (SplineMath.smoothstep(0f, 0.05f, p.t) *
                (1f - SplineMath.smoothstep(0.93f, 1.0f, p.t))).coerceIn(0f, 1f)
            val densityAlpha = if (p.isBodyBlob) edgeFade * 0.56f else edgeFade * 0.92f

            writeQuad(
                index = idx,
                v0 = v0, v1 = v1, v2 = v2, v3 = v3,
                isArterial = isArterial,
                speedNorm = speedNorm.coerceIn(0f, 1f),
                alpha = densityAlpha
            )
        }
    }

    private fun zoneSpeedFor(t: Float, cycle: CardiacCycleState): Float {
        return when {
            t < 0.16f -> cycle.venousInflowSpeed
            t < 0.27f -> cycle.atrialPushSpeed
            t < 0.34f -> cycle.avValveSpeed
            t < 0.56f -> cycle.ventricularPushSpeed
            t < 0.64f -> cycle.semilunarValveSpeed
            else -> cycle.arterialOutflowSpeed
        }
    }

    private fun chamberRadiusAt(t: Float, cycle: CardiacCycleState): Float {
        return when {
            t < 0.16f -> 0.075f
            t < 0.27f -> 0.165f * cycle.atrialScale
            t < 0.34f -> 0.068f * (0.45f + 0.55f * cycle.avValveOpen)
            t < 0.56f -> 0.185f * cycle.ventricularScale
            t < 0.64f -> 0.064f * (0.45f + 0.55f * cycle.semilunarValveOpen)
            else -> 0.076f * (1f + cycle.vesselPulse)
        }
    }

    private fun splineFor(branch: PathBranch): List<Vec3> = when (branch) {
        PathBranch.SVC_TO_LEFT_PA -> svcToLeftPaSpline
        PathBranch.IVC_TO_RIGHT_PA -> ivcToRightPaSpline
        PathBranch.LEFT_PV_TO_DESC_AORTA -> leftPvToDescAortaSpline
        PathBranch.RIGHT_PV_TO_ARCH_BRANCHES -> rightPvToArchBranchSpline
    }

    private fun writeQuad(
        index: Int,
        v0: Vec3, v1: Vec3, v2: Vec3, v3: Vec3,
        isArterial: Float,
        speedNorm: Float,
        alpha: Float
    ) {
        val base = index * 48
        putVert(base + 0, v0, 0f, 0f, isArterial, speedNorm, alpha)
        putVert(base + 8, v1, 1f, 0f, isArterial, speedNorm, alpha)
        putVert(base + 16, v2, 1f, 1f, isArterial, speedNorm, alpha)

        putVert(base + 24, v0, 0f, 0f, isArterial, speedNorm, alpha)
        putVert(base + 32, v2, 1f, 1f, isArterial, speedNorm, alpha)
        putVert(base + 40, v3, 0f, 1f, isArterial, speedNorm, alpha)
    }

    private fun putVert(
        offset: Int,
        v: Vec3,
        u: Float,
        w: Float,
        isArterial: Float,
        speedNorm: Float,
        alpha: Float
    ) {
        vertexData[offset + 0] = v.x
        vertexData[offset + 1] = v.y
        vertexData[offset + 2] = v.z
        vertexData[offset + 3] = u
        vertexData[offset + 4] = w
        vertexData[offset + 5] = isArterial
        vertexData[offset + 6] = speedNorm
        vertexData[offset + 7] = alpha
    }
}
