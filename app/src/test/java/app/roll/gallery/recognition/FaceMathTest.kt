package app.roll.gallery.recognition

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class FaceMathTest {
    @Test
    fun decodesOneFaceOnTheGrid() {
        // A 32 × 32 input: at stride 8 that's a 4 × 4 grid. One strong spot, at column 1, row 2.
        val spots = 16
        val cls = FloatArray(spots).also { it[2 * 4 + 1] = 0.9f }
        val obj = FloatArray(spots).also { it[2 * 4 + 1] = 0.9f }
        val bbox = FloatArray(spots * 4).also { b ->
            val i = 2 * 4 + 1
            b[i * 4] = 0.5f; b[i * 4 + 1] = 0.5f // centre in the middle of the spot
            b[i * 4 + 2] = 0f; b[i * 4 + 3] = 0f // size e^0 × 8 = 8
        }
        val kps = FloatArray(spots * 10)
        val faces = decodeFaces(mapOf(8 to cls), mapOf(8 to obj), mapOf(8 to bbox), mapOf(8 to kps), 32, 32)
        val face = faces.single()
        assertEquals(0.9f, face.score, 0.0001f)
        assertEquals(8f, face.left, 0.0001f) // centre x (1 + 0.5) × 8 = 12, minus half of 8
        assertEquals(16f, face.top, 0.0001f) // centre y (2 + 0.5) × 8 = 20, minus 4
        assertEquals(8f, face.width, 0.0001f)
        assertEquals(8f, face.landmarks[0], 0.0001f) // landmark x: (0 + column 1) × 8
        assertEquals(16f, face.landmarks[1], 0.0001f) // landmark y: (0 + row 2) × 8
    }

    @Test
    fun weakSpotsAreDropped() {
        val cls = FloatArray(16) { 0.5f }
        val obj = FloatArray(16) { 0.5f }
        val faces = decodeFaces(mapOf(8 to cls), mapOf(8 to obj), mapOf(8 to FloatArray(64)), mapOf(8 to FloatArray(160)), 32, 32)
        assertEquals(0, faces.size)
    }

    private fun face(left: Float, score: Float) = DetectedFace(left, 0f, 10f, 10f, FloatArray(10), score)

    @Test
    fun overlapsKeepTheBest() {
        val kept = suppressOverlaps(listOf(face(0f, 0.8f), face(1f, 0.95f), face(50f, 0.9f)))
        assertEquals(listOf(0.95f, 0.9f), kept.map { it.score })
        assertEquals(0f, overlap(face(0f, 1f), face(20f, 1f)), 0f)
    }

    @Test
    fun transformMapsPointsExactlyWhenTheyFit() {
        // The template moved by (10, 20) and halved: the transform should undo exactly that.
        val from = FloatArray(10) { i -> FACE_TEMPLATE[i] / 2 + if (i % 2 == 0) 10f else 20f }
        val (a, b, tx, ty) = similarityTransform(from, FACE_TEMPLATE).toList()
        for (i in 0 until 5) {
            val x = from[2 * i]
            val y = from[2 * i + 1]
            assertEquals(FACE_TEMPLATE[2 * i], a * x - b * y + tx, 0.01f)
            assertEquals(FACE_TEMPLATE[2 * i + 1], b * x + a * y + ty, 0.01f)
        }
        assertEquals(2f, a, 0.001f)
        assertEquals(0f, b, 0.001f)
    }

    @Test
    fun similarityOfNormalizedVectors() {
        val a = normalized(floatArrayOf(3f, 4f))
        assertEquals(1f, similarity(a, a), 0.0001f)
        assertEquals(0f, similarity(normalized(floatArrayOf(1f, 0f)), normalized(floatArrayOf(0f, 2f))), 0.0001f)
    }

    @Test
    fun groupsSimilarFaces() {
        val alice1 = FaceRef(1, 0, normalized(floatArrayOf(1f, 0.1f, 0f)))
        val alice2 = FaceRef(2, 0, normalized(floatArrayOf(1f, 0f, 0.1f)))
        val bob = FaceRef(2, 1, normalized(floatArrayOf(0f, 1f, 0f)))
        val alice3 = FaceRef(3, 0, normalized(floatArrayOf(0.9f, 0.1f, 0.1f)))
        val groups = groupFaces(listOf(bob, alice3, alice1, alice2))
        assertEquals(listOf(listOf("1#0", "2#0", "3#0"), listOf("2#1")), groups.map { g -> g.map { it.key } })
    }

    @Test
    fun embeddingsRoundTrip() {
        val v = floatArrayOf(0.25f, -1f, 3.5f)
        assertArrayEquals(v, decodeEmbedding(encodeEmbedding(v)), 0f)
        assertEquals(null, decodeEmbedding("@@not base64@@"))
    }
}
