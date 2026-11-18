package app.roll.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StacksTest {
    private val t = at(2026, 11, 16, 12)

    @Test
    fun quickShotsInOneFolderStack() {
        val items = listOf(
            testMedia(1, t),
            testMedia(2, t - 500),
            testMedia(3, t - 1500),
            testMedia(4, t - 60_000),
            testMedia(5, t - 61_000, albumId = 2),
            testMedia(6, t - 61_500, isVideo = true),
        )
        val stacks = buildStacks(items)
        assertEquals(1, stacks.size)
        assertEquals(listOf(1L, 2L, 3L), stacks[0].members.map { it.id })
        assertEquals(listOf(1L, 4L, 5L, 6L), withoutStacked(items, stacks).map { it.id })
    }

    @Test
    fun aBurstsCoverIsShown() {
        val items = listOf(testMedia(1, t), testMedia(2, t - 300).copy(fileName = "PXL_BURST_COVER.jpg"))
        assertEquals(2L, buildStacks(items)[0].cover.id)
    }
}
