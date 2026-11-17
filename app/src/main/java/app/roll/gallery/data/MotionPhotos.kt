package app.roll.gallery.data

import android.content.ContentResolver

/** Where the short video inside a motion photo sits in the file: [length] bytes from [offset]. */
data class MotionVideo(val offset: Long, val length: Long)

/**
 * How many bytes at the end of the file are the motion video, from the photo's XMP metadata, or
 * null if it isn't a motion photo. Two ways of writing it are understood:
 * - the older "MicroVideo" tags, where `MicroVideoOffset` is the video's length;
 * - the newer "MotionPhoto" container, where the directory lists the video item's `Length`
 *   (plus any `Padding` after it, which also sits at the end).
 * Both put the video at the very end of the file. Tags can be attributes or elements.
 */
fun motionVideoLength(xmp: String): Long? {
    tagValue(xmp, "GCamera:MicroVideoOffset")?.let { return it.takeIf { it > 0 } }
    if (!xmp.contains("Camera:MotionPhoto") && !xmp.contains("Container:Directory")) return null
    // Each item of the directory is one element; find the video's.
    val items = Regex("<Container:Item\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(xmp).map { it.value }
    for (item in items) {
        if (!Regex("Item:Mime\\s*=\\s*\"video/", RegexOption.IGNORE_CASE).containsMatchIn(item)) continue
        val length = tagValue(item, "Item:Length") ?: return null
        val padding = tagValue(item, "Item:Padding") ?: 0
        return (length + padding).takeIf { length > 0 }
    }
    return null
}

/** A whole-number tag value written as name="123" or <name>123</name>. */
private fun tagValue(text: String, name: String): Long? {
    val escaped = Regex.escape(name)
    val attribute = Regex("$escaped\\s*=\\s*\"(\\d+)\"").find(text)
    val element = Regex("<$escaped>\\s*(\\d+)\\s*</$escaped>").find(text)
    return (attribute ?: element)?.groupValues?.get(1)?.toLongOrNull()
}

/** How much of the start of a file to search for XMP: it's always near the top of a JPEG. */
private const val XMP_SEARCH_BYTES = 256 * 1024

/**
 * Finds the motion video inside [media], or null for an ordinary photo. Reads only the start of
 * the file (where the XMP is) as text; the video is played straight out of the photo file
 * later, without copying it out.
 */
fun readMotionVideo(resolver: ContentResolver, media: Media): MotionVideo? {
    if (media.isVideo) return null
    if (!media.mimeType.equals("image/jpeg", ignoreCase = true) && !media.mimeType.startsWith("image/heic") && !media.mimeType.startsWith("image/heif")) return null
    return runCatching {
        val head = resolver.openInputStream(media.uri)?.use { input ->
            val buffer = ByteArray(XMP_SEARCH_BYTES)
            var read = 0
            while (read < buffer.size) {
                val n = input.read(buffer, read, buffer.size - read)
                if (n < 0) break
                read += n
            }
            String(buffer, 0, read, Charsets.ISO_8859_1)
        } ?: return null
        val length = motionVideoLength(head) ?: return null
        val size = media.sizeBytes.takeIf { it > 0 }
            ?: resolver.openAssetFileDescriptor(media.uri, "r")?.use { it.length } ?: return null
        if (length >= size) return null
        MotionVideo(offset = size - length, length = length)
    }.getOrNull()
}
