package app.roll.gallery.recognition

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.roll.gallery.Edition
import app.roll.gallery.data.EditRenderer
import app.roll.gallery.data.MediaStoreSource
import java.util.concurrent.TimeUnit

/**
 * Reads the text in every photo that hasn't been read yet (or has changed since), in the
 * background, and adds it to the [TextIndex] in small batches, so stopping part way loses
 * little. After each batch it reports progress, which Settings watches to update its count. Android runs it when its conditions are met (by default only while charging) and
 * stops it when they aren't. Gallery Plus only; in the plain edition there's no reader and it
 * does nothing. Videos and the Locked folder are never read.
 */
class TextIndexWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
            return Result.success()
        }
        val reader = Edition.textReader(context) ?: return Result.success()
        val index = TextIndex(context)
        val known = index.load()
        val photos = MediaStoreSource(context.contentResolver).load().items.filter { !it.isVideo }
        val ids = photos.mapTo(HashSet()) { it.id }
        // Tidy up: forget photos that are gone and drop repeated lines.
        if (known.keys.any { it !in ids }) index.rewrite(known.values.filter { it.id in ids })
        val todo = photos.filter { known[it.id]?.modifiedAt != it.modifiedAt }
        val renderer = EditRenderer(context)
        val batch = ArrayList<IndexEntry>()
        var saved = 0
        reader.use {
            for (photo in todo) {
                if (isStopped) break
                val text = runCatching {
                    val bitmap = renderer.decode(photo, MAX_SIDE)
                    try { reader.read(bitmap) } finally { bitmap.recycle() }
                }.getOrDefault("")
                batch += IndexEntry(photo.id, photo.modifiedAt, text)
                if (batch.size >= BATCH) {
                    index.append(batch)
                    batch.clear()
                    // Lets Settings update its count as reading goes.
                    setProgress(workDataOf(PROGRESS_READ to ++saved))
                }
            }
            index.append(batch)
        }
        return Result.success()
    }

    /** Starting, re-scheduling and stopping the reading. */
    companion object {
        /** The names the reading is scheduled under; screens watch them for progress. */
        val WORK_NAMES = listOf("text-index", "text-index-now")
        private const val PERIODIC = "text-index"
        private const val NOW = "text-index-now"
        private const val PROGRESS_READ = "batches"
        private const val BATCH = 20
        // Photos are read at most this big on their longer side: plenty for text, quick to decode.
        private const val MAX_SIDE = 1280

        /**
         * Makes sure reading happens: once soon, and again every 12 hours for new photos, only
         * while charging if [onlyWhileCharging]. Does nothing in the plain edition.
         */
        fun schedule(context: Context, onlyWhileCharging: Boolean) {
            if (!Edition.HAS_TEXT_SEARCH) return
            val constraints = Constraints.Builder()
                .setRequiresCharging(onlyWhileCharging)
                .setRequiresBatteryNotLow(true)
                .build()
            val work = WorkManager.getInstance(context)
            work.enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<TextIndexWorker>(12, TimeUnit.HOURS).setConstraints(constraints).build(),
            )
            work.enqueueUniqueWork(
                NOW,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<TextIndexWorker>().setConstraints(constraints).build(),
            )
        }

        /** Stops all reading and forgets what was read. */
        fun stop(context: Context) {
            val work = WorkManager.getInstance(context)
            work.cancelUniqueWork(PERIODIC)
            work.cancelUniqueWork(NOW)
            TextIndex(context).clear()
        }
    }
}
