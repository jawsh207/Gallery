package app.roll.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavingTest {
    @Test
    fun editedNames() {
        assertEquals("IMG_1234-edited.jpg", editedName("IMG_1234.jpg", "jpg"))
        assertEquals("PXL_1-edited.jpg", editedName("PXL_1.heic", "jpg"))
        assertEquals("scan-edited.png", editedName("scan", "png"))
        assertEquals("photo-edited.jpg", editedName(".jpg", "jpg"))
    }

    @Test
    fun overwritableTypes() {
        assertTrue(canOverwrite("image/jpeg"))
        assertTrue(canOverwrite("IMAGE/PNG"))
        assertFalse(canOverwrite("image/heic"))
        assertFalse(canOverwrite("image/gif"))
    }
}
