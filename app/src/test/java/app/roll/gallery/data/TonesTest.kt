package app.roll.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TonesTest {
    private fun grey(v: Int) = (v shl 16) or (v shl 8) or v
    private fun level(p: Int) = p and 0xFF

    @Test
    fun shadowsMoveDarksAndLeaveLights() {
        assertTrue(level(tone(grey(30), 0f, 1f)) > 60)
        assertTrue(level(tone(grey(240), 0f, 1f)) - 240 < 3)
        assertTrue(level(tone(grey(30), 0f, -1f)) < 30)
    }

    @Test
    fun highlightsMoveLightsAndLeaveDarks() {
        assertTrue(level(tone(grey(220), -1f, 0f)) < 160)
        assertTrue(kotlin.math.abs(level(tone(grey(10), -1f, 0f)) - 10) <= 1)
    }

    @Test
    fun enhanceLiftsFlatDarkPhotosAndLeavesGoodOnesMostlyAlone() {
        val flatDark = IntArray(256).apply { for (i in 40..100) this[i] = 100 }
        val dull = autoEnhance(flatDark, 0.1f)
        assertTrue(dull.brightness > 0.05f)
        assertTrue(dull.contrast > 0.1f)
        assertTrue(dull.saturation > 0.1f)
        val full = IntArray(256) { 100 }
        val good = autoEnhance(full, 0.45f)
        assertTrue(good.contrast < 0.05f)
        assertEquals(0f, good.saturation, 0.001f)
    }
}
