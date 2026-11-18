package app.roll.gallery.data

/** The tiles at the top of the Collections tab, in their usual order. Their order can be changed. */
enum class CollectionTile { Favorites, Videos, Screenshots, RecentlyAdded, Duplicates, Locked, Trash }

/**
 * The tiles in the order [saved] (tile names, as kept in settings) puts them: those named first,
 * in that order, then any not named (new ones, or all of them when nothing was saved) in their
 * usual order. Unknown and repeated names are ignored.
 */
fun orderTiles(saved: List<String>): List<CollectionTile> {
    val named = saved.mapNotNull { name -> CollectionTile.entries.firstOrNull { it.name == name } }.distinct()
    return named + CollectionTile.entries.filter { it !in named }
}

/** [tiles] with the one at [index] moved one place earlier ([by] -1) or later ([by] 1), if it can be. */
fun moveTile(tiles: List<CollectionTile>, index: Int, by: Int): List<CollectionTile> {
    val target = index + by
    if (index !in tiles.indices || target !in tiles.indices) return tiles
    return tiles.toMutableList().apply { add(target, removeAt(index)) }
}
