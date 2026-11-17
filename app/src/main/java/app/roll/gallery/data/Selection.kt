package app.roll.gallery.data

/**
 * The selection while dragging across the grid after a long press. Every item between the one
 * pressed ([anchor]) and the one under the finger ([current]), in grid order ([order]), is
 * added to the selection the drag started from ([base]), or removed from it when the drag
 * began on an already selected item ([selecting] false). Dragging back shrinks the range and
 * restores what was there before, as in other gallery apps.
 */
fun dragSelection(base: Set<Long>, order: List<Long>, anchor: Int, current: Int, selecting: Boolean): Set<Long> {
    if (order.isEmpty()) return base
    val from = minOf(anchor, current).coerceIn(0, order.lastIndex)
    val to = maxOf(anchor, current).coerceIn(0, order.lastIndex)
    val range = order.subList(from, to + 1)
    return if (selecting) base + range else base - range.toSet()
}
