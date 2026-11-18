package app.roll.gallery.data

/**
 * Photos taken in quick succession, shown as one tile in the Photos grid: a camera burst, or
 * several tries at the same shot. [cover] is the one shown; the rest are tucked behind it.
 */
data class Stack(val cover: Media, val members: List<Media>)

/** The longest gap between shots in one stack, in milliseconds. */
const val STACK_GAP_MS = 2_000L

/**
 * Groups [items] (newest first) into [Stack]s: two or more photos in the same folder, each
 * taken within [gapMs] of the one before. Videos never stack. The cover is the shot a burst
 * marks as its cover (a name containing "COVER", as Pixel cameras write), or else the first
 * (newest) one.
 */
fun buildStacks(items: List<Media>, gapMs: Long = STACK_GAP_MS): List<Stack> {
    val stacks = ArrayList<Stack>()
    var run = ArrayList<Media>()
    fun close() {
        if (run.size >= 2) {
            val cover = run.firstOrNull { "COVER" in it.fileName.uppercase() } ?: run.first()
            stacks += Stack(cover, run)
        }
        run = ArrayList()
    }
    for (item in items) {
        val last = run.lastOrNull()
        val joins = last != null && !item.isVideo && item.albumId == last.albumId && last.takenAt - item.takenAt in 0..gapMs
        if (!joins) {
            close()
            if (!item.isVideo) run += item
        } else {
            run += item
        }
    }
    close()
    return stacks
}

/**
 * [items] as the Photos grid shows them with [stacks]: every stack's other shots left out,
 * so only its cover shows.
 */
fun withoutStacked(items: List<Media>, stacks: List<Stack>): List<Media> {
    if (stacks.isEmpty()) return items
    val tucked = stacks.flatMap { stack -> stack.members.filter { it.id != stack.cover.id }.map { it.id } }.toHashSet()
    return items.filter { it.id !in tucked }
}
