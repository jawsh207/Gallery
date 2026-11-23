package app.roll.gallery.ui

import android.app.Application
import android.net.Uri
import android.content.IntentSender
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.roll.gallery.data.CameraDetails
import app.roll.gallery.data.Library
import app.roll.gallery.data.Media
import app.roll.gallery.R
import app.roll.gallery.data.MediaStoreSource
import app.roll.gallery.data.MediaWriter
import app.roll.gallery.Edition
import app.roll.gallery.recognition.TextIndex
import app.roll.gallery.recognition.RecognitionWorker
import app.roll.gallery.data.CollectionTile
import app.roll.gallery.data.AlbumPrefs
import app.roll.gallery.data.AlbumSort
import app.roll.gallery.data.setTakenAt
import app.roll.gallery.data.Cell
import app.roll.gallery.data.CollageShape
import app.roll.gallery.data.CreationWriter
import app.roll.gallery.data.Shot
import app.roll.gallery.data.VideoShape
import app.roll.gallery.data.makeVideo
import app.roll.gallery.data.VideoEdits
import app.roll.gallery.data.exportVideo
import app.roll.gallery.data.videoFrame
import app.roll.gallery.data.orderTiles
import app.roll.gallery.recognition.DescriptionIndex
import app.roll.gallery.recognition.TextDescriber
import app.roll.gallery.recognition.bestMatches
import app.roll.gallery.recognition.Thing
import app.roll.gallery.recognition.OTHER_PHRASES
import app.roll.gallery.recognition.sortIntoThings
import app.roll.gallery.recognition.FaceEntry
import app.roll.gallery.recognition.FaceIndex
import app.roll.gallery.recognition.PeopleNames
import app.roll.gallery.recognition.Person
import app.roll.gallery.recognition.buildPeople
import app.roll.gallery.recognition.searchableText
import androidx.work.WorkManager
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.merge
import app.roll.gallery.data.LockedFolder
import kotlinx.coroutines.flow.combine
import app.roll.gallery.data.EditRenderer
import app.roll.gallery.data.Edits
import android.graphics.Bitmap
import app.roll.gallery.data.DuplicateGroup
import app.roll.gallery.data.findDuplicates
import app.roll.gallery.data.hashPhoto
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import app.roll.gallery.data.MotionVideo
import app.roll.gallery.data.readMotionVideo
import app.roll.gallery.data.AppSettings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import app.roll.gallery.data.readCameraDetails
import app.roll.gallery.util.contentChanges
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/** A short message for the user, with something [undo] can reverse if there is one. */
data class Message(val text: String, val undo: Undo? = null)

/** What Undo on a message reverses. */
sealed interface Undo {
    /** Items just trashed: Undo takes them out again. */
    data class Untrash(val items: List<Media>) : Undo

    /** Items just moved into another album: Undo moves each back to its old folder ([paths], by id). */
    data class MoveBack(val paths: Map<Long, String>, val items: List<Media>) : Undo

    /** Locked folder copies of items just moved in: Undo moves them back out. */
    data class Unlock(val items: List<Media>) : Undo
}

/**
 * Holds the library for every screen and builds the change requests they send. The library
 * reloads whenever anything in Android's media store changes, so photos taken, deleted or
 * edited in other apps show up here by themselves.
 */
class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    private val source = MediaStoreSource(app.contentResolver)
    private val writer = MediaWriter(app)
    private val settings = AppSettings(app)

    /** Whether the Photos tab shows "On this day" memories. */
    val showMemories: StateFlow<Boolean> = settings.showMemories
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Turns memories on or off. */
    fun setShowMemories(show: Boolean) {
        viewModelScope.launch { settings.setShowMemories(show) }
    }

    /** Whether text in photos is read for search (Gallery Plus). */
    val readText: StateFlow<Boolean> = settings.readText.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Whether reading text waits for charging (Gallery Plus). */
    val readTextOnlyCharging: StateFlow<Boolean> = settings.readTextOnlyCharging.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Turns reading text on (and starts it) or off (stopping it and forgetting what was read). */
    fun setReadText(on: Boolean) {
        viewModelScope.launch { settings.setReadText(on) }
    }

    /** Sets whether reading text waits for charging, and re-schedules it. */
    fun setReadTextOnlyCharging(on: Boolean) {
        viewModelScope.launch { settings.setReadTextOnlyCharging(on) }
    }

    /** Whether faces are found and grouped into people (Gallery Plus). */
    val findPeople: StateFlow<Boolean> = settings.findPeople.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Turns finding people on (and starts it) or off (stopping it and forgetting the faces found). */
    fun setFindPeople(on: Boolean) {
        viewModelScope.launch { settings.setFindPeople(on) }
    }

    private val textIndex = TextIndex(app)
    private val faceIndex = FaceIndex(app)
    private val peopleNames = PeopleNames(app)
    private val _photoText = MutableStateFlow<Map<Long, String>>(emptyMap())
    private val _textAsRead = MutableStateFlow<Map<Long, String>>(emptyMap())
    private val _faces = MutableStateFlow<Map<Long, FaceEntry>>(emptyMap())
    private val _names = MutableStateFlow<Map<String, String>>(emptyMap())

    /** The text read in each photo so far, lower-cased for searching, by photo id (Gallery Plus). */
    val photoText: StateFlow<Map<Long, String>> = _photoText

    /** The text read in each photo, as read (lines and capitals kept), for copying (Gallery Plus). */
    val textAsRead: StateFlow<Map<Long, String>> = _textAsRead

    /** The faces found so far, by photo id, including photos found to have none (Gallery Plus). */
    val faces: StateFlow<Map<Long, FaceEntry>> = _faces

    /** Everyone found in the photos, worked out again whenever faces or names change (Gallery Plus). */
    val people: StateFlow<List<Person>> = combine(_faces, _names) { faces, names -> faces to names }
        .debounce(300)
        .map { (faces, names) -> buildPeople(faces, names) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** What search matches each photo against beyond its name and date: its text and its people's names. */
    val searchText: StateFlow<Map<Long, String>> = combine(_photoText, people) { text, people -> searchableText(text, people) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Whether photos are fingerprinted for searching by description (Gallery Plus). */
    val describePhotos: StateFlow<Boolean> = settings.describePhotos.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Turns searching by description on (and starts it) or off (forgetting the fingerprints). */
    fun setDescribePhotos(on: Boolean) {
        viewModelScope.launch { settings.setDescribePhotos(on) }
    }

    private val descriptionIndex = DescriptionIndex(app)
    private val _described = MutableStateFlow<Map<Long, ByteArray?>>(emptyMap())

    /** The photos fingerprinted for description search so far, by id (null for ones that couldn't be read). */
    val described: StateFlow<Map<Long, ByteArray?>> = _described

    private val _descriptionMatches = MutableStateFlow<Pair<String, Set<Long>>>("" to emptySet())

    /** The latest description search: its words, and the photos that match them best. */
    val descriptionMatches: StateFlow<Pair<String, Set<Long>>> = _descriptionMatches

    // Loaded the first time a description search is made, then kept while the app is open.
    private var textDescriber: TextDescriber? = null
    private var lastDescription: String? = null
    private var descriptionJob: Job? = null

    /**
     * Finds the photos best matching [query] as a description, in the background, into
     * [descriptionMatches]. Searches that are only dates or kinds ([isDescription]) are left out.
     */
    fun searchDescriptions(query: String, isDescription: Boolean) {
        if (!Edition.HAS_DESCRIPTIONS) return
        val words = query.trim()
        if (words == lastDescription) return
        lastDescription = words
        descriptionJob?.cancel()
        if (!isDescription || words.length < 3) {
            _descriptionMatches.value = words to emptySet()
            return
        }
        descriptionJob = viewModelScope.launch(Dispatchers.Default) {
            val fingerprints = _described.value.mapNotNull { (id, packed) -> packed?.let { id to it } }.toMap()
            if (fingerprints.isEmpty()) {
                _descriptionMatches.value = words to emptySet()
                return@launch
            }
            val describer = textDescriber ?: runCatching { Edition.textDescriber(getApplication()) }.getOrNull()?.also { textDescriber = it }
            val query = describer?.let { runCatching { it.describe(words) }.getOrNull() }
            _descriptionMatches.value = words to (query?.let { bestMatches(fingerprints, it).toSet() } ?: emptySet())
        }
    }

    // Each Thing's phrases as fingerprints, worked out once with the text half of TinyCLIP.
    private var phraseFingerprints: Map<String, FloatArray>? = null

    /** Photos sorted into Things (documents, pets, food...), by their description fingerprints (Gallery Plus). */
    val things: StateFlow<Map<Thing, List<Long>>> = combine(_described, describePhotos) { described, on -> if (on) described else emptyMap() }
        .debounce(1_000)
        .map { described ->
            val fingerprints = described.mapNotNull { (id, packed) -> packed?.let { id to it } }.toMap()
            if (fingerprints.isEmpty()) return@map emptyMap()
            val phrases = phraseFingerprints ?: run {
                val describer = textDescriber ?: runCatching { Edition.textDescriber(getApplication()) }.getOrNull()?.also { textDescriber = it }
                    ?: return@map emptyMap()
                (Thing.entries.flatMap { it.phrases } + OTHER_PHRASES).associateWith { describer.describe(it) }.also { phraseFingerprints = it }
            }
            sortIntoThings(fingerprints, phrases)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    override fun onCleared() {
        textDescriber?.close()
    }

    /** The Collections tab's tiles, in the order chosen. */
    val collectionOrder: StateFlow<List<CollectionTile>> = settings.collectionOrder
        .map { orderTiles(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, CollectionTile.entries)

    /** Shows the album [albumId] in the Photos grid, or keeps it out ([shown] false); it stays an album either way. */
    fun setAlbumInPhotos(albumId: Long, shown: Boolean) = updateAlbums { it.copy(hidden = if (shown) it.hidden - albumId else it.hidden + albumId) }

    /** Pins the album [albumId] to the top of the albums, after any pinned before it, or unpins it. */
    fun setAlbumPinned(albumId: Long, pinned: Boolean) =
        updateAlbums { it.copy(pinned = if (pinned) it.pinned - albumId + albumId else it.pinned - albumId) }

    /** Makes [photo] its album's cover. */
    fun setAlbumCover(photo: Media) = updateAlbums { it.copy(covers = it.covers + (photo.albumId to photo.id)) }

    /** Orders the albums by [sort] (after the pinned ones). */
    fun setAlbumSort(sort: AlbumSort) = updateAlbums { it.copy(sort = sort) }

    private fun updateAlbums(change: (AlbumPrefs) -> AlbumPrefs) {
        viewModelScope.launch { settings.updateAlbumPrefs(change) }
    }

    /** Whether the Photos grid stacks quick runs of shots. */
    val stackPhotos: StateFlow<Boolean> = settings.stackPhotos.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Turns stacking in the Photos grid on or off. */
    fun setStackPhotos(on: Boolean) {
        viewModelScope.launch { settings.setStackPhotos(on) }
    }

    /** Keeps [tiles] as the Collections tab's order. */
    fun setCollectionOrder(tiles: List<CollectionTile>) {
        viewModelScope.launch { settings.setCollectionOrder(tiles) }
    }

    /**
     * Whether to ask, once, to show notifications: Gallery Plus with some looking at photos
     * turned on, and not asked before. Null until the settings are read.
     */
    val shouldAskNotifications: StateFlow<Boolean?> = if (!Edition.HAS_TEXT_SEARCH && !Edition.HAS_PEOPLE && !Edition.HAS_DESCRIPTIONS) {
        MutableStateFlow(false)
    } else {
        combine(settings.askedNotifications, settings.readText, settings.findPeople, settings.describePhotos) { asked, text, faces, describe ->
            !asked && (text || faces || describe)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    }

    /** Notes that the notification question was asked. */
    fun askedNotifications() {
        viewModelScope.launch { settings.setAskedNotifications() }
    }

    /** Gives [person] a [name], or takes it away when blank. */
    fun renamePerson(person: Person, name: String) {
        viewModelScope.launch {
            val names = app.roll.gallery.recognition.renamePerson(_names.value, person, name)
            withContext(Dispatchers.IO) { peopleNames.save(names) }
            _names.value = names
        }
    }

    /** Re-reads what the background work has found (text, faces and names). */
    fun refreshRecognition() {
        if (!Edition.HAS_TEXT_SEARCH && !Edition.HAS_PEOPLE && !Edition.HAS_DESCRIPTIONS) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val text = textIndex.load()
                _textAsRead.value = text.mapValues { (_, entry) -> entry.text }.filterValues { it.isNotBlank() }
                _photoText.value = text.mapValues { (_, entry) -> entry.text.lowercase() }
                _faces.value = faceIndex.load()
                _names.value = peopleNames.load()
                _described.value = descriptionIndex.load().mapValues { it.value.second }
            }
            lastDescription = null
        }
    }

    init {
        if (Edition.HAS_TEXT_SEARCH || Edition.HAS_PEOPLE || Edition.HAS_DESCRIPTIONS) {
            // Keep the background work in step with the settings. The first time (the app
            // opening) a run already under way is left alone; after a change it starts over.
            viewModelScope.launch {
                var first = true
                combine(settings.readText, settings.findPeople, settings.describePhotos, settings.readTextOnlyCharging) { text, faces, describe, charging ->
                    listOf(text, faces, describe, charging)
                }.distinctUntilChanged().collect { (text, faces, describe, charging) ->
                    withContext(Dispatchers.IO) { RecognitionWorker.schedule(app, text, faces, describe, charging, restart = !first) }
                    first = false
                    refreshRecognition()
                }
            }
            // Re-read the indexes whenever the background work saves a batch or finishes, so
            // counts, people and search stay current without leaving the screen.
            viewModelScope.launch {
                val work = WorkManager.getInstance(app)
                RecognitionWorker.WORK_NAMES
                    .map { name -> work.getWorkInfosForUniqueWorkFlow(name) }
                    .merge()
                    .map { infos -> infos.joinToString { "${it.id}:${it.state}:${it.progress}" } }
                    .distinctUntilChanged()
                    .debounce(500)
                    .collect { refreshRecognition() }
            }
        }
    }

    private val _messages = MutableSharedFlow<Message>(extraBufferCapacity = 4)

    /** Short messages for the user ("Copied 3 items"), shown as a snackbar, some with Undo. */
    val messages: SharedFlow<Message> = _messages
    private val accessChanged = MutableStateFlow(0)

    private val lockedFolder = LockedFolder(app)
    private val lockedItems = MutableStateFlow<List<Media>>(emptyList())

    /** Every photo and video, the trash, and the Locked folder; null until the first load finishes. */
    val library: StateFlow<Library?> = accessChanged
        .flatMapLatest { contentChanges(app, MediaStore.AUTHORITY_URI) }
        // A camera burst or a big delete sends many change notices; reload once they settle.
        .debounce(300)
        .map { source.load() }
        .flowOn(Dispatchers.IO)
        .combine(lockedItems) { library, locked -> library.copy(locked = locked) }
        .combine(settings.albumPrefs) { library, prefs -> library.copy(albumPrefs = prefs) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _unlocked = MutableStateFlow(false)

    /** Whether the Locked folder has been opened with the screen lock since the app last left the screen. */
    val unlocked: StateFlow<Boolean> = _unlocked

    init {
        refreshLocked()
    }

    /** Re-reads the Locked folder's contents. */
    private fun refreshLocked() {
        viewModelScope.launch { lockedItems.value = withContext(Dispatchers.IO) { lockedFolder.list() } }
    }

    /** Marks the Locked folder open, after the screen lock was passed. */
    fun unlock() {
        _unlocked.value = true
    }

    /** Closes the Locked folder again; called whenever the app leaves the screen. */
    fun lock() {
        _unlocked.value = false
    }

    /**
     * Step one of moving [items] into the Locked folder: copies them in, and hands the ones that
     * copied to [onCopied], which asks Android to delete the originals. Step two is
     * [finishLocking].
     */
    fun startLocking(items: List<Media>, onCopied: (originals: List<Media>, copies: List<Media>) -> Unit) {
        viewModelScope.launch {
            val pairs = withContext(Dispatchers.IO) { items.mapNotNull { item -> lockedFolder.add(item)?.let { item to it } } }
            if (pairs.size < items.size) {
                val failed = items.size - pairs.size
                _messages.emit(Message(getApplication<Application>().resources.getQuantityString(R.plurals.lock_failed, failed, failed)))
            }
            if (pairs.isNotEmpty()) onCopied(pairs.map { it.first }, pairs.map { it.second })
        }
    }

    /**
     * Step two: if the user let Android delete the originals ([deleted]), the copies stay
     * locked; if not, they're removed again, so nothing ends up in two places.
     */
    fun finishLocking(copies: List<Media>, deleted: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { if (!deleted) copies.forEach(lockedFolder::delete) }
            refreshLocked()
            if (deleted) {
                _messages.emit(
                    Message(
                        getApplication<Application>().resources.getQuantityString(R.plurals.locked_count, copies.size, copies.size),
                        Undo.Unlock(copies),
                    ),
                )
            }
        }
    }

    /** Moves [items] out of the Locked folder, back among the phone's photos. */
    fun unlockItems(items: List<Media>) {
        viewModelScope.launch {
            val failed = withContext(Dispatchers.IO) { items.count { !lockedFolder.restore(it) } }
            refreshLocked()
            val res = getApplication<Application>().resources
            _messages.emit(
                Message(
                    if (failed == 0) res.getQuantityString(R.plurals.moved_out, items.size, items.size)
                    else res.getQuantityString(R.plurals.move_failed, failed, failed),
                ),
            )
        }
    }

    /** Deletes [items] from the Locked folder for good. */
    fun deleteLocked(items: List<Media>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { items.forEach(lockedFolder::delete) }
            refreshLocked()
        }
    }

    /** Reloads after the user grants (or changes) photo access. */
    fun onAccessChanged() {
        accessChanged.value++
    }

    /** See [MediaStoreSource.favoriteRequest]. */
    fun favoriteRequest(items: List<Media>, favorite: Boolean): IntentSender = source.favoriteRequest(items, favorite)

    /** See [MediaStoreSource.trashRequest]. */
    fun trashRequest(items: List<Media>, trash: Boolean): IntentSender = source.trashRequest(items, trash)

    /** See [MediaStoreSource.deleteRequest]. */
    fun deleteRequest(items: List<Media>): IntentSender = source.deleteRequest(items)

    /** A request for write access to [items], needed before moving files Gallery didn't make. */
    fun writeRequest(items: List<Media>): IntentSender = source.writeRequest(items)

    /** Copies [items] into the folder [path] in the background, then says how it went. */
    fun copy(items: List<Media>, path: String) = runAndReport(items, R.plurals.copied, R.plurals.copy_failed) { writer.copy(items, path) }

    /**
     * Moves [items] into the folder [path] in the background (after a write request), then says
     * how it went, with Undo putting each back where it was.
     */
    fun move(items: List<Media>, path: String) =
        runAndReport(items, R.plurals.moved, R.plurals.move_failed, Undo.MoveBack(items.associate { it.id to it.path }, items)) {
            writer.move(items, path)
        }

    /** Changes when [media] was taken to [millis] (after a write request), then says how it went. */
    fun setTakenAt(media: Media, millis: Long) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { getApplication<Application>().contentResolver.setTakenAt(media, millis) }
            tell(if (ok) R.string.date_changed else R.string.date_change_failed)
        }
    }

    /** Says [items] went to the trash, with Undo to bring them back. */
    fun trashed(items: List<Media>) {
        val res = getApplication<Application>().resources
        _messages.tryEmit(Message(res.getQuantityString(R.plurals.trashed, items.size, items.size), Undo.Untrash(items)))
    }

    /**
     * Carries out [undo] where no confirmation is needed: moving items back to their folders,
     * or out of the Locked folder again. Taking things out of the trash needs Android's
     * confirmation, so the screen does that with [trashRequest].
     */
    fun undo(undo: Undo) {
        when (undo) {
            is Undo.Untrash -> Unit
            is Undo.Unlock -> unlockItems(undo.items)
            is Undo.MoveBack -> viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    // The items were moved, so their current entries are found by id.
                    val now = source.load().items.associateBy { it.id }
                    undo.items.mapNotNull { now[it.id] }.groupBy { undo.paths[it.id] }.forEach { (path, group) ->
                        if (path != null) writer.move(group, path)
                    }
                }
            }
        }
    }

    /** Sets [media] as wallpaper ([which]: home, lock or both), then says whether it worked. */
    fun setWallpaper(media: Media, which: Int) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { writer.setWallpaper(media, which) }
            _messages.emit(Message(getApplication<Application>().getString(if (ok) R.string.wallpaper_set else R.string.wallpaper_failed)))
        }
    }

    /**
     * Runs [work] off the main thread and reports the count done ([done], with [undo] if given)
     * or failed ([failed]).
     */
    private fun runAndReport(items: List<Media>, done: Int, failed: Int, undo: Undo? = null, work: () -> Int) {
        viewModelScope.launch {
            val failures = withContext(Dispatchers.IO) { work() }
            val res = getApplication<Application>().resources
            _messages.emit(
                if (failures == 0) Message(res.getQuantityString(done, items.size, items.size), undo)
                else Message(res.getQuantityString(failed, failures, failures)),
            )
        }
    }

    /**
     * The duplicate finder's progress: how many photos have been looked at out of how many,
     * and the groups once it's done. Null before the first scan.
     */
    val duplicates: StateFlow<DuplicateScan?> get() = _duplicates
    private val _duplicates = MutableStateFlow<DuplicateScan?>(null)
    private var scanJob: Job? = null
    // Hashes by photo id, with the change time they were worked out for, so a second scan only
    // looks at new or edited photos. Kept in memory only.
    private val hashCache = HashMap<Long, Pair<Long, Long>>()

    /**
     * Looks through every photo in [library] for duplicates, in the background. Starting again
     * while a scan is running restarts it; earlier hashes are reused.
     */
    fun scanDuplicates(library: Library) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch(Dispatchers.Default) {
            val resolver = getApplication<Application>().contentResolver
            val photos = library.items.filter { !it.isVideo }
            val hashes = HashMap<Long, Long>(photos.size)
            photos.forEachIndexed { index, photo ->
                ensureActive()
                val cached = synchronized(hashCache) { hashCache[photo.id] }
                val hash = if (cached != null && cached.first == photo.modifiedAt) {
                    cached.second
                } else {
                    hashPhoto(resolver, photo)?.also { synchronized(hashCache) { hashCache[photo.id] = photo.modifiedAt to it } }
                }
                if (hash != null) hashes[photo.id] = hash
                if (index % 25 == 0) _duplicates.value = DuplicateScan(index, photos.size, null)
            }
            _duplicates.value = DuplicateScan(photos.size, photos.size, findDuplicates(photos, hashes))
        }
    }

    private val renderer = EditRenderer(app)

    /** [media] at editing-preview size (2048 pixels on its longer side), or null if it can't be read. */
    suspend fun editPreview(media: Media): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { renderer.decode(media, 2048) }.getOrNull()
    }

    /**
     * Saves [edits] of [media] in the background, over the original or as a copy, says how it
     * went, and calls [onSaved] with whether it worked.
     */
    fun saveEdits(media: Media, edits: Edits, asCopy: Boolean, onSaved: (Boolean) -> Unit) {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.Default) { renderer.save(media, edits, asCopy) } != null
            // Loads the library again once Android has looked the saved file over, in case its
            // change notice is slow, so the edit shows at once.
            if (saved) {
                launch {
                    kotlinx.coroutines.delay(700)
                    accessChanged.value++
                }
            }
            val app = getApplication<Application>()
            _messages.emit(Message(app.getString(if (!saved) R.string.edit_save_failed else if (asCopy) R.string.edit_saved_copy else R.string.edit_saved)))
            onSaved(saved)
        }
    }

    /** Shows [message] (a string resource, filled with [args]) over whichever screen is open. */
    fun tell(@androidx.annotation.StringRes message: Int, vararg args: Any) {
        _messages.tryEmit(Message(getApplication<Application>().getString(message, *args)))
    }

    private val creations = CreationWriter(app)
    private val _making = MutableStateFlow<Float?>(null)

    /** How far making a video is (0 to 1), or null when nothing is being made. */
    val making: StateFlow<Float?> = _making
    private var makingJob: Job? = null

    /**
     * Draws and saves a collage of [photos] in [cells] (see [CreationWriter.drawCollage]), then
     * calls [onDone] with whether it was saved.
     */
    fun saveCollage(
        photos: List<Media>,
        cells: List<Cell>,
        shape: CollageShape,
        gap: Float,
        corners: Float,
        background: Int,
        onDone: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            _making.value = 0f
            val saved = withContext(Dispatchers.Default) {
                runCatching {
                    val bitmap = creations.drawCollage(photos, cells, shape, gap, corners, background)
                    creations.saveImage(bitmap, "Collage").also { bitmap.recycle() }
                }.getOrNull()
            } != null
            _making.value = null
            tell(if (saved) R.string.made_collage else R.string.made_failed)
            onDone(saved)
        }
    }

    /**
     * Makes a video of [shots] (see [makeVideo]) and saves it to Movies/Gallery named after
     * [prefix], reporting progress in [making]; [onDone] gets whether it was saved.
     */
    fun makeMovie(shots: List<Shot>, shape: VideoShape, fill: Boolean, music: Uri?, prefix: String, onDone: (Boolean) -> Unit) {
        makingJob?.cancel()
        makingJob = viewModelScope.launch {
            _making.value = 0f
            val app = getApplication<Application>()
            val file = makeVideo(app, shots, shape, fill, music) { _making.value = it }
            val saved = file?.let { withContext(Dispatchers.IO) { creations.saveVideo(it, prefix).also { _ -> it.delete() } } } != null
            _making.value = null
            tell(if (saved) R.string.made_video else R.string.made_failed)
            onDone(saved)
        }
    }

    /**
     * Saves [edits] of the video [media] as a new copy beside it, reporting progress in
     * [making]; [onDone] gets whether it was saved.
     */
    fun saveVideoEdits(media: Media, edits: VideoEdits, onDone: (Boolean) -> Unit) {
        makingJob?.cancel()
        makingJob = viewModelScope.launch {
            _making.value = 0f
            val file = exportVideo(getApplication(), media, edits) { _making.value = it }
            val saved = file?.let { withContext(Dispatchers.IO) { creations.saveEditedVideo(it, media).also { _ -> it.delete() } } } != null
            _making.value = null
            tell(if (saved) R.string.edit_saved_copy else R.string.made_failed)
            onDone(saved)
        }
    }

    /** Saves the frame of the video [media] at [positionMs] as a photo in Pictures/Gallery. */
    fun saveFrame(media: Media, positionMs: Long) {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                videoFrame(getApplication(), media, positionMs)?.let { frame ->
                    creations.saveImage(frame, "Frame").also { frame.recycle() }
                }
            } != null
            tell(if (saved) R.string.frame_saved else R.string.made_failed)
        }
    }

    /** Stops making a video part way; nothing is saved. */
    fun cancelMaking() {
        makingJob?.cancel()
        makingJob = null
        _making.value = null
    }

    /** Where [media]'s motion video is, if it's a motion photo, read off the main thread. */
    suspend fun motionVideo(media: Media): MotionVideo? = withContext(Dispatchers.IO) {
        readMotionVideo(getApplication<Application>().contentResolver, media)
    }

    /** Camera details for [media]'s info sheet, read off the main thread. */
    suspend fun cameraDetails(media: Media): CameraDetails? = withContext(Dispatchers.IO) {
        readCameraDetails(getApplication<Application>().contentResolver, media)
    }
}

/** Where the duplicate finder is up to: [done] of [total] photos, and the [groups] once finished. */
data class DuplicateScan(val done: Int, val total: Int, val groups: List<DuplicateGroup>?)
