package app.roll.gallery.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextIndexTest {
    @Test
    fun linesRoundTrip() {
        val entry = IndexEntry(42, 1700000000, "OPEN 9\tto 5\nSUNDAY")
        val line = formatIndexLine(entry)
        assertEquals("42\t1700000000\tOPEN 9 to 5 SUNDAY", line)
        assertEquals(IndexEntry(42, 1700000000, "OPEN 9 to 5 SUNDAY"), parseIndexLine(line))
    }

    @Test
    fun emptyTextIsKept() {
        assertEquals(IndexEntry(1, 2, ""), parseIndexLine("1\t2\t"))
        assertEquals(IndexEntry(1, 2, ""), parseIndexLine("1\t2"))
    }

    @Test
    fun damagedLinesAreSkipped() {
        assertNull(parseIndexLine(""))
        assertNull(parseIndexLine("abc\t2\ttext"))
        assertNull(parseIndexLine("1"))
    }
}
