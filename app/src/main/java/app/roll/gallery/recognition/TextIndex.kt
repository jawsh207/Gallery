package app.roll.gallery.recognition

import android.content.Context
import java.io.File

/** What was read in one photo: its id, the change time it was read at, and the text (empty if none). */
data class IndexEntry(val id: Long, val modifiedAt: Long, val text: String)

/** One entry as a line of the index file: id, change time and text, separated by tabs. */
fun formatIndexLine(entry: IndexEntry): String =
    "${entry.id}\t${entry.modifiedAt}\t${entry.text.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')}"

/** The entry a line of the index file holds, or null if the line is damaged. */
fun parseIndexLine(line: String): IndexEntry? {
    val parts = line.split('\t', limit = 3)
    if (parts.size < 2) return null
    val id = parts[0].toLongOrNull() ?: return null
    val modified = parts[1].toLongOrNull() ?: return null
    return IndexEntry(id, modified, parts.getOrElse(2) { "" })
}

/**
 * The text read in photos (Gallery Plus), kept in one file in Gallery's private storage so
 * searching doesn't read photos again. Photos with no text are listed too, with empty text,
 * so they aren't read twice. A photo edited since it was read is read again; entries for
 * deleted photos are dropped when the indexer next tidies up. Only text is kept, never images.
 */
class TextIndex(context: Context) {
    private val file = File(context.filesDir, "text_index.tsv")

    /** Everything read so far, by photo id. Later lines win, so re-reads simply append. */
    fun load(): Map<Long, IndexEntry> {
        if (!file.exists()) return emptyMap()
        val entries = HashMap<Long, IndexEntry>()
        file.forEachLine { line -> parseIndexLine(line)?.let { entries[it.id] = it } }
        return entries
    }

    /** Adds [entries] to the end of the file. */
    fun append(entries: List<IndexEntry>) {
        if (entries.isEmpty()) return
        file.appendText(entries.joinToString("") { formatIndexLine(it) + "\n" })
    }

    /** Replaces the whole file with [entries], dropping old and repeated lines. */
    fun rewrite(entries: Collection<IndexEntry>) {
        val temp = File(file.parentFile, file.name + ".new")
        temp.writeText(entries.joinToString("") { formatIndexLine(it) + "\n" })
        temp.renameTo(file)
    }

    /** Deletes everything read, for when reading text is turned off. */
    fun clear() {
        file.delete()
    }
}
