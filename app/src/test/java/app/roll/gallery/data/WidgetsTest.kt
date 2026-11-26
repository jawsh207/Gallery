package app.roll.gallery.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetsTest {
    @Test
    fun frameSourcesRoundTrip() {
        for (source in listOf(FrameSource.Favorites, FrameSource.All, FrameSource.Album(42))) {
            assertEquals(source, decodeFrameSource(source.encode()))
        }
        assertEquals(FrameSource.Favorites, decodeFrameSource(null))
        assertEquals(FrameSource.Favorites, decodeFrameSource("album:nope"))
        assertEquals(FrameSource.Favorites, decodeFrameSource("something else"))
    }

    @Test
    fun favoritesSkipVideosAndFallBackToEverything() {
        val library = Library(
            items = listOf(testMedia(1, isFavorite = true, isVideo = true), testMedia(2), testMedia(3)),
            trash = emptyList(),
        )
        val photos = framePhotos(library, FrameSource.Favorites)
        assertEquals(MediaSet.All, photos.set)
        assertEquals(listOf(2L, 3L), photos.items.map { it.id })

        val withFavorite = library.copy(items = library.items + testMedia(4, isFavorite = true))
        assertEquals(FramePhotos(MediaSet.Favorites, listOf(withFavorite.items.last())), framePhotos(withFavorite, FrameSource.Favorites))
    }

    @Test
    fun albumsAndFoldersKeptOutOfPhotos() {
        val library = Library(
            items = listOf(testMedia(1, albumId = 1), testMedia(2, albumId = 2), testMedia(3, albumId = 2)),
            trash = emptyList(),
            albumPrefs = AlbumPrefs(hidden = setOf(2)),
        )
        assertEquals(listOf(2L, 3L), framePhotos(library, FrameSource.Album(2)).items.map { it.id })
        // Every photo means the Photos tab's photos, so the hidden folder stays out.
        assertEquals(listOf(1L), framePhotos(library, FrameSource.All).items.map { it.id })
        // An album that's gone falls back to every photo.
        assertEquals(FramePhotos(MediaSet.All, library.items.take(1)), framePhotos(library, FrameSource.Album(9)))
    }

    @Test
    fun framePickVisitsEveryPhotoBeforeRepeating() {
        val items = (1L..10L).map { testMedia(it) }
        val shown = (0L until 10L).map { framePick(items, widgetId = 3, slot = it)!!.id }.toSet()
        assertEquals(10, shown.size)
        // Same slot, same photo; different frames start apart.
        assertEquals(framePick(items, 3, 5), framePick(items, 3, 5))
        assertEquals(false, framePick(items, 3, 5) == framePick(items, 4, 5))
        assertNull(framePick(emptyList(), 1, 1))
    }

    @Test
    fun framePickStillVisitsEverythingWhenTheStepDividesTheCount() {
        val items = (1L..7919L).map { testMedia(it) }
        val shown = (0L until 7919L).map { framePick(items, 0, it)!!.id }.toSet()
        assertEquals(7919, shown.size)
    }

    @Test
    fun frameSlotsLastThreeHours() {
        val hour = 60L * 60 * 1000
        assertEquals(frameSlot(0), frameSlot(3 * hour - 1))
        assertEquals(frameSlot(0) + 1, frameSlot(3 * hour))
    }

    @Test
    fun widgetPrefersOnThisDayThenHighlightsThenBusyDays() {
        val photo = listOf(testMedia(1))
        val recent = Memory(0, photo, recent = true)
        val busy = Memory(0, photo, day = LocalDate.of(2026, 5, 12))
        val year = Memory(2, photo)
        assertEquals(year, widgetMemory(listOf(recent, year, busy)))
        assertEquals(recent, widgetMemory(listOf(busy, recent)))
        assertEquals(busy, widgetMemory(listOf(busy)))
        assertNull(widgetMemory(emptyList()))
    }
}
