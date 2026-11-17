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
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Short messages for the user ("Copied 3 items"), shown as a snackbar. */
    val messages: SharedFlow<String> = _messages
    private val accessChanged = MutableStateFlow(0)

    /** Every photo and video plus the trash; null until the first load finishes. */
    val library: StateFlow<Library?> = accessChanged
        .flatMapLatest { contentChanges(app, MediaStore.AUTHORITY_URI) }
        // A camera burst or a big delete sends many change notices; reload once they settle.
        .debounce(300)
        .map { source.load() }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    /** Camera details for [media]'s info sheet, read off the main thread. */
    suspend fun cameraDetails(media: Media): CameraDetails? = withContext(Dispatchers.IO) {
        readCameraDetails(getApplication<Application>().contentResolver, media)
    }
}
