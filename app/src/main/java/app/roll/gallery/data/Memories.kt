package app.roll.gallery.data

import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Photos from around today's date in an earlier year, shown as an "On this day" card. */
data class Memory(val yearsAgo: Int, val items: List<Media>) {
    /** The newest photo, used as the card's cover. */
    val cover: Media get() = items.first()
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
