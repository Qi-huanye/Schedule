package dev.wakeuppure.data.background

import com.materialkolor.hct.Hct
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Weighted k-means over an image's chromatic pixels in HCT space. Points are
 * (C·cos h, C·sin h, ½·(T − 50)): clusters separate mainly by hue and colorfulness, so
 * course colors can come from hues the photo really contains rather than invented ones.
 */
object HctKMeans {
    private const val MIN_CHROMA = 8.0
    private const val TONE_WEIGHT = 0.5
    private const val MAX_ITERATIONS = 16

    /** Centroids holding at least [minShare] of the chromatic pixels, largest cluster first. */
    fun clusters(pixels: IntArray, k: Int = 8, minShare: Double = 0.02): List<Int> {
        val counts = HashMap<Int, Int>()
        for (pixel in pixels) if (pixel ushr 24 == 255) counts.merge(pixel, 1, Int::plus)
        val points = ArrayList<DoubleArray>()
        val weights = ArrayList<Double>()
        // Sorted keys keep initialization and tie-breaking deterministic.
        for (argb in counts.keys.sorted()) {
            val hct = Hct.fromInt(argb)
            if (hct.chroma < MIN_CHROMA) continue
            val radians = Math.toRadians(hct.hue)
            points += doubleArrayOf(hct.chroma * cos(radians), hct.chroma * sin(radians), (hct.tone - 50.0) * TONE_WEIGHT)
            weights += counts.getValue(argb).toDouble()
        }
        if (points.isEmpty()) return emptyList()

        val centroids = initialCentroids(points, weights, minOf(k, points.size))
        val assignment = IntArray(points.size) { -1 }
        for (iteration in 0 until MAX_ITERATIONS) {
            var changed = false
            for (i in points.indices) {
                val nearest = centroids.indices.minBy { distance(points[i], centroids[it]) }
                if (assignment[i] != nearest) { assignment[i] = nearest; changed = true }
            }
            if (!changed) break
            for (c in centroids.indices) {
                val sum = DoubleArray(3)
                var total = 0.0
                for (i in points.indices) if (assignment[i] == c) {
                    for (d in 0..2) sum[d] += points[i][d] * weights[i]
                    total += weights[i]
                }
                if (total > 0) centroids[c] = DoubleArray(3) { sum[it] / total }
            }
        }

        val sizes = DoubleArray(centroids.size)
        for (i in points.indices) sizes[assignment[i]] += weights[i]
        val total = sizes.sum()
        return centroids.indices.filter { sizes[it] / total >= minShare }
            .sortedByDescending { sizes[it] }
            .map { c ->
                val (x, y, z) = centroids[c].let { Triple(it[0], it[1], it[2]) }
                val hue = (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
                Hct.from(hue, hypot(x, y), (z / TONE_WEIGHT + 50.0).coerceIn(0.0, 100.0)).toInt()
            }
    }

    /** Deterministic k-means++: start at the heaviest color, then repeatedly take the point whose weight × squared distance to its nearest centroid is largest. */
    private fun initialCentroids(points: List<DoubleArray>, weights: List<Double>, k: Int): MutableList<DoubleArray> {
        val centroids = mutableListOf(points[weights.indices.maxBy { weights[it] }].copyOf())
        val nearest = DoubleArray(points.size) { distance(points[it], centroids[0]) }
        while (centroids.size < k) {
            val next = points.indices.maxBy { nearest[it] * weights[it] }
            if (nearest[next] == 0.0) break
            centroids += points[next].copyOf()
            for (i in points.indices) nearest[i] = minOf(nearest[i], distance(points[i], centroids.last()))
        }
        return centroids
    }

    private fun distance(a: DoubleArray, b: DoubleArray): Double {
        val dx = a[0] - b[0]; val dy = a[1] - b[1]; val dz = a[2] - b[2]
        return dx * dx + dy * dy + dz * dz
    }
}
