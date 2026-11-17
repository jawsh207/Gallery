package app.roll.gallery.recognition

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class ClipTokenizerTest {
    @Test
    fun byteAlphabetIsPrintableAndUnique() {
        val chars = clipByteAlphabet()
        assertEquals(256, chars.toSet().size)
        assertEquals('a', chars['a'.code])
        assertEquals((256).toChar(), chars[0])
    }

    @Test
    fun cleansAndSplits() {
        assertEquals("old log-cabin, 1987!", clean("  Old   log-cabin, 1987!  "))
        assertEquals(listOf("it", "'s", "a", "dog", "'s", "life"), clipWords("it's a dog's life"))
        assertEquals(listOf("1", "9", "8", "7", "!"), clipWords("1987!"))
    }

    @Test
    fun mergesByRank() {
        val vocab = listOf("<|startoftext|>", "<|endoftext|>", "d", "o", "g</w>", "do", "dog</w>", "o</w>", "g")
        // "d o" joins first, then "do g</w>".
        val tokenizer = ClipTokenizer(vocab, listOf("d o", "do g</w>"))
        assertArrayEquals(intArrayOf(0, 6, 1), tokenizer.encode("Dog"))
        // Unknown pieces are left out rather than guessed.
        assertArrayEquals(intArrayOf(0, 1), tokenizer.encode("zz"))
    }

    @Test
    fun longTextIsCut() {
        val vocab = listOf("<|startoftext|>", "<|endoftext|>", "a</w>")
        val ids = ClipTokenizer(vocab, emptyList()).encode("a ".repeat(100), maxLength = 10)
        assertEquals(10, ids.size)
        assertEquals(1, ids.last())
    }

    /**
     * Checks the tokenizer against the reference one on the real vocabulary, using the files the
     * Plus build writes (skipped when they haven't been built, as in a plain local checkout).
     */
    @Test
    fun matchesTheReferenceTokenizer() {
        val plus = File("../build/plus")
        val vocab = File(plus, "assets/clip/clip_vocab.txt")
        val merges = File(plus, "assets/clip/clip_merges.txt")
        val expected = File(plus, "test/clip_tokens_expected.txt")
        assumeTrue(vocab.exists() && merges.exists() && expected.exists())
        val tokenizer = ClipTokenizer(vocab.readLines(), merges.readLines().drop(0).filter { !it.startsWith("#version") })
        for (line in expected.readLines().filter { it.isNotBlank() }) {
            val (text, ids) = line.split('\t')
            assertEquals(text, ids, tokenizer.encode(text).joinToString(" "))
        }
    }
}
