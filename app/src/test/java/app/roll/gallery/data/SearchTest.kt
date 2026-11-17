package app.roll.gallery.data

import java.time.Month
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTest {
    private val items = listOf(
        testMedia(1, at(2026, 6, 3), albumName = "Camera", path = "DCIM/Camera/"),
        testMedia(2, at(2025, 6, 10), albumName = "Screenshots", path = "Pictures/Screenshots/"),
        testMedia(3, at(2025, 12, 24), albumName = "Trips", path = "Pictures/Trips/", isVideo = true, isFavorite = true),
        testMedia(4, at(2024, 1, 5), albumName = "WhatsApp Images", path = "Pictures/WhatsApp Images/"),
    )

    private fun ids(query: String) =
        searchMedia(items, query, SearchWords.ENGLISH, Locale.ENGLISH, UTC).map { it.id }

    @Test
    fun blankFindsNothing() {
        assertEquals(emptyList<Long>(), ids("   "))
    }

    @Test
    fun kindWords() {
        assertEquals(listOf(3L), ids("videos"))
        assertEquals(listOf(1L, 2L, 4L), ids("photos"))
        assertEquals(listOf(3L), ids("Favorites"))
        assertEquals(listOf(2L), ids("screenshots"))
    }

    @Test
    fun yearsAndMonths() {
        assertEquals(listOf(2L, 3L), ids("2025"))
        assertEquals(listOf(1L, 2L), ids("june"))
        assertEquals(listOf(1L, 2L), ids("Jun"))
        assertEquals(listOf(2L), ids("june 2025"))
    }

    @Test
    fun textMatchesNamesAndFolders() {
        assertEquals(listOf(4L), ids("whatsapp"))
        assertEquals(listOf(3L), ids("trip"))
        assertEquals(listOf(1L), ids("IMG_1"))
        assertEquals(listOf(1L, 2L, 4L), ids("jpeg"))
    }

    @Test
    fun everyWordMustMatch() {
        assertEquals(emptyList<Long>(), ids("trips 2024"))
    }

    @Test
    fun monthNamesIncludeShortForms() {
        val months = monthNames(Locale.ENGLISH)
        assertEquals(Month.JANUARY, months["jan"])
        assertEquals(Month.MAY, months["may"])
    }

    @Test
    fun textReadInPhotosMatches() {
        val text = mapOf(1L to "open daily 9 to 5", 3L to "boarding pass")
        val found = searchMedia(items, "boarding", SearchWords.ENGLISH, Locale.ENGLISH, UTC) { text[it] }.map { it.id }
        assertEquals(listOf(3L), found)
        // Every word must match somewhere: "open" in the text, "camera" in the album.
        val both = searchMedia(items, "open camera", SearchWords.ENGLISH, Locale.ENGLISH, UTC) { text[it] }.map { it.id }
        assertEquals(listOf(1L), both)
    }
}
