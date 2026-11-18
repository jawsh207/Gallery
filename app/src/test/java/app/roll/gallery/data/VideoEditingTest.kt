package app.roll.gallery.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoEditingTest {
    @Test
    fun cropsTheMiddleToTheShape() {
        assertArrayEquals(floatArrayOf(-0.5625f, 0.5625f, -1f, 1f), centreCropNdc(1920, 1080, 1f), 1e-4f)
        assertArrayEquals(floatArrayOf(-1f, 1f, -0.5625f, 0.5625f), centreCropNdc(1080, 1920, 1f), 1e-4f)
        assertArrayEquals(floatArrayOf(-1f, 1f, -1f, 1f), centreCropNdc(1920, 1080, null), 0f)
    }

    @Test
    fun lengthFollowsTrimAndSpeed() {
        assertEquals(10_000L, editedLengthMs(10_000, VideoEdits()))
        assertEquals(3_000L, editedLengthMs(10_000, VideoEdits(startMs = 2_000, endMs = 8_000, speed = 2f)))
    }

    @Test
    fun nothingChangedUntilSomethingIs() {
        assertTrue(VideoEdits().isUnchanged)
        assertTrue(!VideoEdits(muted = true).isUnchanged)
    }
}
