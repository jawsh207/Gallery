package app.roll.gallery.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineTest {
    private val today = LocalDate.of(2026, 11, 16) // a Monday

    @Test
    fun recentDaysReadAsWords() {
        assertEquals(SectionLabel.Today, dayLabel(today, today))
        assertEquals(SectionLabel.Yesterday, dayLabel(today.minusDays(1), today))
        assertEquals(SectionLabel.Weekday(DayOfWeek.WEDNESDAY), dayLabel(today.minusDays(5), today))
        assertEquals(SectionLabel.Weekday(DayOfWeek.TUESDAY), dayLabel(today.minusDays(6), today))
    }

    @Test
    fun olderDaysAreDatesWithYearOnlyWhenNeeded() {
        assertEquals(SectionLabel.Day(today.minusDays(7), withYear = false), dayLabel(today.minusDays(7), today))
        val lastYear = LocalDate.of(2025, 12, 31)
        assertEquals(SectionLabel.Day(lastYear, withYear = true), dayLabel(lastYear, today))
    }

    @Test
    fun futureDatesAreNotToday() {
        val tomorrow = today.plusDays(1)
        assertEquals(SectionLabel.Day(tomorrow, withYear = false), dayLabel(tomorrow, today))
    }

    @Test
    fun groupsByDayKeepingOrder() {
        val items = listOf(
            testMedia(1, at(2026, 11, 16, 18)),
            testMedia(2, at(2026, 11, 16, 9)),
            testMedia(3, at(2026, 11, 15)),
            testMedia(4, at(2026, 10, 2)),
        )
        val sections = buildSections(items, byMonth = false, today = today, zone = UTC)
        assertEquals(listOf(listOf(1L, 2L), listOf(3L), listOf(4L)), sections.map { s -> s.items.map { it.id } })
        assertEquals(SectionLabel.Today, sections[0].label)
        assertEquals(SectionLabel.Yesterday, sections[1].label)
    }

    @Test
    fun groupsByMonth() {
        val items = listOf(
            testMedia(1, at(2026, 11, 16)),
            testMedia(2, at(2026, 11, 1)),
            testMedia(3, at(2025, 11, 20)),
        )
        val sections = buildSections(items, byMonth = true, today = today, zone = UTC)
        assertEquals(2, sections.size)
        assertEquals(SectionLabel.Month(YearMonth.of(2026, 11), withYear = false), sections[0].label)
        assertEquals(SectionLabel.Month(YearMonth.of(2025, 11), withYear = true), sections[1].label)
    }

    @Test
    fun emptyLibraryHasNoSections() {
        assertEquals(emptyList<Section>(), buildSections(emptyList(), byMonth = false, today = today, zone = UTC))
    }

    @Test
    fun zoomStopsAtTheEnds() {
        assertEquals(GridZoom.Large, GridZoom.Large.zoomIn())
        assertEquals(GridZoom.Months, GridZoom.Months.zoomOut())
        assertEquals(GridZoom.Large, GridZoom.Days.zoomIn())
    }
}
