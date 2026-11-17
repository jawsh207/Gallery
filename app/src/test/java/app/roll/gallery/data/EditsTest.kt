package app.roll.gallery.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditsTest {
    /** Applies a colour matrix to one (r, g, b) value, as Android does (without clamping). */
    private fun apply(m: FloatArray, r: Float, g: Float, b: Float): FloatArray = FloatArray(3) { row ->
        m[row * 5] * r + m[row * 5 + 1] * g + m[row * 5 + 2] * b + m[row * 5 + 4]
    }

    private fun assertColor(expected: FloatArray, actual: FloatArray) = assertArrayEquals(expected, actual, 0.01f)

    @Test
    fun noEditsChangeNothing() {
        assertTrue(Edits().isUnchanged)
        assertTrue(Edits(quarterTurns = 4).isUnchanged)
        assertFalse(Edits(flipped = true).isUnchanged)
        assertArrayEquals(IDENTITY_MATRIX, colorMatrix(Edits()), 0f)
    }

    @Test
    fun monoMakesGrey() {
        val grey = apply(filterMatrix(Filter.Mono), 200f, 100f, 50f)
        assertEquals(grey[0], grey[1], 0.01f)
        assertEquals(grey[1], grey[2], 0.01f)
    }

    @Test
    fun brightnessAndContrast() {
        assertColor(floatArrayOf(164f, 164f, 164f), apply(brightnessMatrix(0.5f), 132f, 132f, 132f))
        // Contrast keeps mid-grey and pushes the rest away from it.
        assertColor(floatArrayOf(128f, 128f, 128f), apply(contrastMatrix(0.5f), 128f, 128f, 128f))
        assertColor(floatArrayOf(188f, 188f, 188f), apply(contrastMatrix(0.5f), 168f, 168f, 168f))
    }

    @Test
    fun concatAppliesInOrder() {
        // Brighten by 64, then double contrast: (100 + 64) → 128 + 2 × (164 − 128) = 200.
        val m = concat(brightnessMatrix(1f), contrastMatrix(1f))
        assertColor(floatArrayOf(200f, 200f, 200f), apply(m, 100f, 100f, 100f))
    }

    @Test
    fun warmthShiftsRedAndBlue() {
        assertColor(floatArrayOf(124f, 100f, 76f), apply(warmthMatrix(1f), 100f, 100f, 100f))
    }

    @Test
    fun aspectFits() {
        assertEquals(CropRect(0.125f, 0f, 0.875f, 1f), fitAspect(1f, 4000, 3000).rounded())
        assertEquals(CropRect(0f, 0.125f, 1f, 0.875f), fitAspect(16f / 9f, 1200, 900).let { CropRect(0f, it.top, 1f, it.bottom) }.rounded())
        assertEquals(CropRect(), fitAspect(null, 4000, 3000))
    }

    @Test
    fun movingStaysInside() {
        val crop = CropRect(0.25f, 0.25f, 0.75f, 0.75f)
        val moved = dragCrop(crop, CropHandle.Move, 1000f, -1000f, null, 100f, 100f, 10f)
        assertEquals(CropRect(0.5f, 0f, 1f, 0.5f), moved)
    }

    @Test
    fun freeCornerResizes() {
        val crop = CropRect(0.2f, 0.2f, 0.8f, 0.8f)
        val dragged = dragCrop(crop, CropHandle.BottomRight, 10f, -20f, null, 100f, 100f, 10f)
        assertEquals(CropRect(0.2f, 0.2f, 0.9f, 0.6f), dragged.rounded())
        // Can't shrink below the minimum.
        val tiny = dragCrop(crop, CropHandle.TopLeft, 100f, 100f, null, 100f, 100f, 10f)
        assertEquals(CropRect(0.7f, 0.7f, 0.8f, 0.8f), tiny.rounded())
    }

    @Test
    fun lockedCornerKeepsRatio() {
        val crop = CropRect(0f, 0f, 0.5f, 0.5f)
        val dragged = dragCrop(crop, CropHandle.BottomRight, 30f, 0f, 1f, 100f, 100f, 10f)
        assertEquals(CropRect(0f, 0f, 0.8f, 0.8f), dragged.rounded())
        // Too big for the photo: shrinks to fit.
        val big = dragCrop(crop, CropHandle.BottomRight, 500f, 0f, 1f, 100f, 100f, 10f)
        assertEquals(CropRect(0f, 0f, 1f, 1f), big.rounded())
    }

    private fun CropRect.rounded() = CropRect(r(left), r(top), r(right), r(bottom))
    private fun r(v: Float) = Math.round(v * 1000) / 1000f
}
