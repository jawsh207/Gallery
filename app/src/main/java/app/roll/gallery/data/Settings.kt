package app.roll.gallery.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The one settings file, in the app's private storage. */
private val Context.settingsStore by preferencesDataStore(name = "settings")

/**
 * Gallery's own settings. Only choices about how the app looks or behaves live here; nothing
 * about the photos themselves, which stay in Android's media store.
 */
class AppSettings(private val context: Context) {
    /** Whether the Photos tab shows "On this day" memories. On unless turned off. */
    val showMemories: Flow<Boolean> = context.settingsStore.data.map { it[SHOW_MEMORIES] ?: true }

    /** Turns memories on or off. */
    suspend fun setShowMemories(show: Boolean) {
        context.settingsStore.edit { it[SHOW_MEMORIES] = show }
    }

    /** Whether Gallery Plus reads the text in photos for search. On unless turned off. */
    val readText: Flow<Boolean> = context.settingsStore.data.map { it[READ_TEXT] ?: true }

    /** Turns reading text in photos on or off. */
    suspend fun setReadText(on: Boolean) {
        context.settingsStore.edit { it[READ_TEXT] = on }
    }

    /** Whether reading text and finding people wait for the phone to be charging. On unless turned off. */
    val readTextOnlyCharging: Flow<Boolean> = context.settingsStore.data.map { it[READ_TEXT_CHARGING] ?: true }

    /** Sets whether reading text waits for charging. */
    suspend fun setReadTextOnlyCharging(on: Boolean) {
        context.settingsStore.edit { it[READ_TEXT_CHARGING] = on }
    }

    /** Whether Gallery Plus finds faces and groups them into people. On unless turned off. */
    val findPeople: Flow<Boolean> = context.settingsStore.data.map { it[FIND_PEOPLE] ?: true }

    /** Turns finding people on or off. */
    suspend fun setFindPeople(on: Boolean) {
        context.settingsStore.edit { it[FIND_PEOPLE] = on }
    }

    /** Whether Gallery Plus fingerprints photos for searching by description. On unless turned off. */
    val describePhotos: Flow<Boolean> = context.settingsStore.data.map { it[DESCRIBE] ?: true }

    /** Turns searching by description on or off. */
    suspend fun setDescribePhotos(on: Boolean) {
        context.settingsStore.edit { it[DESCRIBE] = on }
    }

    /** Whether Gallery Plus has already asked once to show notifications. */
    val askedNotifications: Flow<Boolean> = context.settingsStore.data.map { it[ASKED_NOTIFICATIONS] ?: false }

    /** Notes that the notification question was asked, so it isn't asked again. */
    suspend fun setAskedNotifications() {
        context.settingsStore.edit { it[ASKED_NOTIFICATIONS] = true }
    }

    /** The order of the Collections tab's tiles, by name (see [orderTiles]); empty for the usual order. */
    val collectionOrder: Flow<List<String>> = context.settingsStore.data.map { prefs ->
        prefs[COLLECTION_ORDER]?.split(',')?.filter { it.isNotBlank() }.orEmpty()
    }

    /** Keeps [tiles] as the Collections tab's order. */
    suspend fun setCollectionOrder(tiles: List<CollectionTile>) {
        context.settingsStore.edit { it[COLLECTION_ORDER] = tiles.joinToString(",") { tile -> tile.name } }
    }

    /** The choices made about albums: kept out of Photos, pinned, covers and order. */
    val albumPrefs: Flow<AlbumPrefs> = context.settingsStore.data.map { albumPrefsIn(it) }

    /** Changes the album choices with [change]. */
    suspend fun updateAlbumPrefs(change: (AlbumPrefs) -> AlbumPrefs) {
        context.settingsStore.edit { prefs ->
            val now = change(albumPrefsIn(prefs))
            prefs[HIDDEN_ALBUMS] = now.hidden.joinToString(",")
            prefs[PINNED_ALBUMS] = now.pinned.joinToString(",")
            prefs[ALBUM_COVERS] = now.covers.entries.joinToString(",") { "${it.key}:${it.value}" }
            prefs[ALBUM_SORT] = now.sort.name
        }
    }

    private fun albumPrefsIn(prefs: androidx.datastore.preferences.core.Preferences) = AlbumPrefs(
        hidden = ids(prefs[HIDDEN_ALBUMS]).toSet(),
        pinned = ids(prefs[PINNED_ALBUMS]),
        covers = prefs[ALBUM_COVERS].orEmpty().split(',').mapNotNull { pair ->
            val parts = pair.split(':')
            if (parts.size != 2) null else parts[0].toLongOrNull()?.let { album -> parts[1].toLongOrNull()?.let { album to it } }
        }.toMap(),
        sort = AlbumSort.entries.firstOrNull { it.name == prefs[ALBUM_SORT] } ?: AlbumSort.Recent,
    )

    private fun ids(text: String?): List<Long> = text.orEmpty().split(',').mapNotNull { it.toLongOrNull() }

    private companion object {
        val HIDDEN_ALBUMS = stringPreferencesKey("hidden_albums")
        val PINNED_ALBUMS = stringPreferencesKey("pinned_albums")
        val ALBUM_COVERS = stringPreferencesKey("album_covers")
        val ALBUM_SORT = stringPreferencesKey("album_sort")
        val COLLECTION_ORDER = stringPreferencesKey("collection_order")
        val ASKED_NOTIFICATIONS = booleanPreferencesKey("asked_notifications")
        val DESCRIBE = booleanPreferencesKey("describe_photos")
        val FIND_PEOPLE = booleanPreferencesKey("find_people")
        val SHOW_MEMORIES = booleanPreferencesKey("show_memories")
        val READ_TEXT = booleanPreferencesKey("read_text")
        val READ_TEXT_CHARGING = booleanPreferencesKey("read_text_only_charging")
    }
}
