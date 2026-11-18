package app.roll.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreationsTest {
    private fun area(cells: List<Cell>) = cells.sumOf { (it.width * it.height).toDouble() }

    @Test
    fun everyLayoutFillsThePictureWithTheRightNumberOfCells() {
        for (count in 2..9) {
            val layouts = collageLayouts(count)
            assertTrue(layouts.isNotEmpty())
            for (cells in layouts) {
                assertEquals(count, cells.size)
                assertEquals(1.0, area(cells), 1e-4)
                assertTrue(cells.all { it.left >= 0f && it.top >= 0f && it.right <= 1.0001f && it.bottom <= 1.0001f })
            }
        }
    }

    @Test
    fun splitsAsEvenlyAsPossible() {
        assertEquals(listOf(2, 3), evenSplit(5, 2))
        assertEquals(listOf(2, 2, 3), evenSplit(7, 3))
    }

    @Test
    fun cropsTheMiddleToTheCellsShape() {
        val wide = centreCrop(4000, 2000, 1f, 1f)
        assertEquals(1000f, wide[0])
        assertEquals(3000f, wide[2])
        val tall = centreCrop(1000, 3000, 1f, 1f)
        assertEquals(1000f, tall[1])
        assertEquals(2000f, tall[3])
    }

    @Test
    fun animationsCanPlayThereAndBack() {
        assertEquals(listOf(0, 1, 2, 3), animationOrder(4, backAndForth = false))
        assertEquals(listOf(0, 1, 2, 3, 2, 1), animationOrder(4, backAndForth = true))
        assertEquals(500L, animationFrameMs(0f))
        assertEquals(100L, animationFrameMs(1f))
    }

    @Test
    fun gapsAreEvenInsideAndAround() {
        val left = cellRect(Cell(0f, 0f, 0.5f, 1f), 1000f, 1000f, 20f)
        val right = cellRect(Cell(0.5f, 0f, 1f, 1f), 1000f, 1000f, 20f)
        assertEquals(20f, left[0], 0.01f)
        assertEquals(20f, right[0] - left[2], 0.01f)
        assertEquals(20f, 1000f - right[2], 0.01f)
    }

    @Test
    fun namesCarryTheTime() {
        val millis = java.time.LocalDateTime.of(2026, 11, 17, 19, 45, 12).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        assertEquals("Collage_20261117_194512.jpg", creationName("Collage", "jpg", millis, java.time.ZoneOffset.UTC))
    }
}
