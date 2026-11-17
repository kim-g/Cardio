package com.example.simulation

import kotlin.math.sqrt

data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    operator fun div(s: Float) = Vec3(x / s, y / s, z / s)

    fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z

    fun cross(o: Vec3): Vec3 = Vec3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x
    )

    fun length(): Float = sqrt(x * x + y * y + z * z)

    fun normalized(): Vec3 {
        val len = length()
        return if (len > 1e-6f) Vec3(x / len, y / len, z / len) else Vec3(0f, 1f, 0f)
    }

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
        fun lerp(a: Vec3, b: Vec3, t: Float): Vec3 =
            Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t)
    }
}

object SplineMath {

    /**
     * Evaluates a centripetal/uniform Catmull-Rom spline across control points for t in [0, 1].
     */
    fun evalCatmullRom(points: List<Vec3>, t: Float): Vec3 {
        if (points.isEmpty()) return Vec3.ZERO
        if (points.size == 1) return points[0]
        val clamped = t.coerceIn(0f, 1f)
        val segments = points.size - 1
        val scaled = clamped * segments
        val idx = scaled.toInt().coerceIn(0, segments - 1)
        val localT = scaled - idx

        val p0 = points[(idx - 1).coerceAtLeast(0)]
        val p1 = points[idx]
        val p2 = points[(idx + 1).coerceAtMost(points.lastIndex)]
        val p3 = points[(idx + 2).coerceAtMost(points.lastIndex)]

        val t2 = localT * localT
        val t3 = t2 * localT

        val x = 0.5f * ((2f * p1.x) +
            (-p0.x + p2.x) * localT +
            (2f * p0.x - 5f * p1.x + 4f * p2.x - p3.x) * t2 +
            (-p0.x + 3f * p1.x - 3f * p2.x + p3.x) * t3)

        val y = 0.5f * ((2f * p1.y) +
            (-p0.y + p2.y) * localT +
            (2f * p0.y - 5f * p1.y + 4f * p2.y - p3.y) * t2 +
            (-p0.y + 3f * p1.y - 3f * p2.y + p3.y) * t3)

        val z = 0.5f * ((2f * p1.z) +
            (-p0.z + p2.z) * localT +
            (2f * p0.z - 5f * p1.z + 4f * p2.z - p3.z) * t2 +
            (-p0.z + 3f * p1.z - 3f * p2.z + p3.z) * t3)

        return Vec3(x, y, z)
    }

    fun evalTangent(points: List<Vec3>, t: Float): Vec3 {
        val eps = 0.004f
        val t0 = (t - eps).coerceAtLeast(0f)
        val t1 = (t + eps).coerceAtMost(1f)
        return (evalCatmullRom(points, t1) - evalCatmullRom(points, t0)).normalized()
    }

    fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
