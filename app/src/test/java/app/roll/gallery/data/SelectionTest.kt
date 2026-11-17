package app.roll.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectionTest {
    private val order = listOf(10L, 11L, 12L, 13L, 14L)

    @Test
    fun dragForwardSelectsRange() {
        assertEquals(setOf(11L, 12L, 13L), dragSelection(emptySet(), order, anchor = 1, current = 3, selecting = true))
    }

    @Test
    fun dragBackwardSelectsRange() {
        assertEquals(setOf(10L, 11L, 12L), dragSelection(emptySet(), order, anchor = 2, current = 0, selecting = true))
    }

    @Test
    fun draggingBackRestoresWhatWasThere() {
        val base = setOf(14L)
        val wide = dragSelection(base, order, anchor = 0, current = 3, selecting = true)
        assertEquals(setOf(10L, 11L, 12L, 13L, 14L), wide)
        assertEquals(setOf(10L, 14L), dragSelection(base, order, anchor = 0, current = 0, selecting = true))
    }

    @Test
    fun startingOnASelectedItemDeselects() {
        val base = order.toSet()
        assertEquals(setOf(10L, 14L), dragSelection(base, order, anchor = 1, current = 3, selecting = false))
    }

    @Test
    fun outOfRangeIsClamped() {
        assertEquals(order.toSet(), dragSelection(emptySet(), order, anchor = -3, current = 99, selecting = true))
    }
}
