package app.roll.gallery.recognition

import org.junit.Assert.assertEquals
import org.junit.Test

class PeopleTest {
    private val alice = normalized(floatArrayOf(1f, 0f, 0f))
    private val bob = normalized(floatArrayOf(0f, 1f, 0f))

    private fun face(embedding: FloatArray, size: Float = 0.1f) = StoredFace(0f, 0f, size, size, embedding)

    private val faces = mapOf(
        1L to FaceEntry(1, 0, listOf(face(alice), face(bob))),
        2L to FaceEntry(2, 0, listOf(face(alice, 0.3f))),
        3L to FaceEntry(3, 0, listOf(face(alice))),
        4L to FaceEntry(4, 0, emptyList()),
    )

    @Test
    fun peopleNeedEnoughPhotosOrAName() {
        val people = buildPeople(faces, emptyMap())
        assertEquals(1, people.size)
        assertEquals(setOf(1L, 2L, 3L), people[0].photoIds)
        assertEquals(2L, people[0].coverPhoto) // the biggest face
        val named = buildPeople(faces, mapOf("1#1" to "Bob"))
        assertEquals(listOf("Bob", null), named.map { it.name })
        assertEquals("1#1", named[0].key)
    }

    @Test
    fun renamingMovesTheName() {
        val person = buildPeople(faces, mapOf("2#0" to "Al")).single { it.name != null }
        val names = renamePerson(mapOf("2#0" to "Al", "9#9" to "Other"), person, " Alice ")
        assertEquals(mapOf("9#9" to "Other", "1#0" to "Alice"), names)
        assertEquals(mapOf("9#9" to "Other"), renamePerson(names, person, ""))
    }

    @Test
    fun namesBecomeSearchable() {
        val people = buildPeople(faces, mapOf("1#0" to "Alice"))
        val text = searchableText(mapOf(1L to "beach"), people)
        assertEquals("beach\nalice", text[1L])
        assertEquals("alice", text[3L])
    }
}
