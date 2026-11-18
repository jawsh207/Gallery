package app.roll.gallery.recognition

/**
 * The kinds of photo Gallery Plus sorts into Things on the Collections tab, each described by a
 * few phrases that TinyCLIP compares photos against.
 */
enum class Thing(val phrases: List<String>) {
    Documents(listOf("a photo of a document", "a page of printed text", "a photo of a form or letter")),
    Receipts(listOf("a photo of a receipt", "a shop receipt with prices")),
    Selfies(listOf("a selfie", "a close-up selfie of a face")),
    Pets(listOf("a photo of a dog", "a photo of a cat", "a photo of a pet")),
    Food(listOf("a photo of food", "a plate of food", "a meal on a table")),
    Landscapes(listOf("a landscape photo of nature", "mountains and sky", "a beach and the sea")),
    Flowers(listOf("a photo of flowers", "a close-up of a flower")),
    Cars(listOf("a photo of a car", "a car parked on a street")),
}

/**
 * Phrases for what most photos are that isn't one of the [Thing]s; a photo that matches one of
 * these best stays out of Things, which keeps each Thing from collecting near misses.
 */
val OTHER_PHRASES = listOf(
    "a photo of people", "a group of friends", "a photo of a room", "a photo of a building",
    "a screenshot of a phone screen", "a photo of a street", "a photo",
)

/** The smallest similarity a photo needs to its best phrase to be counted as that [Thing]. */
const val THING_MIN_SCORE = 0.22f

/**
 * Sorts photos into [Thing]s by their description fingerprints ([fingerprints], by photo id):
 * each photo goes to the Thing whose phrase it's most like, if that's closer than any of
 * [OTHER_PHRASES] and at least [minScore]. [phrases] holds each phrase's fingerprint. Returns
 * each Thing's photo ids, best match first; Things with no photos are left out.
 */
fun sortIntoThings(
    fingerprints: Map<Long, ByteArray>,
    phrases: Map<String, FloatArray>,
    minScore: Float = THING_MIN_SCORE,
): Map<Thing, List<Long>> {
    val owner = HashMap<String, Thing?>()
    for (thing in Thing.entries) for (phrase in thing.phrases) owner[phrase] = thing
    for (phrase in OTHER_PHRASES) owner[phrase] = null
    val found = HashMap<Thing, MutableList<Pair<Long, Float>>>()
    for ((id, packed) in fingerprints) {
        var best: String? = null
        var bestScore = Float.NEGATIVE_INFINITY
        for ((phrase, vector) in phrases) {
            if (phrase !in owner) continue
            val score = packedSimilarity(packed, vector)
            if (score > bestScore) {
                bestScore = score
                best = phrase
            }
        }
        val thing = best?.let { owner[it] } ?: continue
        if (bestScore >= minScore) found.getOrPut(thing) { ArrayList() } += id to bestScore
    }
    return found.mapValues { (_, hits) -> hits.sortedByDescending { it.second }.map { it.first } }
}
