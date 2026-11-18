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

    private val items = listOf(
        testMedia(1, at(2026, 11, 10), albumId = 1, albumName = "Camera", path = "DCIM/Camera/"),
        testMedia(2, at(2026, 11, 9), albumId = 2, albumName = "Zoo", path = "Pictures/Zoo/"),
        testMedia(3, at(2026, 11, 8), albumId = 3, albumName = "apples", path = "Pictures/apples/"),
        testMedia(4, at(2026, 11, 7), albumId = 2, albumName = "Zoo", path = "Pictures/Zoo/"),
    )

    @Test
    fun pinnedAlbumsComeFirstThenTheChosenOrder() {
        val recent = buildAlbums(items, AlbumPrefs(pinned = listOf(3)))
        assertEquals(listOf(3L, 1L, 2L), recent.map { it.id })
        val byName = buildAlbums(items, AlbumPrefs(sort = AlbumSort.Name))
        assertEquals(listOf("apples", "Camera", "Zoo"), byName.map { it.name })
    }

    @Test
    fun chosenCoversAreUsedWhileTheyreThere() {
        assertEquals(4L, buildAlbums(items, AlbumPrefs(covers = mapOf(2L to 4L))).first { it.id == 2L }.cover.id)
        assertEquals(2L, buildAlbums(items, AlbumPrefs(covers = mapOf(2L to 99L))).first { it.id == 2L }.cover.id)
    }

    @Test
    fun hiddenFoldersLeaveThePhotosGridButStayAlbums() {
        val library = Library(items, emptyList(), albumPrefs = AlbumPrefs(hidden = setOf(2L)))
        assertEquals(listOf(1L, 3L), library.timeline.map { it.id })
        assertEquals(3, library.albums.size)
        assertFalse(library.albums.first { it.id == 2L }.inPhotos)
    }
}
