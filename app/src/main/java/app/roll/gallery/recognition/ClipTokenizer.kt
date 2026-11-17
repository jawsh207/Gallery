package app.roll.gallery.recognition

import java.text.Normalizer

/**
 * Turns a search like "a black dog" into the numbers CLIP's text half reads: CLIP's own
 * byte-pair tokenizer, rewritten here so the app needs no other library. The text is tidied
 * (accents composed, spaces squeezed, lower-cased), split into words, numbers and symbols,
 * each written as bytes in CLIP's printable byte alphabet, then joined piece by piece following
 * the merge rules ([merges], best first) until no rule applies. Each word ends with "</w>".
 * The result starts with the start-of-text piece and ends with the end-of-text piece.
 *
 * [vocab] is every piece in id order; [merges] holds lines "a b" in rank order.
 */
class ClipTokenizer(vocab: List<String>, merges: List<String>) {
    private val ids: Map<String, Int> = vocab.withIndex().associate { (i, piece) -> piece to i }
    private val ranks: Map<Pair<String, String>, Int> = merges
        .mapNotNull { line -> line.split(' ').takeIf { it.size == 2 }?.let { it[0] to it[1] } }
        .withIndex().associate { (i, pair) -> pair to i }
    private val byteChars: CharArray = clipByteAlphabet()
    private val cache = HashMap<String, List<String>>()

    /** The id of the start-of-text piece. */
    val start: Int = ids.getValue("<|startoftext|>")

    /** The id of the end-of-text piece. */
    val end: Int = ids.getValue("<|endoftext|>")

    /** [text] as piece ids, between the start and end pieces, at most [maxLength] ids in all. */
    fun encode(text: String, maxLength: Int = 77): IntArray {
        val out = ArrayList<Int>()
        out += start
        for (word in clipWords(clean(text))) {
            val encoded = String(word.toByteArray(Charsets.UTF_8).map { byteChars[it.toInt() and 0xFF] }.toCharArray())
            for (piece in bpe(encoded)) ids[piece]?.let { out += it }
        }
        if (out.size > maxLength - 1) out.subList(maxLength - 1, out.size).clear()
        out += end
        return out.toIntArray()
    }

    /** The pieces one (byte-alphabet) word joins into. */
    private fun bpe(word: String): List<String> = cache.getOrPut(word) {
        if (word.isEmpty()) return@getOrPut emptyList()
        var parts = word.map { it.toString() }.toMutableList()
        parts[parts.lastIndex] = parts.last() + "</w>"
        while (parts.size > 1) {
            var best: Pair<String, String>? = null
            var bestRank = Int.MAX_VALUE
            for (i in 0 until parts.size - 1) {
                val rank = ranks[parts[i] to parts[i + 1]] ?: continue
                if (rank < bestRank) {
                    bestRank = rank
                    best = parts[i] to parts[i + 1]
                }
            }
            val (first, second) = best ?: break
            val merged = ArrayList<String>(parts.size)
            var i = 0
            while (i < parts.size) {
                if (i < parts.size - 1 && parts[i] == first && parts[i + 1] == second) {
                    merged += first + second
                    i += 2
                } else {
                    merged += parts[i]
                    i++
                }
            }
            parts = merged
        }
        parts
    }
}

/** [text] tidied as CLIP expects: accents composed, runs of spaces squeezed, trimmed, lower-cased. */
fun clean(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFC).replace(Regex("\\s+"), " ").trim().lowercase()

/** CLIP's split of tidied text: common endings like "'s", runs of letters, single digits, and runs of other symbols. */
fun clipWords(text: String): List<String> =
    CLIP_PATTERN.findAll(text).map { it.value }.toList()

private val CLIP_PATTERN = Regex(
    "<\\|startoftext\\|>|<\\|endoftext\\|>|'s|'t|'re|'ve|'m|'ll|'d|[\\p{L}]+|[\\p{N}]|[^\\s\\p{L}\\p{N}]+",
)

/**
 * CLIP's (and GPT-2's) printable stand-in for each of the 256 byte values: printable bytes stand
 * for themselves, the rest are moved up past 255, so every byte has a visible character.
 */
fun clipByteAlphabet(): CharArray {
    val printable = (('!'.code..'~'.code) + ('¡'.code..'¬'.code) + ('®'.code..'ÿ'.code)).toSet()
    val chars = CharArray(256)
    var extra = 0
    for (b in 0 until 256) {
        chars[b] = if (b in printable) b.toChar() else (256 + extra++).toChar()
    }
    return chars
}
