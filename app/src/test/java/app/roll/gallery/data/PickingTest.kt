package app.roll.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PickingTest {
    @Test
    fun kindsFromTypes() {
        assertEquals(PickKinds(images = true, videos = false), pickKinds(listOf("image/*")))
        assertEquals(PickKinds(images = true, videos = false), pickKinds(listOf("image/png")))
        assertEquals(PickKinds(images = false, videos = true), pickKinds(listOf("video/*")))
        assertEquals(PickKinds(images = true, videos = true), pickKinds(listOf("image/*", "video/*")))
        assertEquals(PickKinds(images = true, videos = false), pickKinds(listOf("vnd.android.cursor.dir/image")))
        assertEquals(PickKinds(images = true, videos = true), pickKinds(listOf("*/*")))
        assertEquals(PickKinds(images = true, videos = true), pickKinds(emptyList()))
    }

    @Test
    fun acceptsByKind() {
        val photosOnly = PickKinds(images = true, videos = false)
        assertTrue(photosOnly.accepts(testMedia(1)))
        assertFalse(photosOnly.accepts(testMedia(2, isVideo = true)))
    }
}
