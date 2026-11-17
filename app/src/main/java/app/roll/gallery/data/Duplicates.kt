package app.roll.gallery.data

/** Whether a group of photos are copies of one file or just look alike (a burst, say). */
enum class DuplicateKind { Exact, Similar }

/**
 * Photos that look like the same picture. [keep] is the one suggested to keep; the rest are
 * offered for the trash.
 */
data class DuplicateGroup(val kind: DuplicateKind, val items: List<Media>, val keep: Media)

/** Hashes this close (out of 64 bits) count as copies of the same file. */
const val EXACT_DISTANCE = 2

/** Hashes this close count as similar, if they were also taken close together. */
const val SIMILAR_DISTANCE = 8

/** How close in time similar photos must be taken: bursts and retakes, not lookalike scenes days apart. */
const val SIMILAR_WINDOW_MILLIS = 2 * 60 * 1000L

/**
 * A 64-bit "difference hash" of a photo from its brightness shrunk to 9 × 8 pixels ([gray], row
 * by row): each bit says whether a pixel is brighter than the one to its right. Photos that look
 * the same get the same or nearly the same bits, even after resizing or recompressing.
 */
fun differenceHash(gray: IntArray): Long {
    require(gray.size == 72) { "needs 9 × 8 values" }
    var hash = 0L
    var bit = 0
    for (row in 0 until 8) {
        for (col in 0 until 8) {
            if (gray[row * 9 + col] > gray[row * 9 + col + 1]) hash = hash or (1L shl bit)
            bit++
        }
    }
    return hash
}

/** How many bits two hashes differ by: 0 for the same picture, up to 64. */
fun hashDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

/**
 * Groups photos ([items], newest first) that look the same, using each one's [hashes] (by id;
 * photos without one are skipped). Copies of one file (the same size in bytes and almost the
 * same hash) are [DuplicateKind.Exact], wherever and whenever they are. Otherwise, photos taken
 * within [SIMILAR_WINDOW_MILLIS] of each other with close hashes are [DuplicateKind.Similar].
 * Each photo is in at most one group, exact ones first.
 */
fun findDuplicates(items: List<Media>, hashes: Map<Long, Long>): List<DuplicateGroup> {
    val photos = items.filter { !it.isVideo && it.id in hashes }
    val used = HashSet<Long>()
    val groups = ArrayList<DuplicateGroup>()

    for ((_, sameSize) in photos.groupBy { it.sizeBytes }) {
        if (sameSize.size < 2) continue
        for (cluster in cluster(sameSize) { a, b -> hashDistance(hashes.getValue(a.id), hashes.getValue(b.id)) <= EXACT_DISTANCE }) {
            groups += DuplicateGroup(DuplicateKind.Exact, cluster, suggestKeep(cluster))
            cluster.mapTo(used) { it.id }
        }
    }

    val rest = photos.filter { it.id !in used }.sortedBy { it.takenAt }
    val linked = ArrayList<Pair<Int, Int>>()
    for (i in rest.indices) {
        var j = i + 1
        while (j < rest.size && rest[j].takenAt - rest[i].takenAt <= SIMILAR_WINDOW_MILLIS) {
            if (hashDistance(hashes.getValue(rest[i].id), hashes.getValue(rest[j].id)) <= SIMILAR_DISTANCE) linked += i to j
            j++
        }
    }
    for (cluster in components(rest.size, linked)) {
        if (cluster.size < 2) continue
        val members = cluster.map { rest[it] }.sortedByDescending { it.takenAt }
        groups += DuplicateGroup(DuplicateKind.Similar, members, suggestKeep(members))
    }
    return groups.sortedByDescending { group -> group.items.maxOf { it.takenAt } }
}

/** Which photo of a group to keep: a favorite, then the most pixels, then the biggest file, then the oldest. */
fun suggestKeep(items: List<Media>): Media = items.sortedWith(
    compareByDescending<Media> { it.isFavorite }
        .thenByDescending { it.width.toLong() * it.height }
        .thenByDescending { it.sizeBytes }
        .thenBy { it.takenAt },
).first()

/** Splits [items] into groups of two or more linked (directly or through others) by [close]. */
private fun cluster(items: List<Media>, close: (Media, Media) -> Boolean): List<List<Media>> {
    val links = ArrayList<Pair<Int, Int>>()
    for (i in items.indices) for (j in i + 1 until items.size) if (close(items[i], items[j])) links += i to j
    return components(items.size, links).filter { it.size >= 2 }.map { c -> c.map { items[it] } }
}

/** The connected groups of [count] points joined by [links], each as a list of indexes in order. */
private fun components(count: Int, links: List<Pair<Int, Int>>): List<List<Int>> {
    val parent = IntArray(count) { it }
    fun root(x: Int): Int {
        var r = x
        while (parent[r] != r) r = parent[r]
        var y = x
        while (parent[y] != r) { val next = parent[y]; parent[y] = r; y = next }
        return r
    }
    for ((a, b) in links) parent[root(a)] = root(b)
    return (0 until count).groupBy { root(it) }.values.map { it.sorted() }
}
