package app.roll.gallery.data

import java.time.LocalDateTime
import java.time.ZoneOffset

/** Tests run in UTC so dates don't depend on the computer's time zone. */
val UTC: ZoneOffset = ZoneOffset.UTC

/** Milliseconds for a local date and time in [UTC]. */
fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
    LocalDateTime.of(year, month, day, hour, 0).toInstant(UTC).toEpochMilli()

/** A photo with sensible defaults; tests set only what they look at. */
fun testMedia(
    id: Long,
    takenAt: Long = at(2026, 11, 16),
    albumId: Long = 1,
    albumName: String = "Camera",
    path: String = "DCIM/Camera/",
    isVideo: Boolean = false,
    isFavorite: Boolean = false,
) = Media(
    id = id, isVideo = isVideo, mimeType = if (isVideo) "video/mp4" else "image/jpeg",
    takenAt = takenAt, modifiedAt = takenAt / 1000, width = 4000, height = 3000,
    durationMs = if (isVideo) 5000 else 0, albumId = albumId, albumName = albumName, path = path,
    fileName = "IMG_$id.jpg", sizeBytes = 1_000_000, isFavorite = isFavorite, isTrashed = false,
    expiresAt = 0,
)
