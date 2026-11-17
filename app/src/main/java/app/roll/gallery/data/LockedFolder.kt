package app.roll.gallery.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns
import java.io.File
import kotlin.random.Random
import org.json.JSONObject

/**
 * The Locked folder: photos and videos kept in Gallery's own private storage instead of the
 * shared media store, so other apps (and Gallery's other screens) can't see them. The one place
 * Gallery keeps files of its own. Each item is its file plus a small ".json" note of what the
 * media store knew about it (name, type, dates, size, folder), so it can be put back as it was.
 *
 * The folder is left out of Android's backups and moves to a new phone with nothing: removing
 * Gallery deletes it. Screens check the screen lock before showing it.
 */
class LockedFolder(context: Context) {
    private val resolver: ContentResolver = context.contentResolver
    private val dir = File(context.filesDir, LOCKED_DIR)

    /** Everything in the folder, newest first. */
    fun list(): List<Media> {
        val notes = dir.listFiles { file -> file.name.endsWith(".json") }.orEmpty()
        return notes.mapNotNull { note -> runCatching { read(note) }.getOrNull() }
            .sortedWith(compareByDescending<Media> { it.takenAt }.thenByDescending { it.id })
    }

    /**
     * Copies [media] into the folder and returns it as it now is there, or null if the copy
     * failed. The original isn't touched; the caller deletes it once the copy is safe.
     */
    fun add(media: Media): Media? {
        dir.mkdirs()
        val id = Random.nextLong() and Long.MAX_VALUE
        val extension = media.fileName.substringAfterLast('.', "").takeIf { it.isNotBlank() && it.length <= 5 } ?: "bin"
        val file = File(dir, "$id.$extension")
        val note = File(dir, "$id.json")
        return runCatching {
            resolver.openInputStream(media.uri).use { input ->
                file.outputStream().use { output -> requireNotNull(input).copyTo(output) }
            }
            val json = JSONObject().apply {
                put("file", file.name)
                put("name", media.fileName)
                put("mime", media.mimeType)
                put("video", media.isVideo)
                put("taken", media.takenAt)
                put("width", media.width)
                put("height", media.height)
                put("duration", media.durationMs)
                put("path", media.path)
                put("album", media.albumName)
            }
            note.writeText(json.toString())
            read(note)
        }.onFailure {
            // Leave nothing half-copied behind.
            file.delete()
            note.delete()
        }.getOrNull()
    }

    /** Deletes [media] from the folder for good. */
    fun delete(media: Media) {
        val file = media.externalUri?.path?.let(::File) ?: return
        file.delete()
        File(dir, "${media.id}.json").delete()
    }

    /**
     * Puts [media] back among the phone's photos, in the folder it came from when Android
     * allows (otherwise in Pictures), then removes it from the Locked folder. Returns whether
     * it worked; on failure it stays locked.
     */
    fun restore(media: Media): Boolean = runCatching {
        val file = requireNotNull(media.externalUri?.path?.let(::File))
        val collection = if (media.isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaColumns.DISPLAY_NAME, media.fileName)
            put(MediaColumns.MIME_TYPE, media.mimeType)
            put(MediaColumns.RELATIVE_PATH, restorePath(media.path, media.isVideo))
            put(MediaColumns.DATE_TAKEN, media.takenAt)
            put(MediaColumns.IS_PENDING, 1)
        }
        val target: Uri = requireNotNull(resolver.insert(collection, values))
        try {
            file.inputStream().use { input ->
                resolver.openOutputStream(target).use { output -> input.copyTo(requireNotNull(output)) }
            }
            resolver.update(target, ContentValues().apply { put(MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(target, null, null)
            throw e
        }
        delete(media)
    }.isSuccess

    /** One item from its note; its id is the note's name, so it stays the same between visits. */
    private fun read(note: File): Media {
        val json = JSONObject(note.readText())
        val file = File(dir, json.getString("file"))
        require(file.exists())
        return Media(
            id = note.nameWithoutExtension.toLong(),
            isVideo = json.optBoolean("video"),
            mimeType = json.optString("mime", "image/jpeg"),
            takenAt = json.optLong("taken", file.lastModified()),
            modifiedAt = file.lastModified() / 1000,
            width = json.optInt("width"),
            height = json.optInt("height"),
            durationMs = json.optLong("duration"),
            albumId = 0,
            albumName = json.optString("album"),
            path = json.optString("path"),
            fileName = json.optString("name", file.name),
            sizeBytes = file.length(),
            isFavorite = false,
            isTrashed = false,
            expiresAt = 0,
            externalUri = Uri.fromFile(file),
        )
    }

    private companion object {
        /** The folder inside Gallery's private files; also named in the backup rules. */
        const val LOCKED_DIR = "locked"
    }
}

/**
 * Where a locked item goes back to: the folder it came from if Android lets that kind of file
 * live there, otherwise Pictures.
 */
fun restorePath(originalPath: String, isVideo: Boolean): String =
    if (originalPath.isNotBlank() && canHold(originalPath, isVideo)) originalPath else "Pictures/"
