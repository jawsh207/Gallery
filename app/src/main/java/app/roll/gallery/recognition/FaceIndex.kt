package app.roll.gallery.recognition

import android.content.Context
import java.io.File
import java.util.Locale

/**
 * One face found in a photo, as kept in the index: its box as fractions of the photo's width and
 * height (so it fits any size of the photo), and its fingerprint.
 */
data class StoredFace(val left: Float, val top: Float, val width: Float, val height: Float, val embedding: FloatArray) {
    override fun equals(other: Any?): Boolean =
        other is StoredFace && left == other.left && top == other.top && width == other.width &&
            height == other.height && embedding.contentEquals(other.embedding)

    override fun hashCode(): Int = listOf(left, top, width, height).hashCode() * 31 + embedding.contentHashCode()
}

/** The faces found in one photo (none is fine), and the change time it was looked at. */
data class FaceEntry(val id: Long, val modifiedAt: Long, val faces: List<StoredFace>)

/** One entry as a line of the faces file: id, change time, then each face as "left,top,width,height:fingerprint". */
fun formatFaceLine(entry: FaceEntry): String = buildString {
    append(entry.id).append('\t').append(entry.modifiedAt).append('\t')
    entry.faces.joinTo(this, " ") { f ->
        String.format(Locale.ROOT, "%.4f,%.4f,%.4f,%.4f:%s", f.left, f.top, f.width, f.height, encodeEmbedding(f.embedding))
    }
}

/** The entry a line of the faces file holds, or null if the line is damaged. Damaged faces are skipped. */
fun parseFaceLine(line: String): FaceEntry? {
    val parts = line.split('\t', limit = 3)
    if (parts.size < 2) return null
    val id = parts[0].toLongOrNull() ?: return null
    val modified = parts[1].toLongOrNull() ?: return null
    val faces = parts.getOrElse(2) { "" }.split(' ').filter { it.isNotBlank() }.mapNotNull { text ->
        val (box, fingerprint) = text.split(':', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
        val numbers = box.split(',').mapNotNull { it.toFloatOrNull() }
        val embedding = decodeEmbedding(fingerprint) ?: return@mapNotNull null
        if (numbers.size != 4) null else StoredFace(numbers[0], numbers[1], numbers[2], numbers[3], embedding)
    }
    return FaceEntry(id, modified, faces)
}

/**
 * The faces found in photos (Gallery Plus), in one file in Gallery's private storage, so people
 * can be grouped without looking at photos again. Works like [TextIndex]: photos with no faces
 * are listed too, edited photos are looked at again, later lines win. Only boxes and
 * fingerprints are kept, never pictures of faces.
 */
class FaceIndex(context: Context) {
    private val file = File(context.filesDir, "faces_index.tsv")

    /** Everything found so far, by photo id. */
    fun load(): Map<Long, FaceEntry> {
        if (!file.exists()) return emptyMap()
        val entries = HashMap<Long, FaceEntry>()
        file.forEachLine { line -> parseFaceLine(line)?.let { entries[it.id] = it } }
        return entries
    }

    /** Adds [entries] to the end of the file. */
    fun append(entries: List<FaceEntry>) {
        if (entries.isEmpty()) return
        file.appendText(entries.joinToString("") { formatFaceLine(it) + "\n" })
    }

    /** Replaces the whole file with [entries]. */
    fun rewrite(entries: Collection<FaceEntry>) {
        val temp = File(file.parentFile, file.name + ".new")
        temp.writeText(entries.joinToString("") { formatFaceLine(it) + "\n" })
        temp.renameTo(file)
    }

    /** Deletes everything found, for when finding people is turned off. */
    fun clear() {
        file.delete()
    }
}

/**
 * The names given to people, kept by one of their faces' keys ("photo#index"), in Gallery's
 * private storage. People are worked out afresh from the faces each time, so a name sticks to
 * whichever group contains that face.
 */
class PeopleNames(context: Context) {
    private val file = File(context.filesDir, "people_names.tsv")

    /** Every name, by face key. */
    fun load(): Map<String, String> {
        if (!file.exists()) return emptyMap()
        return file.readLines().mapNotNull { line ->
            val parts = line.split('\t', limit = 2)
            if (parts.size == 2 && parts[1].isNotBlank()) parts[0] to parts[1] else null
        }.toMap()
    }

    /** Saves [names] (face key to name), replacing what was there. */
    fun save(names: Map<String, String>) {
        file.writeText(names.entries.joinToString("") { (key, name) -> "$key\t${name.replace('\t', ' ').replace('\n', ' ')}\n" })
    }
}
