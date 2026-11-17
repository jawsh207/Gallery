package app.roll.gallery.ui

import android.app.Application
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
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Short messages for the user ("Copied 3 items"), shown as a snackbar. */
    val messages: SharedFlow<String> = _messages
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
                _messages.emit(getApplication<Application>().resources.getQuantityString(R.plurals.lock_failed, failed, failed))
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
                _messages.emit(getApplication<Application>().resources.getQuantityString(R.plurals.locked_count, copies.size, copies.size))
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
                if (failed == 0) res.getQuantityString(R.plurals.moved_out, items.size, items.size)
                else res.getQuantityString(R.plurals.move_failed, failed, failed),
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

    /** Moves [items] into the folder [path] in the background (after a write request), then says how it went. */
    fun move(items: List<Media>, path: String) = runAndReport(items, R.plurals.moved, R.plurals.move_failed) { writer.move(items, path) }

    /** Sets [media] as wallpaper ([which]: home, lock or both), then says whether it worked. */
    fun setWallpaper(media: Media, which: Int) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { writer.setWallpaper(media, which) }
            _messages.emit(getApplication<Application>().getString(if (ok) R.string.wallpaper_set else R.string.wallpaper_failed))
        }
    }

    /** Runs [work] off the main thread and reports the count done ([done]) or failed ([failed]). */
    private fun runAndReport(items: List<Media>, done: Int, failed: Int, work: () -> Int) {
        viewModelScope.launch {
            val failures = withContext(Dispatchers.IO) { work() }
            val res = getApplication<Application>().resources
            _messages.emit(
                if (failures == 0) res.getQuantityString(done, items.size, items.size)
                else res.getQuantityString(failed, failures, failures),
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
            val app = getApplication<Application>()
            _messages.emit(app.getString(if (!saved) R.string.edit_save_failed else if (asCopy) R.string.edit_saved_copy else R.string.edit_saved))
            onSaved(saved)
        }
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
