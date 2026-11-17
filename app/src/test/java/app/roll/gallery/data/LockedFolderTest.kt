package app.roll.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LockedFolderTest {
    @Test
    fun restoresToTheOriginalFolderWhenAllowed() {
        assertEquals("DCIM/Camera/", restorePath("DCIM/Camera/", isVideo = false))
        assertEquals("Movies/", restorePath("Movies/", isVideo = true))
    }

    @Test
    fun otherwiseToPictures() {
        assertEquals("Pictures/", restorePath("Download/", isVideo = false))
        assertEquals("Pictures/", restorePath("Movies/", isVideo = false))
        assertEquals("Pictures/", restorePath("", isVideo = false))
    }
}
