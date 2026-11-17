package app.roll.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StraightenTest {
    @Test
    fun noTiltNoZoom() {
        assertEquals(1f, straightenScale(4000f, 3000f, 0f), 0f)
    }

    @Test
    fun squareAtFortyFiveDegrees() {
        assertEquals(Math.sqrt(2.0).toFloat(), straightenScale(100f, 100f, 45f), 0.001f)
    }

    @Test
    fun sameZoomEitherWay() {
        assertEquals(straightenScale(4000f, 3000f, 10f), straightenScale(4000f, 3000f, -10f), 0.0001f)
        // A wide photo needs more zoom than a square one for the same tilt.
        assertTrue(straightenScale(4000f, 3000f, 10f) > straightenScale(100f, 100f, 10f))
    }

    @Test
    fun tiltCountsAsAnEdit() {
        assertFalse(Edits(straighten = 2f).isUnchanged)
    }
}
