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
 * The items in [items] matching every word of [query], plus any in [alsoMatching] (the best
 * matches from searching by description), in their original order. A word matches
 * when it is:
 * - a kind word from [words] ("videos", "favorites", "screenshots"),
 * - a year ("2024") or a month name in [locale] ("June", "jun"), matched against when the item
 *   was taken,
 * - or part of the file name, album name, folder path or file type ("png", "gif"), or of the
 *   text read in the photo ([photoText], lower-cased, by id; Gallery Plus only).
 *
 * Matching ignores case but not accents: "cafe" won't find "Café".
 */
fun searchMedia(
    items: List<Media>,
    query: String,
    words: SearchWords,
    locale: Locale,
    zone: ZoneId,
    alsoMatching: Set<Long> = emptySet(),
    photoText: (Long) -> String? = { null },
): List<Media> {
    val terms = query.trim().lowercase(locale).split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (terms.isEmpty()) return emptyList()
    val months = monthNames(locale)
    val matchers = terms.map { term -> termMatcher(term, words, months, zone, locale, photoText) }
    return items.filter { item -> item.id in alsoMatching || matchers.all { it(item) } }
}

/**
 * Whether [query] describes what's in a photo ("a black dog"), so it's worth searching by
 * description, rather than being only kind words, years and month names ("videos 2024"),
 * which description search would only muddle.
 */
fun isDescriptionQuery(query: String, words: SearchWords, locale: Locale): Boolean {
    val months = monthNames(locale)
    val kinds = words.videos + words.photos + words.favorites + words.screenshots
    return query.trim().lowercase(locale).split(Regex("\\s+")).filter { it.isNotEmpty() }.any { term ->
        term !in kinds && term !in months && !(term.length == 4 && term.toIntOrNull() in 1900..2999)
    }
}

/** How one search word is tested against an item. */
private fun termMatcher(
    term: String,
    words: SearchWords,
    months: Map<String, Month>,
    zone: ZoneId,
    locale: Locale,
    photoText: (Long) -> String?,
): (Media) -> Boolean {
    val text: (Media) -> Boolean = { item ->
        photoText(item.id)?.contains(term) == true ||
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
