package app.roll.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumRulesTest {
    @Test
    fun names() {
        assertNull(albumNameProblem("  Trips ", listOf("Camera")))
        assertEquals(AlbumNameProblem.Blank, albumNameProblem("   ", emptyList()))
        assertEquals(AlbumNameProblem.Blank, albumNameProblem("..", emptyList()))
        assertEquals(AlbumNameProblem.Slash, albumNameProblem("a/b", emptyList()))
        assertEquals(AlbumNameProblem.Taken, albumNameProblem("camera", listOf("Camera")))
    }

    @Test
    fun newAlbumsGoUnderPictures() {
        assertEquals("Pictures/Trips/", newAlbumPath(" Trips "))
    }

    @Test
    fun whereFilesMayGo() {
        assertTrue(canHold("DCIM/Camera/", isVideo = false))
        assertTrue(canHold("Pictures/Trips/", isVideo = true))
        assertTrue(canHold("Movies/", isVideo = true))
        assertFalse(canHold("Movies/", isVideo = false))
        assertFalse(canHold("Download/", isVideo = false))
        assertFalse(canHold("Android/media/com.whatsapp/WhatsApp/Media/", isVideo = false))
    }
}
