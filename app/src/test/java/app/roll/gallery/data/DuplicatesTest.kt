package app.roll.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicatesTest {
    @Test
    fun hashComparesNeighbours() {
        // Brightness falling left to right in every row: every bit set.
        val falling = IntArray(72) { 255 - (it % 9) * 10 }
        assertEquals(-1L, differenceHash(falling))
        // Rising: no bits set.
        val rising = IntArray(72) { (it % 9) * 10 }
        assertEquals(0L, differenceHash(rising))
    }

    @Test
    fun distanceCountsBits() {
        assertEquals(0, hashDistance(5, 5))
        assertEquals(64, hashDistance(0, -1))
        assertEquals(2, hashDistance(0b1010, 0b0000))
    }

    @Test
    fun exactCopiesGroupAnywhereInTime() {
        val a = testMedia(1, at(2026, 1, 1)).copy(sizeBytes = 500)
        val b = testMedia(2, at(2024, 5, 5)).copy(sizeBytes = 500)
        val c = testMedia(3, at(2025, 3, 3)).copy(sizeBytes = 500)
        val groups = findDuplicates(listOf(a, c, b), mapOf(1L to 0L, 2L to 1L, 3L to -1L))
        assertEquals(1, groups.size)
        assertEquals(DuplicateKind.Exact, groups[0].kind)
        assertEquals(setOf(1L, 2L), groups[0].items.map { it.id }.toSet())
    }

    @Test
    fun similarNeedsCloseTimes() {
        val base = at(2026, 1, 1)
        val a = testMedia(1, base).copy(sizeBytes = 100)
        val b = testMedia(2, base + 30_000).copy(sizeBytes = 200)
        val c = testMedia(3, base + 60 * 60_000).copy(sizeBytes = 300) // an hour later
        val hashes = mapOf(1L to 0L, 2L to 0b111L, 3L to 0L)
        val groups = findDuplicates(listOf(c, b, a), hashes)
        assertEquals(1, groups.size)
        assertEquals(DuplicateKind.Similar, groups[0].kind)
        assertEquals(listOf(2L, 1L), groups[0].items.map { it.id })
    }

    @Test
    fun keepsFavoriteThenBiggest() {
        val small = testMedia(1).copy(width = 100, height = 100)
        val big = testMedia(2).copy(width = 4000, height = 3000)
        val favorite = testMedia(3, isFavorite = true).copy(width = 10, height = 10)
        assertEquals(2L, suggestKeep(listOf(small, big)).id)
        assertEquals(3L, suggestKeep(listOf(small, big, favorite)).id)
    }

    @Test
    fun videosAndUnhashedAreSkipped() {
        val a = testMedia(1, isVideo = true).copy(sizeBytes = 1)
        val b = testMedia(2, isVideo = true).copy(sizeBytes = 1)
        val c = testMedia(3).copy(sizeBytes = 1)
        assertEquals(emptyList<DuplicateGroup>(), findDuplicates(listOf(a, b, c), mapOf(1L to 0L, 2L to 0L)))
    }
}
