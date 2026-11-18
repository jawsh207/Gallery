package app.roll.gallery.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoriesTest {
    private val today = LocalDate.of(2026, 11, 16)

    @Test
    fun groupsByYearWithinTheWindow() {
        val items = listOf(
            testMedia(1, at(2025, 11, 18)),
            testMedia(2, at(2025, 11, 16)),
            testMedia(3, at(2025, 11, 13)),
            testMedia(4, at(2025, 11, 12)), // 4 days early: outside
            testMedia(5, at(2023, 11, 16)),
            testMedia(6, at(2026, 11, 16)), // this year: not a memory
        )
        val memories = memoriesFor(items, today, UTC, minimum = 1)
        assertEquals(listOf(1, 3), memories.map { it.yearsAgo })
        assertEquals(listOf(1L, 2L, 3L), memories[0].items.map { it.id })
        assertEquals(1L, memories[0].cover.id)
    }

    @Test
    fun smallYearsAndScreenshotsAreLeftOut() {
        val items = listOf(
            testMedia(1, at(2025, 11, 16)),
            testMedia(2, at(2025, 11, 16), path = "Pictures/Screenshots/"),
            testMedia(3, at(2025, 11, 16), path = "Pictures/Screenshots/"),
        )
        assertEquals(emptyList<Memory>(), memoriesFor(items, today, UTC, minimum = 2))
    }

    @Test
    fun leapDayMatchesTheTwentyEighth() {
        val leap = LocalDate.of(2028, 2, 29)
        val items = listOf(testMedia(1, at(2027, 2, 28)))
        assertEquals(listOf(1), memoriesFor(items, leap, UTC, minimum = 1).map { it.yearsAgo })
    }

    @Test
    fun recentHighlightsAndBusyDaysJoinOnThisDay() {
        val today = java.time.LocalDate.of(2026, 11, 16)
        val recent = List(10) { testMedia(100L + it, at(2026, 11, 10)) }
        val busy = List(25) { testMedia(200L + it, at(2026, 5, 2)) }
        val memories = allMemories(recent + busy, today, UTC)
        assertEquals(listOf("recent", "day:2026-05-02"), memories.map { it.key })
        assertEquals(10, memories[0].items.size)
    }

    @Test
    fun highlightsKeepFavoritesAndSpreadTheRest() {
        val photos = List(100) { testMedia(it.toLong(), isFavorite = it == 99) }
        val picked = pickHighlights(photos)
        assertEquals(HIGHLIGHT_LIMIT, picked.size)
        assertEquals(true, picked.any { it.id == 99L })
    }
}
