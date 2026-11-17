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
import app.roll.gallery.data.Media
import android.os.BatteryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.TimeUnit

/**
 * Looks at every photo that hasn't been looked at yet (or has changed since), in the
 * background: reads its text into the [TextIndex] and finds its faces for the [FaceIndex],
 * whichever of the two is turned on. Each photo is decoded once for both, the next photo loads
 * while the current one is looked at, and text and faces are looked for at the same time. Each
 * model uses 2 processor cores, or 4 while the phone is charging. Results are saved in
 * small batches, so stopping part way loses little, and progress is reported after each batch
 * so Settings can update its counts. Android runs it when its conditions are met (by default
 * only while charging) and stops it when they aren't. Gallery Plus only; in the plain edition
 * there are no models and it does nothing. Videos and the Locked folder are never looked at.
 */
class RecognitionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
            return Result.success()
        }
        val readText = inputData.getBoolean(KEY_TEXT, false)
        val findFaces = inputData.getBoolean(KEY_FACES, false)
        // Twice the cores while plugged in, where speed matters more than battery.
        val charging = context.getSystemService(BatteryManager::class.java)?.isCharging == true
        val threads = if (charging) CHARGING_THREADS else BATTERY_THREADS
        val reader = if (readText) Edition.textReader(context, threads) else null
        val finder = if (findFaces) Edition.faceFinder(context, threads) else null
        if (reader == null && finder == null) return Result.success()
        val accelerated = listOfNotNull(reader?.accelerated, finder?.accelerated).all { it }

        val photos = MediaStoreSource(context.contentResolver).load().items.filter { !it.isVideo }
        val ids = photos.mapTo(HashSet()) { it.id }
        val textIndex = TextIndex(context)
        val faceIndex = FaceIndex(context)
        val knownText = if (reader != null) textIndex.load() else emptyMap()
        val knownFaces = if (finder != null) faceIndex.load() else emptyMap()
        // Tidy up: forget photos that are gone and drop repeated lines.
        if (reader != null && knownText.keys.any { it !in ids }) textIndex.rewrite(knownText.values.filter { it.id in ids })
        if (finder != null && knownFaces.keys.any { it !in ids }) faceIndex.rewrite(knownFaces.values.filter { it.id in ids })

        fun needsText(photo: Media) = reader != null && knownText[photo.id]?.modifiedAt != photo.modifiedAt
        fun needsFaces(photo: Media) = finder != null && knownFaces[photo.id]?.modifiedAt != photo.modifiedAt
        val todo = photos.filter { needsText(it) || needsFaces(it) }

        val renderer = EditRenderer(context)
        val texts = ArrayList<IndexEntry>()
        val faces = ArrayList<FaceEntry>()
        var batches = 0
        setProgress(workDataOf(PROGRESS to 0, ACCELERATED to accelerated))
        try {
            coroutineScope {
                fun load(photo: Media) = async(Dispatchers.IO) { runCatching { renderer.decode(photo, MAX_SIDE) }.getOrNull() }
                // The next photo loads while this one is looked at.
                var next = todo.firstOrNull()?.let(::load)
                for ((i, photo) in todo.withIndex()) {
                    if (isStopped) break
                    val bitmap = next!!.await()
                    next = todo.getOrNull(i + 1)?.let(::load)
                    // Text and faces are looked for at the same time, each on its own cores.
                    val text = if (needsText(photo)) {
                        async(Dispatchers.Default) { bitmap?.let { runCatching { reader!!.read(it) }.getOrNull() }.orEmpty() }
                    } else {
                        null
                    }
                    val found = if (needsFaces(photo)) {
                        async(Dispatchers.Default) { bitmap?.let { runCatching { finder!!.find(it) }.getOrNull() }.orEmpty() }
                    } else {
                        null
                    }
                    text?.let { texts += IndexEntry(photo.id, photo.modifiedAt, it.await()) }
                    found?.let { faces += FaceEntry(photo.id, photo.modifiedAt, it.await()) }
                    bitmap?.recycle()
                    if (texts.size + faces.size >= BATCH) {
                        textIndex.append(texts)
                        faceIndex.append(faces)
                        texts.clear()
                        faces.clear()
                        setProgress(workDataOf(PROGRESS to ++batches, ACCELERATED to accelerated))
                    }
                }
                next?.cancel()
            }
            textIndex.append(texts)
            faceIndex.append(faces)
        } finally {
            reader?.close()
            finder?.close()
        }
        return Result.success()
    }

    /** Starting, re-scheduling and stopping the work. */
    companion object {
        private const val PERIODIC = "recognition"
        private const val NOW = "recognition-now"
        // Names used before faces were added; cancelled so old schedules don't linger.
        private val OLD_NAMES = listOf("text-index", "text-index-now")
        private const val KEY_TEXT = "text"
        private const val KEY_FACES = "faces"
        private const val PROGRESS = "batches"
        /** The progress key saying whether the faster XNNPACK engine is in use. */
        const val ACCELERATED = "accelerated"
        private const val CHARGING_THREADS = 4
        private const val BATTERY_THREADS = 2
        private const val BATCH = 20
        // Photos are looked at no bigger than this on their longer side: plenty for text and
        // faces, quick to decode.
        private const val MAX_SIDE = 1280

        /** The names the work is scheduled under; screens watch them for progress. */
        val WORK_NAMES = listOf(PERIODIC, NOW)

        /**
         * Makes sure the work happens for what's turned on ([readText], [findFaces]): once soon,
         * and again every 12 hours for new photos, only while charging if [onlyWhileCharging].
         * With neither on, or in the plain edition, it stops instead. [restart] starts a run
         * under way over, for when the settings changed.
         */
        fun schedule(context: Context, readText: Boolean, findFaces: Boolean, onlyWhileCharging: Boolean, restart: Boolean) {
            val work = WorkManager.getInstance(context)
            OLD_NAMES.forEach(work::cancelUniqueWork)
            val text = readText && Edition.HAS_TEXT_SEARCH
            val people = findFaces && Edition.HAS_PEOPLE
            if (!text) TextIndex(context).clear()
            if (!people) FaceIndex(context).clear()
            if (!text && !people) {
                WORK_NAMES.forEach(work::cancelUniqueWork)
                return
            }
            val constraints = Constraints.Builder()
                .setRequiresCharging(onlyWhileCharging)
                .setRequiresBatteryNotLow(true)
                .build()
            val data = workDataOf(KEY_TEXT to text, KEY_FACES to people)
            work.enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<RecognitionWorker>(12, TimeUnit.HOURS).setConstraints(constraints).setInputData(data).build(),
            )
            // After a change of settings it's replaced, so the change takes effect now rather
            // than after a run that started with the old ones; otherwise a run under way is kept.
            work.enqueueUniqueWork(
                NOW,
                if (restart) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<RecognitionWorker>().setConstraints(constraints).setInputData(data).build(),
            )
        }
    }
}
