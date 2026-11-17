package app.roll.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatsTest {
    @Test
    fun durations() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:05", formatDuration(4_600))
        assertEquals("12:30", formatDuration(750_000))
        assertEquals("1:02:03", formatDuration(3_723_000))
    }

    @Test
    fun sizes() {
        assertEquals("999 B", formatBytes(999))
        assertEquals("1 KB", formatBytes(1000))
        assertEquals("820 KB", formatBytes(820_000))
        assertEquals("2.4 MB", formatBytes(2_400_000))
        assertEquals("1.0 MB", formatBytes(999_990))
        assertEquals("1.1 GB", formatBytes(1_100_000_000))
    }

    @Test
    fun megapixelCounts() {
        assertEquals("12 MP", megapixels(4000, 3000))
        assertEquals("0.3 MP", megapixels(640, 480))
        assertNull(megapixels(0, 100))
    }

    @Test
    fun cameraSettings() {
        assertEquals("1/120 s", formatExposure(1 / 120.0))
        assertEquals("2 s", formatExposure(2.0))
        assertEquals("0.5 s", formatExposure(0.5))
        assertEquals("ƒ/1.8", formatAperture(1.8))
        assertEquals("ƒ/8", formatAperture(8.0))
        assertEquals("6.9 mm", formatFocalLength(6.9))
        assertEquals("24 mm", formatFocalLength(24.0))
    }

    @Test
    fun shareTypes() {
        assertEquals("image/jpeg", commonMimeType(listOf("image/jpeg", "image/jpeg")))
        assertEquals("image/*", commonMimeType(listOf("image/jpeg", "image/png")))
        assertEquals("*/*", commonMimeType(listOf("image/jpeg", "video/mp4")))
    }
}
