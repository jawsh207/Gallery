package app.roll.gallery.ui.components

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.roll.gallery.R
import app.roll.gallery.data.SearchWords
import app.roll.gallery.data.SectionLabel
import androidx.compose.runtime.remember
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAccessor
import java.util.Locale

/** The phone's current language, for dates. */
@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/**
 * [value] formatted in the local style for a set of date parts ([skeleton], e.g. "MMMd" or
 * "jmm"); Android picks the order and punctuation each language expects.
 */
fun formatSkeleton(value: TemporalAccessor, skeleton: String, locale: Locale): String =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(value)

/** A group header in words: "Today", "Friday", "Mon, Nov 3" or "November 2024". */
@Composable
fun SectionLabel.text(): String {
    val locale = currentLocale()
    return when (this) {
        SectionLabel.Today -> stringResource(R.string.today)
        SectionLabel.Yesterday -> stringResource(R.string.yesterday)
        is SectionLabel.Weekday -> day.getDisplayName(TextStyle.FULL, locale)
        is SectionLabel.Day -> formatSkeleton(date, if (withYear) "EEEMMMdyyyy" else "EEEMMMd", locale)
        is SectionLabel.Month -> formatSkeleton(month, if (withYear) "MMMMyyyy" else "MMMM", locale)
    }
}

/** When something was taken, as a date ("November 16, 2026") and a time ("6:42 PM"). */
fun takenAtText(millis: Long, locale: Locale): Pair<String, String> {
    val time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return formatSkeleton(time, "MMMMdyyyy", locale) to formatSkeleton(time, "jmm", locale)
}

/**
 * The kind words search understands ("videos", "favorites"…) in the phone's language, plus
 * the English ones, from comma-separated string resources.
 */
@Composable
fun rememberSearchWords(): SearchWords {
    val videos = stringResource(R.string.search_words_videos)
    val photos = stringResource(R.string.search_words_photos)
    val favorites = stringResource(R.string.search_words_favorites)
    val screenshots = stringResource(R.string.search_words_screenshots)
    val locale = currentLocale()
    return remember(videos, photos, favorites, screenshots, locale) {
        fun words(list: String) = list.split(',').map { it.trim().lowercase(locale) }.filter { it.isNotEmpty() }.toSet()
        val english = SearchWords.ENGLISH
        SearchWords(
            videos = english.videos + words(videos),
            photos = english.photos + words(photos),
            favorites = english.favorites + words(favorites),
            screenshots = english.screenshots + words(screenshots),
        )
    }
}
