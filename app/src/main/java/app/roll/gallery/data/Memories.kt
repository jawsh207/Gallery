package app.roll.gallery.data

import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * A memory card at the top of Photos: photos from around today's date in an earlier year
 * ([yearsAgo], "On this day"), the last two weeks' highlights ([recent]), or a busy day from the
 * past year ([day]). [key] names it for opening.
 */
data class Memory(val yearsAgo: Int, val items: List<Media>, val recent: Boolean = false, val day: LocalDate? = null) {
    /** The newest photo, used as the card's cover. */
    val cover: Media get() = items.first()

    /** What names this memory, for opening it again: "year:3", "recent" or "day:2026-05-12". */
    val key: String get() = when {
        recent -> "recent"
        day != null -> "day:$day"
        else -> "year:$yearsAgo"
    }
}

/** How far back Recent highlights look, in days. */
const val RECENT_HIGHLIGHT_DAYS = 14L

/** The most photos a highlights memory shows. */
const val HIGHLIGHT_LIMIT = 40

/**
 * Every memory for [today]: the last [RECENT_HIGHLIGHT_DAYS] days' highlights first (when
 * there are at least [minimum] × 3 photos), then On this day for each earlier year
 * ([memoriesFor]), then up to two of the past year's busiest days (at least [busyDay] photos,
 * longer ago than the highlights reach). Highlights favour favorites, then spread evenly over the
 * rest so one busy afternoon doesn't fill them.
 */
fun allMemories(items: List<Media>, today: LocalDate, zone: ZoneId, minimum: Int = 3, busyDay: Int = 20): List<Memory> {
    val photos = items.filter { !it.isVideo && !isScreenshot(it) }
    val recentStart = today.minusDays(RECENT_HIGHLIGHT_DAYS)
    val recent = photos.filter { it.day(zone) > recentStart && it.day(zone) <= today }
    val highlights = if (recent.size >= minimum * 3) listOf(Memory(0, pickHighlights(recent), recent = true)) else emptyList()
    val busy = photos
        .filter { val d = it.day(zone); d <= recentStart && d > today.minusYears(1) }
        .groupBy { it.day(zone) }
        .filterValues { it.size >= busyDay }
        .entries.sortedByDescending { it.value.size }
        .take(2)
        .map { (day, media) -> Memory(0, pickHighlights(media), day = day) }
        .sortedByDescending { it.day }
    return highlights + memoriesFor(items, today, zone, minimum) + busy
}

/** Up to [HIGHLIGHT_LIMIT] of [photos] (newest first): favorites first, then evenly spread, kept newest first. */
internal fun pickHighlights(photos: List<Media>): List<Media> {
    if (photos.size <= HIGHLIGHT_LIMIT) return photos
    val favorites = photos.filter { it.isFavorite }.take(HIGHLIGHT_LIMIT)
    val room = HIGHLIGHT_LIMIT - favorites.size
    val rest = photos.filter { !it.isFavorite }
    val step = rest.size.toFloat() / room.coerceAtLeast(1)
    val spread = if (room <= 0) emptyList() else List(room) { rest[(it * step).toInt().coerceAtMost(rest.lastIndex)] }
    val chosen = (favorites + spread).toHashSet()
    return photos.filter { it in chosen }
}

/** How many days either side of today's date still count for a memory. */
const val MEMORY_WINDOW_DAYS = 3

/**
 * The "On this day" memories for [today]: for each earlier year, the photos taken within
 * [MEMORY_WINDOW_DAYS] days of the same date, newest year first. Screenshots are left out (they
 * rarely make good memories), as are years with fewer than [minimum] photos. February 29th is
 * matched against the 28th in other years.
 */
fun memoriesFor(items: List<Media>, today: LocalDate, zone: ZoneId, minimum: Int = 3): List<Memory> {
    val byYear = HashMap<Int, MutableList<Media>>()
    for (item in items) {
        if (isScreenshot(item)) continue
        val day = item.day(zone)
        val yearsAgo = today.year - day.year
        if (yearsAgo <= 0) continue
        val anniversary = MonthDay.from(today).atYear(day.year)
        if (abs(ChronoUnit.DAYS.between(anniversary, day)) <= MEMORY_WINDOW_DAYS) {
            byYear.getOrPut(yearsAgo) { ArrayList() } += item
        }
    }
    return byYear.filterValues { it.size >= minimum }
        .map { (years, media) -> Memory(years, media) }
        .sortedBy { it.yearsAgo }
}
