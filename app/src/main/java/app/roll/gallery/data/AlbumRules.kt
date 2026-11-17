package app.roll.gallery.data

/** Why a name can't be used for a new album. */
enum class AlbumNameProblem { Blank, Slash, Taken }

/**
 * What's wrong with [name] as a new album's name, or null if it's fine. An album is a folder,
 * so the name can't contain "/" or be empty, and shouldn't clash with an album already in
 * [existing] (ignoring case, as the phone's storage does).
 */
fun albumNameProblem(name: String, existing: Collection<String>): AlbumNameProblem? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() || trimmed == "." || trimmed == ".." -> AlbumNameProblem.Blank
        '/' in trimmed || '\\' in trimmed -> AlbumNameProblem.Slash
        existing.any { it.equals(trimmed, ignoreCase = true) } -> AlbumNameProblem.Taken
        else -> null
    }
}

/** The folder a new album named [name] is made in: under Pictures, which takes photos and videos. */
fun newAlbumPath(name: String): String = "Pictures/${name.trim()}/"

/**
 * Whether Android lets a photo (or a video, when [isVideo]) be saved in the folder [path].
 * Android only allows photos under DCIM or Pictures, and videos under DCIM, Movies or
 * Pictures; a move or copy anywhere else is refused, so those albums aren't offered.
 */
fun canHold(path: String, isVideo: Boolean): Boolean {
    val top = path.trim('/').substringBefore('/').lowercase()
    return top == "dcim" || top == "pictures" || (isVideo && top == "movies")
}
