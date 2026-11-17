package com.example

import com.example.model.CardiacPhase
import com.example.simulation.AnatomicalMeshBuilder
import com.example.simulation.BloodFlowParticleSystem
import com.example.simulation.CardiacCycleEngine
import com.example.simulation.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardiacSimulationTest {

    @Test
    fun cardiacCycle_exhibitsAllPhasesAndNonUniformHemodynamics() {
        val engine = CardiacCycleEngine()
        var atrialAvSpeedSum = 0f
        var atrialSamples = 0
        var ventSemiSpeedSum = 0f
        var ventAvSpeedSum = 0f
        var ventSamples = 0
        var diastoleVenousSpeedSum = 0f
        var diastoleSemiSpeedSum = 0f
        var diastoleSamples = 0

        // Simulate 1.2 seconds at 72 BPM (~1.44 cardiac cycles)
        val dt = 0.01f
        repeat(120) {
            val state = engine.step(dt, bpm = 72, isPlaying = true)
            when (state.phaseEnum) {
                CardiacPhase.ATRIAL_SYSTOLE -> {
                    atrialAvSpeedSum += state.avValveSpeed
                    atrialSamples++
                }
                CardiacPhase.VENTRICULAR_SYSTOLE -> {
                    ventSemiSpeedSum += state.semilunarValveSpeed
                    ventAvSpeedSum += state.avValveSpeed
                    ventSamples++
                }
                CardiacPhase.DIASTOLE -> {
                    diastoleVenousSpeedSum += state.venousInflowSpeed
                    diastoleSemiSpeedSum += state.semilunarValveSpeed
                    diastoleSamples++
                }
            }
        }

        assertTrue("Should observe Atrial Systole", atrialSamples > 0)
        assertTrue("Should observe Ventricular Systole", ventSamples > 0)
        assertTrue("Should observe Diastole", diastoleSamples > 0)

        val avgAtrialAv = atrialAvSpeedSum / atrialSamples
        val avgVentAv = ventAvSpeedSum / ventSamples
        val avgVentSemi = ventSemiSpeedSum / ventSamples
        val avgDiastoleSemi = diastoleSemiSpeedSum / diastoleSamples
        val avgDiastoleVenous = diastoleVenousSpeedSum / diastoleSamples

        // Atrial systole pushes blood through AV valves faster than during ventricular systole (closed)
        assertTrue("AV valve flow must surge in atrial systole ($avgAtrialAv > $avgVentAv)", avgAtrialAv > avgVentAv * 3f)
        // Ventricular systole ejects blood through semilunar valves much faster than diastole (closed)
        assertTrue("Semilunar valve jet must surge in ventricular systole ($avgVentSemi > $avgDiastoleSemi)", avgVentSemi > avgDiastoleSemi * 4f)
        // Diastole steadily fills atria from veins
        assertTrue("Diastole venous inflow should be active ($avgDiastoleVenous)", avgDiastoleVenous > 0.15f)
    }

    @Test
    fun anatomicalMeshBuilder_generatesValidWatertightGeometryAndValves() {
        val mesh = AnatomicalMeshBuilder.build()
        assertTrue("Heart vertices should be substantial", mesh.heartVertices.size > 20_000)
        assertTrue("Heart indices should be triangles", mesh.heartIndices.isNotEmpty() && mesh.heartIndices.size % 3 == 0)
        assertEquals("Should contain 4 cardiac valves", 4, mesh.valveMeshes.size)

        val vertexCount = mesh.heartVertices.size / 10
        for (i in mesh.heartVertices.indices) {
            assertFalse("Heart vertex float at $i should not be NaN", mesh.heartVertices[i].isNaN())
        }
        for (idx in mesh.heartIndices) {
            assertTrue("Index $idx out of bounds ($vertexCount)", idx in 0 until vertexCount)
        }

        // Verify CCW triangle geometric winding matches vertex normals (outward-facing)
        var alignedTriangles = 0
        var nonDegenerateTriangles = 0
        for (t in mesh.heartIndices.indices step 3) {
            val i0 = mesh.heartIndices[t] * 10
            val i1 = mesh.heartIndices[t + 1] * 10
            val i2 = mesh.heartIndices[t + 2] * 10

            val p0 = Vec3(mesh.heartVertices[i0], mesh.heartVertices[i0 + 1], mesh.heartVertices[i0 + 2])
            val p1 = Vec3(mesh.heartVertices[i1], mesh.heartVertices[i1 + 1], mesh.heartVertices[i1 + 2])
            val p2 = Vec3(mesh.heartVertices[i2], mesh.heartVertices[i2 + 1], mesh.heartVertices[i2 + 2])

            val faceNormal = (p1 - p0).cross(p2 - p0)
            if (faceNormal.length() > 1e-7f) {
                nonDegenerateTriangles++
                val vNorm = Vec3(
                    mesh.heartVertices[i0 + 3] + mesh.heartVertices[i1 + 3] + mesh.heartVertices[i2 + 3],
                    mesh.heartVertices[i0 + 4] + mesh.heartVertices[i1 + 4] + mesh.heartVertices[i2 + 4],
                    mesh.heartVertices[i0 + 5] + mesh.heartVertices[i1 + 5] + mesh.heartVertices[i2 + 5]
                )
                if (faceNormal.dot(vNorm) > 0f) {
                    alignedTriangles++
                }
            }
        }
        val alignmentRatio = alignedTriangles.toFloat() / nonDegenerateTriangles.coerceAtLeast(1)
        assertTrue(
            "Outward triangle winding must match outward vertex normals ($alignmentRatio)",
            alignmentRatio > 0.98f
        )

        mesh.valveMeshes.forEach { valve ->
            assertTrue("Valve should have vertices", valve.vertices.isNotEmpty())
            assertEquals("Valve vertices should be multiple of 8 floats", 0, valve.vertices.size % 8)
            for (v in valve.vertices) {
                assertFalse("Valve vertex float should not be NaN", v.isNaN())
            }
        }
    }

    @Test
    fun bloodFlowParticles_updateCleanlyWithoutNaNs() {
        val engine = CardiacCycleEngine()
        val system = BloodFlowParticleSystem()
        assertTrue("Should initialize dense blood particles", system.particleCount >= 2000)

        val camRight = Vec3(1f, 0f, 0f)
        val camUp = Vec3(0f, 1f, 0f)

        repeat(30) {
            val cycle = engine.step(0.016f, bpm = 125, isPlaying = true)
            system.update(0.016f, cycle, isPlaying = true, cameraRight = camRight, cameraUp = camUp)
        }

        for (v in system.vertexData) {
            assertFalse("Particle vertex data should not contain NaN", v.isNaN())
        }
    }

    @Test
    fun ecgTrace_preservesFullExtremumPeaksAcrossBpm() {
        for (testBpm in listOf(45, 72, 165, 180)) {
            val engine = CardiacCycleEngine()
            // Step 1.5 seconds even at coarse 30fps (33ms) frame steps
            repeat(45) {
                engine.step(0.033f, bpm = testBpm, isPlaying = true)
            }
            val history = engine.getEcgHistorySnapshot()
            val maxR = history.maxOrNull() ?: 0f
            val minS = history.minOrNull() ?: 0f
            assertTrue("R-peak at $testBpm BPM must reach full amplitude (>= 0.90, got $maxR)", maxR >= 0.90f)
            assertTrue("S-dip at $testBpm BPM must reach full depth (<= -0.22, got $minS)", minS <= -0.22f)
        }
    }
}
