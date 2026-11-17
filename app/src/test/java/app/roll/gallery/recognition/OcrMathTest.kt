package app.roll.gallery.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrMathTest {
    @Test
    fun finderSizesAreMultiplesOf32() {
        assertEquals(960 to 736, finderInputSize(4000, 3000))
        assertEquals(320 to 224, finderInputSize(320, 230))
        assertEquals(32 to 32, finderInputSize(10, 10))
    }

    /** A map with value 0.9 inside the given rectangles (x0, y0, x1, y1 exclusive) and 0 elsewhere. */
    private fun map(width: Int, height: Int, vararg rects: IntArray) = FloatArray(width * height).also { m ->
        for (r in rects) for (y in r[1] until r[3]) for (x in r[0] until r[2]) m[y * width + x] = 0.9f
    }

    @Test
    fun findsSeparateRegions() {
        val m = map(40, 20, intArrayOf(2, 2, 12, 6), intArrayOf(20, 2, 35, 6), intArrayOf(2, 12, 30, 16))
        val boxes = findTextBoxes(m, 40, 20, unclipRatio = 0f)
        assertEquals(3, boxes.size)
        // Reading order: the two on the top line left to right, then the bottom one.
        assertEquals(listOf(2f, 20f, 2f), boxes.map { it.left })
        assertEquals(TextBox(2f, 2f, 12f, 6f, 0.9f), boxes[0])
    }

    @Test
    fun dropsFaintAndTinyRegions() {
        val m = map(20, 20, intArrayOf(1, 1, 3, 3))
        assertEquals(0, findTextBoxes(m, 20, 20).size)
        val faint = FloatArray(400) { 0.4f }
        assertEquals(0, findTextBoxes(faint, 20, 20).size)
    }

    @Test
    fun growsBoxesWithinTheImage() {
        val m = map(40, 20, intArrayOf(0, 5, 10, 9))
        val box = findTextBoxes(m, 40, 20, unclipRatio = 1.6f).single()
        assertEquals(0f, box.left, 0f)
        assertTrue(box.top < 5f && box.bottom > 9f && box.right > 10f)
    }

    @Test
    fun readerWidthKeepsShape() {
        assertEquals(240, readerInputWidth(500, 100))
        assertEquals(1280, readerInputWidth(100000, 100))
        assertEquals(8, readerInputWidth(1, 100))
    }

    @Test
    fun decodesMergingRepeatsAndBlanks() {
        val chars = listOf("a", "b", " ")
        // Slots: 0 blank, 1 a, 2 b, 3 space. Slices: a a blank a b space b.
        val best = intArrayOf(1, 1, 0, 1, 2, 3, 2)
        val classes = 4
        val scores = FloatArray(best.size * classes).also { s -> best.forEachIndexed { t, c -> s[t * classes + c] = 0.8f } }
        val (text, confidence) = decodeCharacters(scores, best.size, classes, chars)
        assertEquals("aab b", text)
        assertEquals(0.8f, confidence, 0.0001f)
    }

    @Test
    fun charactersEndWithSpace() {
        assertEquals(listOf("0", "1", "a", " "), readerCharacters("0\r\n1\na\n\n"))
    }
}
