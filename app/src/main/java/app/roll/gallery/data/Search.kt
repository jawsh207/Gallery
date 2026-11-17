package app.roll.gallery.data

import java.time.Month
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * Words that search for a kind of item rather than text, in the phone's language. Kept apart
 * from the matching so it can be tested; the app fills it from its string resources.
 */
data class SearchWords(
    val videos: Set<String>,
    val photos: Set<String>,
    val favorites: Set<String>,
    val screenshots: Set<String>,
) {
    /** The English words, used by tests and as a fallback. */
    companion object {
        val ENGLISH = SearchWords(
            videos = setOf("video", "videos"),
            photos = setOf("photo", "photos", "picture", "pictures", "image", "images"),
            favorites = setOf("favorite", "favorites", "favourite", "favourites"),
            screenshots = setOf("screenshot", "screenshots"),
        )
    }
}

/**
 * The items in [items] matching every word of [query], in their original order. A word matches
 * when it is:
 * - a kind word from [words] ("videos", "favorites", "screenshots"),
 * - a year ("2024") or a month name in [locale] ("June", "jun"), matched against when the item
 *   was taken,
 * - or part of the file name, album name, folder path or file type ("png", "gif").
 *
 * Matching ignores case but not accents: "cafe" won't find "Café".
 */
fun searchMedia(items: List<Media>, query: String, words: SearchWords, locale: Locale, zone: ZoneId): List<Media> {
    val terms = query.trim().lowercase(locale).split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (terms.isEmpty()) return emptyList()
    val months = monthNames(locale)
    val matchers = terms.map { term -> termMatcher(term, words, months, zone, locale) }
    return items.filter { item -> matchers.all { it(item) } }
}

/** How one search word is tested against an item. */
private fun termMatcher(
    term: String,
    words: SearchWords,
    months: Map<String, Month>,
    zone: ZoneId,
    locale: Locale,
): (Media) -> Boolean {
    val text: (Media) -> Boolean = { item ->
        item.fileName.lowercase(locale).contains(term) ||
            item.albumName.lowercase(locale).contains(term) ||
            item.path.lowercase(locale).contains(term) ||
            item.mimeType.substringAfter('/').lowercase(locale) == term
    }
    val year = term.toIntOrNull()?.takeIf { term.length == 4 && it in 1900..2999 }
    val month = months[term]
    return when {
        term in words.videos -> { item -> item.isVideo }
        term in words.photos -> { item -> !item.isVideo }
        term in words.favorites -> { item -> item.isFavorite }
        term in words.screenshots -> { item -> isScreenshot(item) || text(item) }
        year != null -> { item -> item.day(zone).year == year || text(item) }
        month != null -> { item -> item.day(zone).month == month || text(item) }
        else -> text
    }
}

/** True for items saved in a screenshots folder. */
fun isScreenshot(media: Media): Boolean = media.path.lowercase().trimEnd('/').endsWith("screenshots")

/** Every month's full and short name in [locale], lower-cased, to the month. */
fun monthNames(locale: Locale): Map<String, Month> = buildMap {
    for (month in Month.entries) {
        for (style in listOf(TextStyle.FULL, TextStyle.SHORT, TextStyle.FULL_STANDALONE, TextStyle.SHORT_STANDALONE)) {
            put(month.getDisplayName(style, locale).lowercase(locale).trimEnd('.'), month)
        }
    }
}
