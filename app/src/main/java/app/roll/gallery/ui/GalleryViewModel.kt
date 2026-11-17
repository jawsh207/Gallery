package app.roll.gallery.ui

import android.app.Application
import android.content.IntentSender
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.roll.gallery.data.CameraDetails
import app.roll.gallery.data.Library
import app.roll.gallery.data.Media
import app.roll.gallery.data.MediaStoreSource
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

    /** Camera details for [media]'s info sheet, read off the main thread. */
    suspend fun cameraDetails(media: Media): CameraDetails? = withContext(Dispatchers.IO) {
        readCameraDetails(getApplication<Application>().contentResolver, media)
    }
}
