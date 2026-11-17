package app.roll.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailsTest {
    @Test
    fun cameraNames() {
        assertEquals("Canon EOS R6", cameraName("Canon", "Canon EOS R6"))
        assertEquals("samsung SM-S928B", cameraName("samsung", "SM-S928B"))
        assertEquals("Fairphone", cameraName("Fairphone", null))
        assertEquals("FP5", cameraName(null, "FP5"))
        assertNull(cameraName(null, null))
    }
}
