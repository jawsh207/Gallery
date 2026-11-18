package app.roll.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionTilesTest {
    @Test
    fun usualOrderWhenNothingSaved() {
        assertEquals(CollectionTile.entries, orderTiles(emptyList()))
    }

    @Test
    fun savedOrderFirstThenTheRest() {
        val order = orderTiles(listOf("Trash", "Favorites", "Nope", "Trash"))
        assertEquals(CollectionTile.Trash, order[0])
        assertEquals(CollectionTile.Favorites, order[1])
        assertEquals(CollectionTile.Videos, order[2])
        assertEquals(CollectionTile.entries.size, order.size)
    }

    @Test
    fun movesOnePlaceAndStopsAtTheEnds() {
        val tiles = CollectionTile.entries
        assertEquals(CollectionTile.Videos, moveTile(tiles, 1, -1)[0])
        assertEquals(tiles, moveTile(tiles, 0, -1))
        assertEquals(tiles, moveTile(tiles, tiles.lastIndex, 1))
    }
}
