package app.roll.gallery.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FaceIndexTest {
    @Test
    fun linesRoundTrip() {
        val face = StoredFace(0.1f, 0.25f, 0.2f, 0.3f, floatArrayOf(0.5f, -0.5f))
        val entry = FaceEntry(7, 1700000000, listOf(face, face.copy(left = 0.6f)))
        assertEquals(entry, parseFaceLine(formatFaceLine(entry)))
    }

    @Test
    fun photosWithoutFaces() {
        val entry = FaceEntry(7, 1, emptyList())
        assertEquals("7\t1\t", formatFaceLine(entry))
        assertEquals(entry, parseFaceLine("7\t1\t"))
        assertEquals(entry, parseFaceLine("7\t1"))
    }

    @Test
    fun damagedFacesAreSkipped() {
        assertEquals(emptyList<StoredFace>(), parseFaceLine("7\t1\t0.1,0.2:AAAA nonsense")!!.faces)
        assertNull(parseFaceLine("x\t1\t"))
    }
}
