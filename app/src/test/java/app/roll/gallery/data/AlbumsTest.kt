package app.roll.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumsTest {
    @Test
    fun cameraThenScreenshotsThenNewest() {
        val items = listOf(
            testMedia(1, at(2026, 11, 16), albumId = 3, albumName = "WhatsApp Images", path = "Pictures/WhatsApp Images/"),
            testMedia(2, at(2026, 11, 15), albumId = 2, albumName = "Screenshots", path = "Pictures/Screenshots/"),
            testMedia(3, at(2026, 11, 14), albumId = 4, albumName = "Download", path = "Download/"),
            testMedia(4, at(2026, 11, 1), albumId = 1, albumName = "Camera", path = "DCIM/Camera/"),
            testMedia(5, at(2026, 10, 1), albumId = 1, albumName = "Camera", path = "DCIM/Camera/"),
        )
        val albums = buildAlbums(items)
        assertEquals(listOf("Camera", "Screenshots", "WhatsApp Images", "Download"), albums.map { it.name })
        assertEquals(2, albums[0].count)
        assertEquals(4L, albums[0].cover.id)
    }

    @Test
    fun unnamedFolderUsesItsPath() {
        val albums = buildAlbums(listOf(testMedia(1, albumName = "", path = "Pictures/Trips/")))
        assertEquals("Trips", albums.single().name)
    }

    @Test
    fun rankIgnoresCaseAndSlash() {
        assertEquals(0, albumRank("DCIM/Camera"))
        assertEquals(1, albumRank("dcim/screenshots/"))
        assertEquals(2, albumRank("DCIM/Camera/Raw/"))
    }
}
