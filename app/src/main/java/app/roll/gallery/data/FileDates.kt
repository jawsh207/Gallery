package app.roll.gallery.data

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore.MediaColumns
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

// A date, maybe followed by a time, as cameras and apps write them into file names:
// IMG_20240512_143000.jpg, PXL_20240512_143000123.jpg, Screenshot_2024-05-12-14-30-00.png,
// IMG-20240512-WA0001.jpg, signal-2024-05-12-143000.jpg. Not part of a longer number.
private val NAME_DATE = Regex(
    "(?<!\\d)((?:19|20)\\d{2})[-_.]?(0[1-9]|1[0-2])[-_.]?(0[1-9]|[12]\\d|3[01])" +
        "(?:[-_ T.]?([01]\\d|2[0-3])[-_.:]?([0-5]\\d)[-_.:]?([0-5]\\d))?",
)

// Milliseconds since 1970, as some apps name their files (1715524200000.jpg).
private val NAME_MILLIS = Regex("(?<!\\d)(1\\d{12})(?!\\d)")

/**
 * When a photo was taken, worked out from its file [name] in milliseconds since 1970, or null if
 * the name holds no believable date. Used when the photo itself carries no date (Android reads
 * the camera's date from the file when there is one). Dates and times in names are local, so
 * [zone] is the phone's time zone; a date with no time counts as the start of that day. Dates
 * before 1990 or after [now] (with a day's leeway) are ignored as unlikely.
 */
fun dateFromName(name: String, zone: ZoneId, now: Long = System.currentTimeMillis()): Long? {
    val earliest = LocalDate.of(1990, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
    val latest = now + 24 * 60 * 60 * 1000L
    fun believable(millis: Long) = millis in earliest..latest
    NAME_MILLIS.find(name)?.groupValues?.get(1)?.toLong()?.takeIf(::believable)?.let { return it }
    for (match in NAME_DATE.findAll(name)) {
        val g = match.groupValues
        val millis = runCatching {
            val date = LocalDate.of(g[1].toInt(), g[2].toInt(), g[3].toInt())
            val time = if (g[4].isNotEmpty()) {
                LocalDateTime.of(date.year, date.month, date.dayOfMonth, g[4].toInt(), g[5].toInt(), g[6].toInt())
            } else {
                date.atStartOfDay()
            }
            time.atZone(zone).toInstant().toEpochMilli()
        }.getOrNull() ?: continue
        if (believable(millis)) return millis
    }
    return null
}

/**
 * Makes a file Gallery just wrote ([target], inserted as pending) visible to other apps, then
 * sets when it was taken to [takenAt] again. Android looks the finished file over when it stops
 * being pending and, finding no date inside it, would otherwise date it to now, so a photo
 * moved out of the Locked folder or copied to an album would jump to Today.
 */
fun ContentResolver.publish(target: Uri, takenAt: Long) {
    update(target, ContentValues().apply { put(MediaColumns.IS_PENDING, 0) }, null, null)
    runCatching { update(target, ContentValues().apply { put(MediaColumns.DATE_TAKEN, takenAt) }, null, null) }
}
