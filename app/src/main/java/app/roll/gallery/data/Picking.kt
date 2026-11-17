package app.roll.gallery.data

/** Which kinds of item another app asked to pick. */
data class PickKinds(val images: Boolean, val videos: Boolean) {
    /** True when [media] is a kind that was asked for. */
    fun accepts(media: Media): Boolean = if (media.isVideo) videos else images
}

/**
 * Works out what to offer from a pick request's MIME types: "image/\*" or "image/png" offers
 * photos, "video/\*" videos, the media store's own folder types ("vnd.android.cursor.dir/image")
 * the same, and anything else ("\*\/\*", none at all) both. Several types add up.
 */
fun pickKinds(types: List<String>): PickKinds {
    var images = false
    var videos = false
    for (type in types.map { it.lowercase() }) {
        when {
            type.startsWith("image/") || type.endsWith("/image") -> images = true
            type.startsWith("video/") || type.endsWith("/video") -> videos = true
            else -> {
                images = true
                videos = true
            }
        }
    }
    return if (!images && !videos) PickKinds(images = true, videos = true) else PickKinds(images, videos)
}
