package app.roll.gallery.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * How dense the photo grid is. Pinching out moves towards [Large], pinching in towards
 * [Months]; the coarsest level groups by month instead of by day.
 */
enum class GridZoom(val columns: Int, val byMonth: Boolean) {
    Large(2, false),
    Days(4, false),
    Months(7, true),
    ;

    /** One step closer (fewer, bigger photos), or this level if it's already the closest. */
    fun zoomIn(): GridZoom = entries.getOrElse(ordinal - 1) { this }

    /** One step further out (more, smaller photos), or this level if it's already the furthest. */
    fun zoomOut(): GridZoom = entries.getOrElse(ordinal + 1) { this }
}

/**
 * What a group header says, before it's turned into words. Kept apart from the wording so
 * the grouping can be tested without a phone and translated on one.
 */
sealed interface SectionLabel {
    data object Today : SectionLabel
    data object Yesterday : SectionLabel
    /** Within the last week: just the weekday ("Friday"). */
    data class Weekday(val day: DayOfWeek) : SectionLabel
    /** An older day; [withYear] when it isn't this year. */
    data class Day(val date: LocalDate, val withYear: Boolean) : SectionLabel
    /** A whole month, in the furthest zoom level; [withYear] when it isn't this year. */
    data class Month(val month: YearMonth, val withYear: Boolean) : SectionLabel
}

/** One group of the grid: a header and the photos under it, newest first. */
data class Section(val key: String, val label: SectionLabel, val items: List<Media>)

/** The day [media] was taken, in [zone]. */
fun Media.day(zone: ZoneId): LocalDate = Instant.ofEpochMilli(takenAt).atZone(zone).toLocalDate()

/** Which date the grid groups by: when each was taken (usually), or when it arrived on the phone. */
enum class GroupBy { Taken, Added }

/** The day [media] is grouped under by [by], in [zone]. */
fun Media.day(zone: ZoneId, by: GroupBy): LocalDate = when (by) {
    GroupBy.Taken -> day(zone)
    GroupBy.Added -> Instant.ofEpochSecond(addedAt).atZone(zone).toLocalDate()
}

/**
 * Splits [items] (newest first) into the grid's groups: one per day, or one per month when
 * [byMonth], by when each was taken or, with [by] set to [GroupBy.Added], when it arrived. [today] decides which days read "Today", "Yesterday" or a weekday.
 */
fun buildSections(
    items: List<Media>,
    byMonth: Boolean,
    today: LocalDate,
    zone: ZoneId,
    by: GroupBy = GroupBy.Taken,
): List<Section> {
    val sections = ArrayList<Section>()
    var currentKey: String? = null
    var current = ArrayList<Media>()
    var currentLabel: SectionLabel? = null
    for (item in items) {
        val day = item.day(zone, by)
        val key = if (byMonth) "m${YearMonth.from(day)}" else "d$day"
        if (key != currentKey) {
            if (currentKey != null) sections += Section(currentKey, currentLabel!!, current)
            currentKey = key
            current = ArrayList()
            currentLabel = if (byMonth) monthLabel(YearMonth.from(day), today) else dayLabel(day, today)
        }
        current += item
    }
    if (currentKey != null) sections += Section(currentKey, currentLabel!!, current)
    return sections
}

/** The header for a single [day], seen from [today]. Future dates (a wrong clock) read as dates. */
fun dayLabel(day: LocalDate, today: LocalDate): SectionLabel = when {
    day == today -> SectionLabel.Today
    day == today.minusDays(1) -> SectionLabel.Yesterday
    day.isBefore(today) && day.isAfter(today.minusDays(7)) -> SectionLabel.Weekday(day.dayOfWeek)
    else -> SectionLabel.Day(day, withYear = day.year != today.year)
}

/** The header for a whole [month], seen from [today]. */
fun monthLabel(month: YearMonth, today: LocalDate): SectionLabel =
    SectionLabel.Month(month, withYear = month.year != today.year)
