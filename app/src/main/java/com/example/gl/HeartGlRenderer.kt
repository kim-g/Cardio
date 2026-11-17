package com.example.gl

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.model.AnatomyStructureId
import com.example.model.ProjectedLabel
import com.example.model.TelemetrySnapshot
import com.example.model.TransparencyMode
import com.example.simulation.AnatomicalMeshBuilder
import com.example.simulation.BloodFlowParticleSystem
import com.example.simulation.CardiacCycleEngine
import com.example.simulation.HeartMeshData
import com.example.simulation.Vec3
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

class HeartGlRenderer(
    private val onTelemetryUpdate: (TelemetrySnapshot) -> Unit
) : GLSurfaceView.Renderer {

    @Volatile var bpm: Int = 72
    @Volatile var myocardiumOpacity: Float = 0.34f
    @Volatile var transparencyMode: TransparencyMode = TransparencyMode.TRANSLUCENT
    @Volatile var isPlaying: Boolean = true
    @Volatile var selectedRegionCode: Float = 0f

    // Camera orbital state (accessed from touch thread & GL thread with synchronized lock)
    private val cameraLock = Any()
    private var yawDeg = -18f
    private var pitchDeg = 8f
    private var distance = 4.15f
    private var velYaw = 0f
    private var velPitch = 0f

    private var viewportWidth = 1
    private var viewportHeight = 1

    private val cycleEngine = CardiacCycleEngine()
    private val particleSystem = BloodFlowParticleSystem()
    private lateinit var meshData: HeartMeshData

    private var lastFrameNs = 0L
    private var lastTelemetryPostNs = 0L

    // GL Programs
    private var backdropProgram = 0
    private var heartProgram = 0
    private var valveProgram = 0
    private var particleProgram = 0

    // Buffers
    private var backdropVbo = 0
    private var heartVbo = 0
    private var heartIbo = 0
    private var particleVbo = 0
    private val valveVbos = mutableListOf<Int>()
    private lateinit var particleFloatBuffer: FloatBuffer

    // Matrices
    private val modelMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)
    private val viewProjMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    // Uniform locations (Heart)
    private var hModelLoc = 0
    private var hViewProjLoc = 0
    private var hCamPosLoc = 0
    private var hAtrialScaleLoc = 0
    private var hVentScaleLoc = 0
    private var hVentTwistLoc = 0
    private var hVesselPulseLoc = 0
    private var hOpacityLoc = 0
    private var hCutawayLoc = 0
    private var hSelectedLoc = 0
    private var hIsOpaqueLoc = 0

    // Uniform locations (Valve)
    private var vModelLoc = 0
    private var vViewProjLoc = 0
    private var vCamPosLoc = 0
    private var vCenterLoc = 0
    private var vAxisLoc = 0
    private var vOpenLoc = 0
    private var vRadiusLoc = 0

    // Uniform locations (Particle)
    private var pModelLoc = 0
    private var pViewProjLoc = 0

    fun addRotationDelta(dYaw: Float, dPitch: Float) {
        synchronized(cameraLock) {
            yawDeg += dYaw
            pitchDeg = (pitchDeg + dPitch).coerceIn(-75f, 75f)
            velYaw = dYaw * 0.55f
            velPitch = dPitch * 0.55f
        }
    }

    fun addZoomFactor(scaleFactor: Float) {
        synchronized(cameraLock) {
            distance = (distance / scaleFactor).coerceIn(2.3f, 6.8f)
        }
    }

    fun resetCamera() {
        synchronized(cameraLock) {
            yawDeg = -18f
            pitchDeg = 8f
            distance = 4.15f
            velYaw = 0f
            velPitch = 0f
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)

        meshData = AnatomicalMeshBuilder.build()

        backdropProgram = HeartShaders.compileProgram(
            HeartShaders.BACKDROP_VERTEX,
            HeartShaders.BACKDROP_FRAGMENT
        )
        heartProgram = HeartShaders.compileProgram(
            HeartShaders.HEART_VERTEX,
            HeartShaders.HEART_FRAGMENT
        )
        valveProgram = HeartShaders.compileProgram(
            HeartShaders.VALVE_VERTEX,
            HeartShaders.VALVE_FRAGMENT
        )
        particleProgram = HeartShaders.compileProgram(
            HeartShaders.PARTICLE_VERTEX,
            HeartShaders.PARTICLE_FRAGMENT
        )

        cacheUniformLocations()
        uploadStaticMeshes()

        particleFloatBuffer = ByteBuffer
            .allocateDirect(particleSystem.vertexData.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        val pBuf = IntArray(1)
        GLES30.glGenBuffers(1, pBuf, 0)
        particleVbo = pBuf[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, particleVbo)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            particleSystem.vertexData.size * 4,
            null,
            GLES30.GL_DYNAMIC_DRAW
        )

        lastFrameNs = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
        GLES30.glViewport(0, 0, viewportWidth, viewportHeight)
        val aspect = viewportWidth.toFloat() / viewportHeight.toFloat()
        Matrix.perspectiveM(projMatrix, 0, 42f, aspect, 0.2f, 30f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - lastFrameNs) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastFrameNs = now

        // Apply orbital inertia
        val yaw: Float
        val pitch: Float
        val dist: Float
        synchronized(cameraLock) {
            yawDeg += velYaw
            pitchDeg = (pitchDeg + velPitch).coerceIn(-75f, 75f)
            velYaw *= 0.90f
            velPitch *= 0.90f
            yaw = yawDeg
            pitch = pitchDeg
            dist = distance
        }

        val yawRad = Math.toRadians(yaw.toDouble()).toFloat()
        val pitchRad = Math.toRadians(pitch.toDouble()).toFloat()
        val camX = dist * cos(pitchRad) * sin(yawRad)
        val camY = dist * sin(pitchRad) + 0.08f
        val camZ = dist * cos(pitchRad) * cos(yawRad)

        Matrix.setLookAtM(viewMatrix, 0, camX, camY, camZ, 0f, 0.08f, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(viewProjMatrix, 0, projMatrix, 0, viewMatrix, 0)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, viewProjMatrix, 0, modelMatrix, 0)

        val cycle = cycleEngine.step(dt, bpm, isPlaying)

        val camDir = Vec3(-camX, 0.08f - camY, -camZ).normalized()
        val camRight = camDir.cross(Vec3(0f, 1f, 0f)).normalized()
        val camUp = camRight.cross(camDir).normalized()
        particleSystem.update(dt, cycle, isPlaying, camRight, camUp)

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        // 1. Studio radial backdrop
        drawBackdrop()

        // 2. Opaque interior pass (Septum in cutaway + Opaque myocardium if solid)
        val opacity = myocardiumOpacity
        val cutaway = transparencyMode.cutawayAmount
        if (opacity >= 0.96f && cutaway < 0.5f) {
            GLES30.glDisable(GLES30.GL_BLEND)
            GLES30.glDepthMask(true)
            GLES30.glEnable(GLES30.GL_CULL_FACE)
            GLES30.glCullFace(GLES30.GL_BACK)
            drawHeartMesh(cycle, camX, camY, camZ, opacity, cutaway, isOpaquePass = 1f)
            // Also show blood flowing into/out of open vessel mouths
            drawParticles()
        } else {
            // Draw opaque animated cardiac valves inside first
            drawValves(cycle, camX, camY, camZ)

            // Draw luminous blood particles inside chambers & vessels
            drawParticles()

            // Two-pass back-to-front translucent myocardium + cutaway walls
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            GLES30.glDepthMask(false)

            if (cutaway > 0.5f) {
                // In cutaway mode, disable face culling so inner endocardium of posterior wall renders cleanly
                GLES30.glDisable(GLES30.GL_CULL_FACE)
                drawHeartMesh(cycle, camX, camY, camZ, opacity, cutaway, isOpaquePass = 0f)
            } else {
                GLES30.glEnable(GLES30.GL_CULL_FACE)
                // Back faces first
                GLES30.glCullFace(GLES30.GL_FRONT)
                drawHeartMesh(cycle, camX, camY, camZ, opacity, cutaway, isOpaquePass = 0f)
                // Front faces second with Fresnel rim
                GLES30.glCullFace(GLES30.GL_BACK)
                drawHeartMesh(cycle, camX, camY, camZ, opacity, cutaway, isOpaquePass = 0f)
            }

            GLES30.glDepthMask(true)
            GLES30.glDisable(GLES30.GL_CULL_FACE)
            GLES30.glDisable(GLES30.GL_BLEND)
        }

        // Emit telemetry & projected 2D label coordinates at ~30 Hz
        if (now - lastTelemetryPostNs >= 32_000_000L) {
            lastTelemetryPostNs = now
            val labels = projectLabels()
            onTelemetryUpdate(
                TelemetrySnapshot(
                    phase = cycle.phaseEnum,
                    cycleProgress = cycle.phaseNormalized,
                    ecgHistory = cycleEngine.getEcgHistorySnapshot(),
                    atrialPressureNorm = (1.15f - cycle.atrialScale) * 4f,
                    ventricularPressureNorm = (1.08f - cycle.ventricularScale) * 4f,
                    projectedLabels = labels
                )
            )
        }
    }

    private fun drawBackdrop() {
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)
        GLES30.glUseProgram(backdropProgram)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, backdropVbo)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDepthMask(true)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    private fun drawValves(
        cycle: com.example.simulation.CardiacCycleState,
        camX: Float,
        camY: Float,
        camZ: Float
    ) {
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(true)

        GLES30.glUseProgram(valveProgram)
        GLES30.glUniformMatrix4fv(vModelLoc, 1, false, modelMatrix, 0)
        GLES30.glUniformMatrix4fv(vViewProjLoc, 1, false, viewProjMatrix, 0)
        GLES30.glUniform3f(vCamPosLoc, camX, camY, camZ)

        val strideBytes = 8 * 4
        meshData.valveMeshes.forEachIndexed { idx, valve ->
            val openAmount = if (valve.isAvValve) cycle.avValveOpen else cycle.semilunarValveOpen
            GLES30.glUniform3f(vCenterLoc, valve.center.x, valve.center.y, valve.center.z)
            GLES30.glUniform3f(vAxisLoc, valve.flowAxis.x, valve.flowAxis.y, valve.flowAxis.z)
            GLES30.glUniform1f(vOpenLoc, openAmount)
            GLES30.glUniform1f(vRadiusLoc, valve.radius)

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, valveVbos[idx])
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, strideBytes, 0)
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, strideBytes, 12)
            GLES30.glEnableVertexAttribArray(2)
            GLES30.glVertexAttribPointer(2, 1, GLES30.GL_FLOAT, false, strideBytes, 24)
            GLES30.glEnableVertexAttribArray(3)
            GLES30.glVertexAttribPointer(3, 1, GLES30.GL_FLOAT, false, strideBytes, 28)

            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, valve.vertices.size / 8)
        }
        for (a in 0..3) GLES30.glDisableVertexAttribArray(a)
    }

    private fun drawParticles() {
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)

        particleFloatBuffer.clear()
        particleFloatBuffer.put(particleSystem.vertexData)
        particleFloatBuffer.position(0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, particleVbo)
        GLES30.glBufferSubData(
            GLES30.GL_ARRAY_BUFFER,
            0,
            particleSystem.vertexData.size * 4,
            particleFloatBuffer
        )

        GLES30.glUseProgram(particleProgram)
        GLES30.glUniformMatrix4fv(pModelLoc, 1, false, modelMatrix, 0)
        GLES30.glUniformMatrix4fv(pViewProjLoc, 1, false, viewProjMatrix, 0)

        val strideBytes = 8 * 4
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, strideBytes, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, strideBytes, 12)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 1, GLES30.GL_FLOAT, false, strideBytes, 20)
        GLES30.glEnableVertexAttribArray(3)
        GLES30.glVertexAttribPointer(3, 1, GLES30.GL_FLOAT, false, strideBytes, 24)
        GLES30.glEnableVertexAttribArray(4)
        GLES30.glVertexAttribPointer(4, 1, GLES30.GL_FLOAT, false, strideBytes, 28)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, particleSystem.particleCount * 6)
        for (a in 0..4) GLES30.glDisableVertexAttribArray(a)

        GLES30.glDepthMask(true)
    }

    private fun drawHeartMesh(
        cycle: com.example.simulation.CardiacCycleState,
        camX: Float,
        camY: Float,
        camZ: Float,
        opacity: Float,
        cutaway: Float,
        isOpaquePass: Float
    ) {
        GLES30.glUseProgram(heartProgram)
        GLES30.glUniformMatrix4fv(hModelLoc, 1, false, modelMatrix, 0)
        GLES30.glUniformMatrix4fv(hViewProjLoc, 1, false, viewProjMatrix, 0)
        GLES30.glUniform3f(hCamPosLoc, camX, camY, camZ)
        GLES30.glUniform1f(hAtrialScaleLoc, cycle.atrialScale)
        GLES30.glUniform1f(hVentScaleLoc, cycle.ventricularScale)
        GLES30.glUniform1f(hVentTwistLoc, cycle.ventricularTwistRad)
        GLES30.glUniform1f(hVesselPulseLoc, cycle.vesselPulse)
        GLES30.glUniform1f(hOpacityLoc, opacity)
        GLES30.glUniform1f(hCutawayLoc, cutaway)
        GLES30.glUniform1f(hSelectedLoc, selectedRegionCode)
        GLES30.glUniform1f(hIsOpaqueLoc, isOpaquePass)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, heartVbo)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, heartIbo)

        val strideBytes = 10 * 4
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, strideBytes, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, strideBytes, 12)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 1, GLES30.GL_FLOAT, false, strideBytes, 24)
        GLES30.glEnableVertexAttribArray(3)
        GLES30.glVertexAttribPointer(3, 3, GLES30.GL_FLOAT, false, strideBytes, 28)

        GLES30.glDrawElements(
            GLES30.GL_TRIANGLES,
            meshData.heartIndices.size,
            GLES30.GL_UNSIGNED_INT,
            0
        )

        for (a in 0..3) GLES30.glDisableVertexAttribArray(a)
    }

    private fun projectLabels(): List<ProjectedLabel> {
        val inVec = FloatArray(4)
        val outVec = FloatArray(4)
        val result = ArrayList<ProjectedLabel>(AnatomyStructureId.entries.size)
        for (struct in AnatomyStructureId.entries) {
            inVec[0] = struct.anchorX
            inVec[1] = struct.anchorY
            inVec[2] = struct.anchorZ
            inVec[3] = 1f
            Matrix.multiplyMV(outVec, 0, mvpMatrix, 0, inVec, 0)
            val w = outVec[3]
            if (w > 0.05f) {
                val ndcX = outVec[0] / w
                val ndcY = outVec[1] / w
                val ndcZ = outVec[2] / w
                val normX = (ndcX + 1f) * 0.5f
                val normY = (1f - ndcY) * 0.5f
                if (normX in 0.04f..0.96f && normY in 0.08f..0.90f) {
                    // Valves only show prominently when translucent or cutaway
                    val isValve = struct.regionCode == 10
                    val baseAlpha = if (isValve && transparencyMode == TransparencyMode.SOLID) 0.25f else 0.95f
                    result.add(
                        ProjectedLabel(
                            id = struct,
                            normX = normX,
                            normY = normY,
                            ndcDepth = ndcZ,
                            alpha = baseAlpha
                        )
                    )
                }
            }
        }
        return result
    }

    private fun cacheUniformLocations() {
        hModelLoc = GLES30.glGetUniformLocation(heartProgram, "uModelMatrix")
        hViewProjLoc = GLES30.glGetUniformLocation(heartProgram, "uViewProjMatrix")
        hCamPosLoc = GLES30.glGetUniformLocation(heartProgram, "uCameraPos")
        hAtrialScaleLoc = GLES30.glGetUniformLocation(heartProgram, "uAtrialScale")
        hVentScaleLoc = GLES30.glGetUniformLocation(heartProgram, "uVentricularScale")
        hVentTwistLoc = GLES30.glGetUniformLocation(heartProgram, "uVentricularTwist")
        hVesselPulseLoc = GLES30.glGetUniformLocation(heartProgram, "uVesselPulse")
        hOpacityLoc = GLES30.glGetUniformLocation(heartProgram, "uOpacity")
        hCutawayLoc = GLES30.glGetUniformLocation(heartProgram, "uCutawayAmount")
        hSelectedLoc = GLES30.glGetUniformLocation(heartProgram, "uSelectedRegion")
        hIsOpaqueLoc = GLES30.glGetUniformLocation(heartProgram, "uIsOpaquePass")

        vModelLoc = GLES30.glGetUniformLocation(valveProgram, "uModelMatrix")
        vViewProjLoc = GLES30.glGetUniformLocation(valveProgram, "uViewProjMatrix")
        vCamPosLoc = GLES30.glGetUniformLocation(valveProgram, "uCameraPos")
        vCenterLoc = GLES30.glGetUniformLocation(valveProgram, "uValveCenter")
        vAxisLoc = GLES30.glGetUniformLocation(valveProgram, "uFlowAxis")
        vOpenLoc = GLES30.glGetUniformLocation(valveProgram, "uOpenAmount")
        vRadiusLoc = GLES30.glGetUniformLocation(valveProgram, "uValveRadius")

        pModelLoc = GLES30.glGetUniformLocation(particleProgram, "uModelMatrix")
        pViewProjLoc = GLES30.glGetUniformLocation(particleProgram, "uViewProjMatrix")
    }

    private fun uploadStaticMeshes() {
        val quad = floatArrayOf(
            -1f, -1f, 1f, -1f, 1f, 1f,
            -1f, -1f, 1f, 1f, -1f, 1f
        )
        val quadBuf = ByteBuffer.allocateDirect(quad.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().put(quad).apply { position(0) }
        val bId = IntArray(1)
        GLES30.glGenBuffers(1, bId, 0)
        backdropVbo = bId[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, backdropVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, quad.size * 4, quadBuf, GLES30.GL_STATIC_DRAW)

        val vboBuf = ByteBuffer.allocateDirect(meshData.heartVertices.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().put(meshData.heartVertices).apply { position(0) }
        val iboBuf: IntBuffer = ByteBuffer.allocateDirect(meshData.heartIndices.size * 4)
            .order(ByteOrder.nativeOrder()).asIntBuffer().put(meshData.heartIndices).apply { position(0) }

        val ids = IntArray(2)
        GLES30.glGenBuffers(2, ids, 0)
        heartVbo = ids[0]
        heartIbo = ids[1]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, heartVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, meshData.heartVertices.size * 4, vboBuf, GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, heartIbo)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, meshData.heartIndices.size * 4, iboBuf, GLES30.GL_STATIC_DRAW)

        valveVbos.clear()
        val vIds = IntArray(meshData.valveMeshes.size)
        GLES30.glGenBuffers(meshData.valveMeshes.size, vIds, 0)
        meshData.valveMeshes.forEachIndexed { idx, vMesh ->
            valveVbos.add(vIds[idx])
            val fb = ByteBuffer.allocateDirect(vMesh.vertices.size * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer().put(vMesh.vertices).apply { position(0) }
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vIds[idx])
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vMesh.vertices.size * 4, fb, GLES30.GL_STATIC_DRAW)
        }
    }
}
