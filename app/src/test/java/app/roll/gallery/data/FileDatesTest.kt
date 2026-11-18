package app.roll.gallery.data

import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileDatesTest {
    private val now = LocalDateTime.of(2026, 11, 17, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun at(y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0, s: Int = 0) =
        LocalDateTime.of(y, mo, d, h, mi, s).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun date(name: String) = dateFromName(name, ZoneOffset.UTC, now)

    @Test
    fun readsCameraAndAppNames() {
        assertEquals(at(2024, 5, 12, 14, 30), date("IMG_20240512_143000.jpg"))
        assertEquals(at(2024, 5, 12, 14, 30), date("PXL_20240512_143000123.jpg"))
        assertEquals(at(2024, 5, 12, 14, 30), date("Screenshot_2024-05-12-14-30-00.png"))
        assertEquals(at(2024, 5, 12, 14, 30, 5), date("signal-2024-05-12-143005.jpg"))
        assertEquals(at(2024, 5, 12), date("IMG-20240512-WA0001.jpg"))
        assertEquals(at(2019, 3, 1, 8, 15, 22), date("20190301_081522.jpg"))
    }

    @Test
    fun readsMillisecondNames() {
        assertEquals(1715524200000L, date("1715524200000.jpg"))
    }

    @Test
    fun ignoresNamesWithoutBelievableDates() {
        assertNull(date("holiday.jpg"))
        assertNull(date("IMG_1234.jpg"))
        assertNull(date("IMG_20991231_000000.jpg"))
        assertNull(date("IMG_20241332_000000.jpg"))
        assertNull(date("photo_12345678901234567.jpg"))
    }

    @Test
    fun writesExifDates() {
        val (date, offset) = exifDateTime(at(2024, 5, 12, 14, 30), java.time.ZoneOffset.ofHours(-4))
        assertEquals("2024:05:12 10:30:00", date)
        assertEquals("-04:00", offset)
    }
}
