package app.roll.gallery.recognition

import org.junit.Assert.assertEquals
import org.junit.Test

class ThingsTest {
    private fun unit(i: Int) = FloatArray(8).also { it[i] = 1f }

    @Test
    fun photosGoToTheirClosestThingUnlessSomethingElseIsCloser() {
        val phrases = mapOf(
            "a photo of a dog" to unit(0),
            "a photo of food" to unit(1),
            "a photo of people" to unit(2),
        )
        val fingerprints = mapOf(
            1L to packFingerprint(floatArrayOf(0.9f, 0.1f, 0f, 0f, 0f, 0f, 0f, 0f)),
            2L to packFingerprint(floatArrayOf(0.1f, 0.8f, 0.2f, 0f, 0f, 0f, 0f, 0f)),
            3L to packFingerprint(floatArrayOf(0.3f, 0.1f, 0.9f, 0f, 0f, 0f, 0f, 0f)),
            4L to packFingerprint(floatArrayOf(0.1f, 0f, 0f, 0.99f, 0f, 0f, 0f, 0f)),
        )
        val things = sortIntoThings(fingerprints, phrases)
        assertEquals(listOf(1L), things[Thing.Pets])
        assertEquals(listOf(2L), things[Thing.Food])
        assertEquals(2, things.size)
    }
}
