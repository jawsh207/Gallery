package app.roll.gallery.recognition

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DescriptionsTest {
    @Test
    fun packingKeepsDirection() {
        val v = normalized(floatArrayOf(3f, 4f, 0f))
        val packed = packFingerprint(v)
        assertArrayEquals(byteArrayOf(76, 102, 0), packed)
        assertEquals(1f, packedSimilarity(packed, v), 0.01f)
        assertEquals(0f, packedSimilarity(packFingerprint(floatArrayOf(0f, 0f, 1f)), v), 0.001f)
    }

    @Test
    fun bestMatchesStayCloseToTheBest() {
        val query = floatArrayOf(1f, 0f)
        val photos = mapOf(
            1L to packFingerprint(floatArrayOf(0.30f, 0.95f)),
            2L to packFingerprint(floatArrayOf(0.27f, 0.96f)),
            3L to packFingerprint(floatArrayOf(0.10f, 0.99f)),
            4L to packFingerprint(floatArrayOf(0.15f, 0.99f)),
        )
        assertEquals(listOf(1L, 2L), bestMatches(photos, query, minScore = 0.2f, margin = 0.05f))
        // Nothing good enough: nothing at all, rather than the least bad.
        assertEquals(emptyList<Long>(), bestMatches(photos, query, minScore = 0.5f))
        assertEquals(emptyList<Long>(), bestMatches(emptyMap(), query))
    }

    @Test
    fun linesRoundTrip() {
        val packed = byteArrayOf(1, -2, 127, -127)
        val (id, modified, back) = parseDescriptionLine(formatDescriptionLine(5, 9, packed))!!
        assertEquals(5L, id)
        assertEquals(9L, modified)
        assertArrayEquals(packed, back)
        assertNull(parseDescriptionLine(formatDescriptionLine(5, 9, null))!!.third)
        assertNull(parseDescriptionLine("x\t1\t"))
    }
}
