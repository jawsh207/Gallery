package app.roll.gallery.recognition

/**
 * A person: a group of faces that look like the same someone. [key] identifies them while the
 * app is open (a face key, "photo#index"; the named face if they have a name). [cover] is their
 * clearest face, used for the round picture, in photo [coverPhoto].
 */
data class Person(
    val key: String,
    val name: String?,
    val faceKeys: List<String>,
    val photoIds: Set<Long>,
    val coverPhoto: Long,
    val cover: StoredFace,
)

/**
 * Everyone in [faces] (by photo id): faces are grouped by [groupFaces], and a group becomes a
 * person if it appears in at least [minPhotos] photos or has been given a name. A group takes the
 * name ([names], by face key) of the first of its faces that has one. Named people come first,
 * then the rest by how many photos they're in.
 */
fun buildPeople(faces: Map<Long, FaceEntry>, names: Map<String, String>, minPhotos: Int = 3): List<Person> {
    val refs = faces.values.flatMap { entry -> entry.faces.mapIndexed { i, f -> FaceRef(entry.id, i, f.embedding) } }
    val people = groupFaces(refs).mapNotNull { group ->
        val keys = group.map { it.key }
        val named = keys.firstOrNull { it in names }
        val photos = group.mapTo(LinkedHashSet()) { it.photoId }
        if (named == null && photos.size < minPhotos) return@mapNotNull null
        // The biggest face is usually the clearest.
        val best = group.maxBy { ref -> faces.getValue(ref.photoId).faces[ref.index].let { it.width * it.height } }
        Person(
            key = named ?: keys.first(),
            name = named?.let { names.getValue(it) },
            faceKeys = keys,
            photoIds = photos,
            coverPhoto = best.photoId,
            cover = faces.getValue(best.photoId).faces[best.index],
        )
    }
    return people.sortedWith(compareBy<Person> { it.name == null }.thenByDescending { it.photoIds.size })
}

/**
 * The names to keep after naming [person] [name] (blank removes it): every name already on any of
 * their faces is dropped, and the new one is kept on their first face.
 */
fun renamePerson(names: Map<String, String>, person: Person, name: String): Map<String, String> {
    val kept = names - person.faceKeys.toSet()
    return if (name.isBlank()) kept else kept + (person.faceKeys.first() to name.trim())
}

/**
 * Each photo's searchable words: the text read in it ([text], already lower-case) plus the
 * names of the people in it, lower-cased.
 */
fun searchableText(text: Map<Long, String>, people: List<Person>): Map<Long, String> {
    if (people.none { it.name != null }) return text
    val out = HashMap(text)
    for (person in people) {
        val name = person.name?.lowercase() ?: continue
        for (id in person.photoIds) out[id] = (out[id]?.let { "$it\n" } ?: "") + name
    }
    return out
}
