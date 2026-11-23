package app.roll.gallery.intents

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import app.roll.gallery.data.Media
import app.roll.gallery.data.PickKinds
import app.roll.gallery.data.pickKinds

/** Something another app asked Gallery to do when it opened it. */
sealed interface ExternalRequest {
    /**
     * Show one photo or video: "Open with Gallery" from a file manager, or the camera's
     * thumbnail (Android's "review" action).
     */
    data class View(val uri: Uri, val mimeType: String?) : ExternalRequest

    /**
     * Edit a photo another app handed over: "Edit" on a new screenshot, or Gallery chosen from
     * a share sheet. [canWrite] when that app allowed changing the original (Edit usually
     * does); otherwise the edit can only be saved as a copy.
     */
    data class Edit(val uri: Uri, val mimeType: String?, val canWrite: Boolean) : ExternalRequest

    /** Choose photos or videos and hand them back; [multiple] when the app accepts several. */
    data class Pick(val kinds: PickKinds, val multiple: Boolean) : ExternalRequest
}

/** What [intent] asks for, or null when Gallery was simply opened. */
fun parseRequest(context: Context, intent: Intent?): ExternalRequest? {
    intent ?: return null
    return when (intent.action) {
        Intent.ACTION_VIEW, MediaStore.ACTION_REVIEW, MediaStore.ACTION_REVIEW_SECURE ->
            intent.data?.let { ExternalRequest.View(it, intent.resolveType(context)) }
        Intent.ACTION_EDIT -> intent.data?.let { ExternalRequest.Edit(it, intent.resolveType(context), canWrite(context, it)) }
        Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)?.let {
            ExternalRequest.Edit(it, intent.type, canWrite(context, it))
        }
        Intent.ACTION_GET_CONTENT, Intent.ACTION_PICK -> {
            val types = listOfNotNull(intent.resolveType(context)) +
                intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES).orEmpty()
            ExternalRequest.Pick(pickKinds(types), intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
        }
        else -> null
    }
}

/** Whether Gallery was allowed to change the file at [uri] (the app that handed it over said so). */
private fun canWrite(context: Context, uri: Uri): Boolean =
    context.checkUriPermission(uri, android.os.Process.myPid(), android.os.Process.myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

/**
 * Describes a file another app handed over, from what its provider will say: the name and size
 * every provider gives, and when it was taken if it's in the media store. Anything unknown is
 * left empty; the viewer only needs the address and whether it's a video.
 */
fun externalMedia(context: Context, uri: Uri, mimeType: String?): Media {
    val type = mimeType ?: context.contentResolver.getType(uri) ?: "image/*"
    var name = uri.lastPathSegment.orEmpty()
    var size = 0L
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
    }
    var takenAt = 0L
    if (uri.authority == MediaStore.AUTHORITY) {
        runCatching {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATE_TAKEN), null, null, null)?.use { c ->
                if (c.moveToFirst()) takenAt = c.getLong(0)
            }
        }
    }
    return Media(
        id = uri.hashCode().toLong(), isVideo = type.startsWith("video/"), mimeType = type,
        takenAt = takenAt.takeIf { it > 0 } ?: System.currentTimeMillis(), modifiedAt = 0,
        width = 0, height = 0, durationMs = 0, albumId = 0, albumName = "", path = "", fileName = name,
        sizeBytes = size, isFavorite = false, isTrashed = false, expiresAt = 0, externalUri = uri,
    )
}
