package app.roll.gallery.data

import android.content.ContentResolver
import androidx.exifinterface.media.ExifInterface

/** Camera details stored inside a photo, for its info sheet. Any part can be missing. */
data class CameraDetails(
    /** "Canon EOS R6" or "samsung SM-S928B": maker and model, without the maker twice. */
    val camera: String?,
    val aperture: String?,
    val exposure: String?,
    val iso: String?,
    val focalLength: String?,
) {
    /** True when there's nothing to show. */
    val isEmpty: Boolean get() = listOf(camera, aperture, exposure, iso, focalLength).all { it == null }
}

/**
 * Reads [media]'s camera details from its EXIF tags. Videos and files without EXIF give null.
 * Location tags are not read: the app doesn't ask for location access.
 */
fun readCameraDetails(resolver: ContentResolver, media: Media): CameraDetails? {
    if (media.isVideo) return null
    val exif = runCatching {
        resolver.openInputStream(media.uri)?.use { ExifInterface(it) }
    }.getOrNull() ?: return null
    val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim()?.takeIf { it.isNotEmpty() }
    val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim()?.takeIf { it.isNotEmpty() }
    val details = CameraDetails(
        camera = cameraName(make, model),
        aperture = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).takeIf { it > 0 }?.let(::formatAperture),
        exposure = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0).takeIf { it > 0 }?.let(::formatExposure),
        iso = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0).takeIf { it > 0 }?.let { "ISO $it" },
        focalLength = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0).takeIf { it > 0 }?.let(::formatFocalLength),
    )
    return details.takeUnless { it.isEmpty }
}

/** The camera's name: [model] alone if it already starts with [make] (Canon writes "Canon EOS R6"), else both. */
fun cameraName(make: String?, model: String?): String? = when {
    model == null -> make
    make == null -> model
    model.startsWith(make, ignoreCase = true) -> model
    else -> "$make $model"
}
