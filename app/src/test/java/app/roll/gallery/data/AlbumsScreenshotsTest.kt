package app.roll.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumsScreenshotsTest {
    @Test
    fun knowsScreenshotFolders() {
        assertTrue(isScreenshotFolder("Pictures/Screenshots/"))
        assertTrue(isScreenshotFolder("DCIM/Screenshots/"))
        assertTrue(isScreenshotFolder("Movies/Screen recordings/"))
        assertFalse(isScreenshotFolder("DCIM/Camera/"))
    }

    @Test
    fun recentlyAddedGoesByArrival() {
        val day = 24 * 60 * 60L
        val now = 100 * day
        val old = testMedia(1).copy(addedAt = now - 40 * day)
        val newer = testMedia(2).copy(addedAt = now - 2 * day)
        val newest = testMedia(3).copy(addedAt = now - day)
        val library = Library(listOf(old, newer, newest), emptyList())
        assertEquals(listOf(3L, 2L), library.recentlyAdded(now).map { it.id })
    }
}
