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

    /** Whether Gallery Plus reads the text in photos for search. On unless turned off. */
    val readText: Flow<Boolean> = context.settingsStore.data.map { it[READ_TEXT] ?: true }

    /** Turns reading text in photos on or off. */
    suspend fun setReadText(on: Boolean) {
        context.settingsStore.edit { it[READ_TEXT] = on }
    }

    /** Whether reading text waits for the phone to be charging. On unless turned off. */
    val readTextOnlyCharging: Flow<Boolean> = context.settingsStore.data.map { it[READ_TEXT_CHARGING] ?: true }

    /** Sets whether reading text waits for charging. */
    suspend fun setReadTextOnlyCharging(on: Boolean) {
        context.settingsStore.edit { it[READ_TEXT_CHARGING] = on }
    }

    private companion object {
        val SHOW_MEMORIES = booleanPreferencesKey("show_memories")
        val READ_TEXT = booleanPreferencesKey("read_text")
        val READ_TEXT_CHARGING = booleanPreferencesKey("read_text_only_charging")
    }
}
