package app.roll.gallery.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
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

    private companion object {
        val SHOW_MEMORIES = booleanPreferencesKey("show_memories")
    }
}
