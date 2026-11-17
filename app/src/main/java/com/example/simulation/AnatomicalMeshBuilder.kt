package com.example.simulation

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

data class HeartMeshData(
    val heartVertices: FloatArray, // stride = 10 floats: pos(3), normal(3), region(1), localDir(3)
    val heartIndices: IntArray,
    val valveMeshes: List<ValveMeshData>
)

data class ValveMeshData(
    val isAvValve: Boolean, // true = Tricuspid/Mitral, false = Pulmonary/Aortic
    val center: Vec3,
    val flowAxis: Vec3,     // unit vector in direction of forward blood flow
    val radius: Float,
    val leafletCount: Int,
    val vertices: FloatArray // stride = 8 floats: pos(3), normal(3), hingeWeight(1), radialAngle(1)
)

object AnatomicalMeshBuilder {

    const val REGION_LV = 1f
    const val REGION_RV = 2f
    const val REGION_RA = 3f
    const val REGION_LA = 4f
    const val REGION_PA = 5f
    const val REGION_AORTA = 6f
    const val REGION_SEPTUM = 7f
    const val REGION_VENA_CAVA = 8f
    const val REGION_PULM_VEINS = 9f

    private class MeshAccumulator {
        private val positions = mutableListOf<Vec3>()
        private val normals = mutableListOf<Vec3>()
        private val regions = mutableListOf<Float>()
        private val localDirs = mutableListOf<Vec3>()
        private val indices = mutableListOf<Int>()

        fun addLoftedTube(
            spine: List<Vec3>,
            rings: Int,
            slices: Int,
            region: Float,
            contractionCenter: Vec3,
            radiusAt: (t: Float, angle: Float) -> Float,
            capStart: Boolean = false,
            capEnd: Boolean = false
        ) {
            val baseIndex = positions.size
            val ringPositions = Array(rings + 1) { Array(slices + 1) { Vec3.ZERO } }

            // Compute parallel-transport frame along the spline to avoid twisting artifacts
            var prevTangent = SplineMath.evalTangent(spine, 0f)
            val initRef = if (kotlin.math.abs(prevTangent.y) < 0.9f) Vec3(0f, 1f, 0f) else Vec3(1f, 0f, 0f)
            var frameNormal = prevTangent.cross(initRef).normalized()
            var frameBinormal = prevTangent.cross(frameNormal).normalized()

            for (i in 0..rings) {
                val t = i.toFloat() / rings
                val center = SplineMath.evalCatmullRom(spine, t)
                val tangent = SplineMath.evalTangent(spine, t)

                // Parallel transport update
                val rotAxis = prevTangent.cross(tangent)
                val rotLen = rotAxis.length()
                if (rotLen > 1e-5f) {
                    val dot = prevTangent.dot(tangent).coerceIn(-1f, 1f)
                    val angle = kotlin.math.acos(dot)
                    frameNormal = rotateAroundAxis(frameNormal, rotAxis / rotLen, angle).normalized()
                    frameBinormal = tangent.cross(frameNormal).normalized()
                }
                prevTangent = tangent

                for (j in 0..slices) {
                    val phi = (j.toFloat() / slices) * 2f * PI.toFloat()
                    val r = radiusAt(t, phi)
                    val offset = (frameNormal * cos(phi) + frameBinormal * sin(phi)) * r
                    ringPositions[i][j] = center + offset
                }
            }

            // Compute smooth normals by finite differences on grid
            for (i in 0..rings) {
                for (j in 0..slices) {
                    val p = ringPositions[i][j]
                    val pNextI = ringPositions[(i + 1).coerceAtMost(rings)][j]
                    val pPrevI = ringPositions[(i - 1).coerceAtLeast(0)][j]
                    val pNextJ = ringPositions[i][(j + 1) % slices]
                    val pPrevJ = ringPositions[i][(j - 1 + slices) % slices]

                    val dI = pNextI - pPrevI
                    val dJ = pNextJ - pPrevJ
                    var n = dJ.cross(dI)
                    if (n.length() < 1e-6f) {
                        n = (p - contractionCenter)
                    }
                    val normal = n.normalized()
                    val localDir = (p - contractionCenter).normalized()

                    positions.add(p)
                    normals.add(normal)
                    regions.add(region)
                    localDirs.add(localDir)
                }
            }

            val stride = slices + 1
            for (i in 0 until rings) {
                for (j in 0 until slices) {
                    val a = baseIndex + i * stride + j
                    val b = baseIndex + (i + 1) * stride + j
                    val c = baseIndex + (i + 1) * stride + (j + 1)
                    val d = baseIndex + i * stride + (j + 1)
                    indices.add(a); indices.add(d); indices.add(b)
                    indices.add(d); indices.add(c); indices.add(b)
                }
            }

            if (capStart) {
                val capCenterIdx = positions.size
                val startCenter = SplineMath.evalCatmullRom(spine, 0f)
                val startNormal = SplineMath.evalTangent(spine, 0f) * -1f
                positions.add(startCenter)
                normals.add(startNormal)
                regions.add(region)
                localDirs.add((startCenter - contractionCenter).normalized())
                for (j in 0 until slices) {
                    indices.add(capCenterIdx)
                    indices.add(baseIndex + j + 1)
                    indices.add(baseIndex + j)
                }
            }

            if (capEnd) {
                val capCenterIdx = positions.size
                val endCenter = SplineMath.evalCatmullRom(spine, 1f)
                val endNormal = SplineMath.evalTangent(spine, 1f)
                positions.add(endCenter)
                normals.add(endNormal)
                regions.add(region)
                localDirs.add((endCenter - contractionCenter).normalized())
                val lastRingOffset = baseIndex + rings * stride
                for (j in 0 until slices) {
                    indices.add(capCenterIdx)
                    indices.add(lastRingOffset + j)
                    indices.add(lastRingOffset + j + 1)
                }
            }
        }

        fun addSculptedEllipsoid(
            center: Vec3,
            radiusX: Float,
            radiusY: Float,
            radiusZ: Float,
            region: Float,
            contractionCenter: Vec3,
            latSteps: Int = 22,
            lonSteps: Int = 28,
            deform: (Vec3, Float, Float) -> Vec3 = { p, _, _ -> p }
        ) {
            val baseIndex = positions.size
            val grid = Array(latSteps + 1) { Array(lonSteps + 1) { Vec3.ZERO } }

            for (i in 0..latSteps) {
                val theta = (i.toFloat() / latSteps) * PI.toFloat()
                val sinT = sin(theta)
                val cosT = cos(theta)
                for (j in 0..lonSteps) {
                    val phi = (j.toFloat() / lonSteps) * 2f * PI.toFloat()
                    val raw = Vec3(
                        center.x + radiusX * sinT * cos(phi),
                        center.y + radiusY * cosT,
                        center.z + radiusZ * sinT * sin(phi)
                    )
                    grid[i][j] = deform(raw, theta, phi)
                }
            }

            for (i in 0..latSteps) {
                for (j in 0..lonSteps) {
                    val p = grid[i][j]
                    val pNextI = grid[(i + 1).coerceAtMost(latSteps)][j]
                    val pPrevI = grid[(i - 1).coerceAtLeast(0)][j]
                    val pNextJ = grid[i][(j + 1) % lonSteps]
                    val pPrevJ = grid[i][(j - 1 + lonSteps) % lonSteps]

                    val dI = pNextI - pPrevI
                    val dJ = pNextJ - pPrevJ
                    var n = dJ.cross(dI)
                    if (n.length() < 1e-6f) n = p - center
                    val normal = n.normalized()

                    positions.add(p)
                    normals.add(normal)
                    regions.add(region)
                    localDirs.add((p - contractionCenter).normalized())
                }
            }

            val stride = lonSteps + 1
            for (i in 0 until latSteps) {
                for (j in 0 until lonSteps) {
                    val a = baseIndex + i * stride + j
                    val b = baseIndex + (i + 1) * stride + j
                    val c = baseIndex + (i + 1) * stride + (j + 1)
                    val d = baseIndex + i * stride + (j + 1)
                    indices.add(a); indices.add(d); indices.add(b)
                    indices.add(d); indices.add(c); indices.add(b)
                }
            }
        }

        fun buildVertexBuffer(): FloatArray {
            val out = FloatArray(positions.size * 10)
            for (i in positions.indices) {
                val p = positions[i]
                val n = normals[i]
                val d = localDirs[i]
                val base = i * 10
                out[base + 0] = p.x
                out[base + 1] = p.y
                out[base + 2] = p.z
                out[base + 3] = n.x
                out[base + 4] = n.y
                out[base + 5] = n.z
                out[base + 6] = regions[i]
                out[base + 7] = d.x
                out[base + 8] = d.y
                out[base + 9] = d.z
            }
            return out
        }

        fun buildIndexArray(): IntArray = indices.toIntArray()

        private fun rotateAroundAxis(v: Vec3, axis: Vec3, angle: Float): Vec3 {
            val c = cos(angle)
            val s = sin(angle)
            return v * c + axis.cross(v) * s + axis * (axis.dot(v) * (1f - c))
        }
    }

    fun build(): HeartMeshData {
        val acc = MeshAccumulator()

        // 1. LEFT VENTRICLE (Conical muscular apex chamber, left-posterior-inferior)
        val lvCenter = Vec3(0.26f, -0.34f, 0.04f)
        acc.addLoftedTube(
            spine = listOf(
                Vec3(0.16f, 0.24f, -0.04f),
                Vec3(0.24f, -0.05f, 0.02f),
                Vec3(0.32f, -0.45f, 0.12f),
                Vec3(0.42f, -0.82f, 0.20f),
                Vec3(0.48f, -1.04f, 0.26f)
            ),
            rings = 28,
            slices = 32,
            region = REGION_LV,
            contractionCenter = lvCenter,
            radiusAt = { t, phi ->
                val profile = when {
                    t < 0.25f -> 0.32f + 0.14f * sin((t / 0.25f) * PI.toFloat() * 0.5f)
                    else -> {
                        val u = (t - 0.25f) / 0.75f
                        0.46f * (1f - u.pow(1.65f)).coerceAtLeast(0.02f)
                    }
                }
                // Slight organic ventricular groove indentation & lateral fullness
                val lobe = 1f + 0.08f * cos(phi) - 0.06f * cos(2f * phi)
                (profile * lobe).coerceAtLeast(0.015f)
            },
            capStart = true,
            capEnd = true
        )

        // 2. RIGHT VENTRICLE (Crescentic anterior chamber sweeping into Pulmonary Infundibulum)
        val rvCenter = Vec3(-0.14f, -0.18f, 0.26f)
        acc.addLoftedTube(
            spine = listOf(
                Vec3(-0.36f, 0.10f, 0.12f),
                Vec3(-0.24f, -0.26f, 0.24f),
                Vec3(0.04f, -0.62f, 0.34f),
                Vec3(0.14f, -0.78f, 0.34f)
            ),
            rings = 24,
            slices = 30,
            region = REGION_RV,
            contractionCenter = rvCenter,
            radiusAt = { t, phi ->
                val baseR = 0.36f * sin((t * 0.85f + 0.15f) * PI.toFloat()).coerceAtLeast(0.05f)
                // Crescent flattening posteriorly against LV, bulging anteriorly
                val crescent = 1f + 0.16f * sin(phi) - 0.12f * cos(2f * phi)
                (baseR * crescent).coerceAtLeast(0.02f)
            },
            capStart = true,
            capEnd = true
        )

        // RV Conus Arteriosus / Infundibulum rising up to the Pulmonary Valve
        acc.addLoftedTube(
            spine = listOf(
                Vec3(-0.16f, -0.18f, 0.28f),
                Vec3(-0.12f, 0.10f, 0.36f),
                Vec3(-0.06f, 0.36f, 0.34f)
            ),
            rings = 14,
            slices = 26,
            region = REGION_RV,
            contractionCenter = rvCenter,
            radiusAt = { t, _ ->
                0.28f - 0.10f * t
            },
            capStart = true,
            capEnd = false
        )

        // 3. RIGHT ATRIUM + RIGHT AURICLE (Superior-Right chamber)
        val raCenter = Vec3(-0.54f, 0.36f, 0.0f)
        acc.addSculptedEllipsoid(
            center = raCenter,
            radiusX = 0.28f,
            radiusY = 0.30f,
            radiusZ = 0.28f,
            region = REGION_RA,
            contractionCenter = raCenter,
            deform = { p, theta, phi ->
                // Organic auricular bulge anteriorly toward aortic root
                val auricle = if (p.z > 0.05f && p.y > 0.30f) 0.06f * sin(theta) else 0f
                Vec3(p.x + auricle * 0.5f, p.y, p.z + auricle)
            }
        )
        // Right Auricle (Ушко правого предсердия)
        acc.addLoftedTube(
            spine = listOf(
                Vec3(-0.46f, 0.44f, 0.14f),
                Vec3(-0.34f, 0.46f, 0.24f),
                Vec3(-0.24f, 0.42f, 0.26f)
            ),
            rings = 10,
            slices = 18,
            region = REGION_RA,
            contractionCenter = raCenter,
            radiusAt = { t, _ -> 0.13f * (1f - 0.65f * t) },
            capEnd = true
        )

        // 4. LEFT ATRIUM + LEFT AURICLE (Posterior-Superior-Left chamber)
        val laCenter = Vec3(0.32f, 0.44f, -0.28f)
        acc.addSculptedEllipsoid(
            center = laCenter,
            radiusX = 0.30f,
            radiusY = 0.25f,
            radiusZ = 0.25f,
            region = REGION_LA,
            contractionCenter = laCenter
        )
        // Left Auricle (Ушко левого предсердия) wrapping anteriorly around pulmonary trunk
        acc.addLoftedTube(
            spine = listOf(
                Vec3(0.46f, 0.46f, -0.14f),
                Vec3(0.56f, 0.45f, 0.02f),
                Vec3(0.48f, 0.42f, 0.18f)
            ),
            rings = 12,
            slices = 18,
            region = REGION_LA,
            contractionCenter = laCenter,
            radiusAt = { t, _ -> 0.12f * (1f - 0.62f * t) },
            capEnd = true
        )

        // 5. INTERVENTRICULAR & INTERATRIAL SEPTUM (Internal muscular wall dividing Left/Right)
        val septumCenter = Vec3(0.02f, -0.12f, -0.02f)
        acc.addSculptedEllipsoid(
            center = septumCenter,
            radiusX = 0.055f,
            radiusY = 0.66f,
            radiusZ = 0.26f,
            region = REGION_SEPTUM,
            contractionCenter = septumCenter,
            latSteps = 16,
            lonSteps = 20,
            deform = { p, _, _ ->
                // Tilt septum anatomically toward the apex
                val dy = (0.35f - p.y)
                Vec3(p.x + dy * 0.18f, p.y, p.z + dy * 0.08f)
            }
        )

        // 6. SUPERIOR & INFERIOR VENA CAVA (Venous inflow to RA)
        acc.addLoftedTube(
            spine = listOf(
                Vec3(-0.54f, 1.32f, -0.04f),
                Vec3(-0.54f, 0.92f, -0.03f),
                Vec3(-0.54f, 0.54f, -0.02f)
            ),
            rings = 16,
            slices = 24,
            region = REGION_VENA_CAVA,
            contractionCenter = raCenter,
            radiusAt = { t, _ -> 0.145f + 0.045f * t * t }
        )
        acc.addLoftedTube(
            spine = listOf(
                Vec3(-0.55f, -0.96f, -0.22f),
                Vec3(-0.54f, -0.42f, -0.18f),
                Vec3(-0.54f, 0.16f, -0.12f)
            ),
            rings = 16,
            slices = 24,
            region = REGION_VENA_CAVA,
            contractionCenter = raCenter,
            radiusAt = { t, _ -> 0.155f + 0.045f * t * t }
        )

        // 7. FOUR PULMONARY VEINS (Arterial inflow to LA)
        val pvSpecs = listOf(
            listOf(Vec3(0.98f, 0.58f, -0.34f), Vec3(0.72f, 0.52f, -0.32f), Vec3(0.46f, 0.48f, -0.30f)),
            listOf(Vec3(0.94f, 0.30f, -0.36f), Vec3(0.70f, 0.34f, -0.33f), Vec3(0.46f, 0.38f, -0.30f)),
            listOf(Vec3(-0.22f, 0.56f, -0.44f), Vec3(-0.02f, 0.52f, -0.38f), Vec3(0.18f, 0.48f, -0.32f)),
            listOf(Vec3(-0.22f, 0.30f, -0.44f), Vec3(-0.02f, 0.34f, -0.38f), Vec3(0.18f, 0.38f, -0.32f))
        )
        pvSpecs.forEach { spine ->
            acc.addLoftedTube(
                spine = spine,
                rings = 12,
                slices = 18,
                region = REGION_PULM_VEINS,
                contractionCenter = laCenter,
                radiusAt = { t, _ -> 0.085f + 0.025f * t }
            )
        }

        // 8. PULMONARY TRUNK + LEFT & RIGHT PULMONARY ARTERIES
        val paCenter = Vec3(0.02f, 0.65f, 0.10f)
        // Main Pulmonary Trunk
        acc.addLoftedTube(
            spine = listOf(
                Vec3(-0.06f, 0.34f, 0.34f),
                Vec3(-0.02f, 0.62f, 0.24f),
                Vec3(0.04f, 0.84f, -0.04f)
            ),
            rings = 18,
            slices = 26,
            region = REGION_PA,
            contractionCenter = paCenter,
            radiusAt = { t, _ -> 0.165f + 0.015f * sin(t * PI.toFloat()) }
        )
        // Left Pulmonary Artery
        acc.addLoftedTube(
            spine = listOf(
                Vec3(0.02f, 0.84f, -0.04f),
                Vec3(0.42f, 0.86f, -0.16f),
                Vec3(0.90f, 0.86f, -0.26f)
            ),
            rings = 16,
            slices = 22,
            region = REGION_PA,
            contractionCenter = Vec3(0.42f, 0.86f, -0.16f),
            radiusAt = { t, _ -> 0.135f * (1f - 0.20f * t) }
        )
        // Right Pulmonary Artery (passes behind ascending aorta & SVC)
        acc.addLoftedTube(
            spine = listOf(
                Vec3(0.02f, 0.84f, -0.06f),
                Vec3(-0.42f, 0.82f, -0.16f),
                Vec3(-0.88f, 0.80f, -0.22f)
            ),
            rings = 16,
            slices = 22,
            region = REGION_PA,
            contractionCenter = Vec3(-0.42f, 0.82f, -0.16f),
            radiusAt = { t, _ -> 0.135f * (1f - 0.20f * t) }
        )

        // 9. AORTA (Ascending, Arch, 3 Supra-aortic Branches, Descending)
        val aortaCenter = Vec3(0.08f, 0.72f, -0.06f)
        acc.addLoftedTube(
            spine = listOf(
                Vec3(0.08f, 0.22f, 0.02f),
                Vec3(-0.08f, 0.62f, 0.08f),
                Vec3(-0.08f, 0.94f, 0.02f),
                Vec3(0.06f, 1.12f, -0.10f),
                Vec3(0.22f, 0.96f, -0.32f),
                Vec3(0.20f, 0.32f, -0.42f),
                Vec3(0.20f, -0.42f, -0.42f),
                Vec3(0.20f, -1.06f, -0.42f)
            ),
            rings = 40,
            slices = 28,
            region = REGION_AORTA,
            contractionCenter = aortaCenter,
            radiusAt = { t, _ ->
                // Subtle aortic root sinuses at start, slight taper down descending aorta
                val rootBulge = 0.03f * kotlin.math.exp(-((t / 0.08f) * (t / 0.08f)))
                (0.175f - 0.035f * t + rootBulge)
            }
        )
        // 3 Iconic Supra-Aortic Arch Branches
        // Brachiocephalic trunk
        acc.addLoftedTube(
            spine = listOf(Vec3(-0.06f, 1.04f, -0.02f), Vec3(-0.14f, 1.24f, -0.02f), Vec3(-0.22f, 1.44f, -0.02f)),
            rings = 10, slices = 18, region = REGION_AORTA, contractionCenter = Vec3(-0.14f, 1.24f, -0.02f),
            radiusAt = { t, _ -> 0.068f * (1f - 0.15f * t) }
        )
        // Left Common Carotid
        acc.addLoftedTube(
            spine = listOf(Vec3(0.06f, 1.10f, -0.10f), Vec3(0.06f, 1.28f, -0.10f), Vec3(0.06f, 1.48f, -0.10f)),
            rings = 10, slices = 18, region = REGION_AORTA, contractionCenter = Vec3(0.06f, 1.28f, -0.10f),
            radiusAt = { t, _ -> 0.056f * (1f - 0.15f * t) }
        )
        // Left Subclavian
        acc.addLoftedTube(
            spine = listOf(Vec3(0.16f, 1.04f, -0.18f), Vec3(0.22f, 1.24f, -0.18f), Vec3(0.28f, 1.42f, -0.18f)),
            rings = 10, slices = 18, region = REGION_AORTA, contractionCenter = Vec3(0.22f, 1.24f, -0.18f),
            radiusAt = { t, _ -> 0.058f * (1f - 0.15f * t) }
        )

        // 10. 4 ANIMATED CARDIAC VALVES
        val valves = listOf(
            buildValveMesh(
                isAvValve = true,
                center = Vec3(-0.38f, 0.10f, 0.10f),
                flowAxis = Vec3(0.45f, -0.85f, 0.26f).normalized(),
                radius = 0.145f,
                leafletCount = 3
            ),
            buildValveMesh(
                isAvValve = true,
                center = Vec3(0.24f, 0.18f, -0.12f),
                flowAxis = Vec3(0.16f, -0.92f, 0.36f).normalized(),
                radius = 0.135f,
                leafletCount = 2
            ),
            buildValveMesh(
                isAvValve = false,
                center = Vec3(-0.06f, 0.36f, 0.34f),
                flowAxis = Vec3(0.12f, 0.94f, -0.32f).normalized(),
                radius = 0.13f,
                leafletCount = 3
            ),
            buildValveMesh(
                isAvValve = false,
                center = Vec3(0.08f, 0.24f, 0.02f),
                flowAxis = Vec3(-0.18f, 0.96f, 0.12f).normalized(),
                radius = 0.135f,
                leafletCount = 3
            )
        )

        return HeartMeshData(
            heartVertices = acc.buildVertexBuffer(),
            heartIndices = acc.buildIndexArray(),
            valveMeshes = valves
        )
    }

    /**
     * Generates a multi-leaflet valve in local orifice space (flow along +Y, orifice in XZ plane),
     * then transforms positions and normals into heart-model space while preserving hingeWeight
     * (0 at annulus ring, 1 at cusp free tip) so the vertex shader can hinge-open leaflets smoothly!
     */
    private fun buildValveMesh(
        isAvValve: Boolean,
        center: Vec3,
        flowAxis: Vec3,
        radius: Float,
        leafletCount: Int
    ): ValveMeshData {
        val up = flowAxis.normalized()
        val ref = if (kotlin.math.abs(up.z) < 0.9f) Vec3(0f, 0f, 1f) else Vec3(1f, 0f, 0f)
        val tangent = up.cross(ref).normalized()
        val bitangent = up.cross(tangent).normalized()

        val radialSteps = 5
        val arcSteps = 8
        val floatsPerVert = 8
        val verts = mutableListOf<Float>()

        fun emitVertex(localPos: Vec3, localNorm: Vec3, hingeWeight: Float, sectorCenterAngle: Float) {
            val worldPos = center + tangent * localPos.x + up * localPos.y + bitangent * localPos.z
            val worldNorm = (tangent * localNorm.x + up * localNorm.y + bitangent * localNorm.z).normalized()
            verts.add(worldPos.x)
            verts.add(worldPos.y)
            verts.add(worldPos.z)
            verts.add(worldNorm.x)
            verts.add(worldNorm.y)
            verts.add(worldNorm.z)
            verts.add(hingeWeight)
            verts.add(sectorCenterAngle)
        }

        val sectorSpan = (2f * PI.toFloat()) / leafletCount
        for (c in 0 until leafletCount) {
            val startAngle = c * sectorSpan
            val midAngle = startAngle + sectorSpan * 0.5f

            for (r in 0 until radialSteps) {
                val t0 = r.toFloat() / radialSteps
                val t1 = (r + 1).toFloat() / radialSteps
                val rad0 = radius * (1f - t0 * 0.95f)
                val rad1 = radius * (1f - t1 * 0.95f)
                val y0 = 0.02f * sin(t0 * PI.toFloat() * 0.5f)
                val y1 = 0.02f * sin(t1 * PI.toFloat() * 0.5f)

                for (a in 0 until arcSteps) {
                    val u0 = a.toFloat() / arcSteps
                    val u1 = (a + 1).toFloat() / arcSteps
                    val ang0 = startAngle + sectorSpan * (0.04f + 0.92f * u0)
                    val ang1 = startAngle + sectorSpan * (0.04f + 0.92f * u1)

                    val p00 = Vec3(rad0 * cos(ang0), y0, rad0 * sin(ang0))
                    val p10 = Vec3(rad1 * cos(ang0), y1, rad1 * sin(ang0))
                    val p01 = Vec3(rad0 * cos(ang1), y0, rad0 * sin(ang1))
                    val p11 = Vec3(rad1 * cos(ang1), y1, rad1 * sin(ang1))

                    val n = Vec3(0f, 1f, 0f)
                    // Two triangles per quad
                    emitVertex(p00, n, t0, midAngle)
                    emitVertex(p10, n, t1, midAngle)
                    emitVertex(p01, n, t0, midAngle)

                    emitVertex(p01, n, t0, midAngle)
                    emitVertex(p10, n, t1, midAngle)
                    emitVertex(p11, n, t1, midAngle)
                }
            }
        }

        val arr = FloatArray(verts.size)
        for (i in verts.indices) arr[i] = verts[i]

        return ValveMeshData(
            isAvValve = isAvValve,
            center = center,
            flowAxis = up,
            radius = radius,
            leafletCount = leafletCount,
            vertices = arr
        )
    }
}
