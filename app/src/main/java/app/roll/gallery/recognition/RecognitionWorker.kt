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
import android.graphics.Bitmap
import android.os.BatteryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.TimeUnit

/**
 * Looks at every photo that hasn't been looked at yet (or has changed since), in the
 * background: reads its text into the [TextIndex], finds its faces for the [FaceIndex], and
 * fingerprints it for searching by description in the [DescriptionIndex], whichever are turned
 * on. Newest photos go first. Each photo is decoded once and shared by the models that need
 * it, at a good size for text and faces, or small when only a description is needed. Only
 * the models with work left are loaded, and the cores are shared between them. Each model works through its own queue at its own pace, with a few
 * photos decoded ahead, so the quick ones aren't held up by the slow ones; each uses 2
 * processor cores, or a share of the performance cores while the phone is charging. Results are saved in
 * small batches, so stopping part way loses little, and progress is reported after each batch
 * so Settings can update its counts. It becomes a foreground job, with a notification, as
 * soon as Gallery is open (Android allows it no sooner), so Android doesn't stop it after 10
 * minutes. Android
 * runs it when its conditions are met (by default only while charging) and stops it when they
 * aren't. Gallery Plus only; in the plain edition there are no models and it does nothing.
 * Videos and the Locked folder are never looked at.
 */
class RecognitionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
            return Result.success()
        }
        val readText = inputData.getBoolean(KEY_TEXT, false)
        val findFaces = inputData.getBoolean(KEY_FACES, false)
        val describe = inputData.getBoolean(KEY_DESCRIBE, false)
        if (!readText && !findFaces && !describe) return Result.success()

        val photos = MediaStoreSource(context.contentResolver).load().items.filter { !it.isVideo }
        val ids = photos.mapTo(HashSet()) { it.id }
        val textIndex = TextIndex(context)
        val faceIndex = FaceIndex(context)
        val descriptionIndex = DescriptionIndex(context)
        val knownText = if (readText) textIndex.load() else emptyMap()
        val knownFaces = if (findFaces) faceIndex.load() else emptyMap()
        val knownDescriptions = if (describe) descriptionIndex.load() else emptyMap()
        // Tidy up: forget photos that are gone and drop repeated lines.
        if (readText && knownText.keys.any { it !in ids }) textIndex.rewrite(knownText.values.filter { it.id in ids })
        if (findFaces && knownFaces.keys.any { it !in ids }) faceIndex.rewrite(knownFaces.values.filter { it.id in ids })
        if (describe && knownDescriptions.keys.any { it !in ids }) {
            descriptionIndex.rewrite(knownDescriptions.filterKeys { it in ids })
        }

        fun needsText(photo: Media) = readText && knownText[photo.id]?.modifiedAt != photo.modifiedAt
        fun needsFaces(photo: Media) = findFaces && knownFaces[photo.id]?.modifiedAt != photo.modifiedAt
        fun needsDescription(photo: Media) = describe && knownDescriptions[photo.id]?.first != photo.modifiedAt
        // Newest first: those are the photos most likely to be searched for.
        val queue = photos.filter { needsText(it) || needsFaces(it) || needsDescription(it) }.sortedByDescending { it.takenAt }
        if (queue.isEmpty()) return Result.success()

        // Only the models with something left to do are loaded, and the cores are shared out
        // between them, since they all run at once: every performance core while plugged in
        // (where speed matters more than battery), 2 each otherwise.
        val textWork = queue.any(::needsText)
        val faceWork = queue.any(::needsFaces)
        val describeWork = queue.any(::needsDescription)
        val charging = context.getSystemService(BatteryManager::class.java)?.isCharging == true
        val active = listOf(textWork, faceWork, describeWork).count { it }
        val share = if (charging) (performanceCores() / active).coerceAtLeast(1) else BATTERY_THREADS
        // Reading text is the slowest, so it gets any cores left over.
        val textThreads = if (charging) (performanceCores() - share * (active - 1)).coerceAtLeast(1) else BATTERY_THREADS
        val reader = if (textWork) Edition.textReader(context, textThreads) else null
        val finder = if (faceWork) Edition.faceFinder(context, share) else null
        val describer = if (describeWork) Edition.imageDescriber(context, share) else null
        if (reader == null && finder == null && describer == null) return Result.success()

        val notifications = ScanNotification(context, queue.size)
        // A foreground job isn't stopped after Android's usual 10 minutes for background work.
        // Android only allows becoming one while Gallery is open, so it's tried again after each
        // batch until it works: a run that started with Gallery closed moves to the foreground
        // the next time Gallery is opened.
        val inForeground = java.util.concurrent.atomic.AtomicBoolean(false)
        suspend fun tryForeground(done: Int) {
            if (inForeground.get()) return
            try {
                setForeground(notifications.foregroundInfo(done))
                inForeground.set(true)
            } catch (_: IllegalStateException) {
            }
        }
        tryForeground(0)

        val renderer = EditRenderer(context)
        val batches = AtomicInteger()
        val lanes = ArrayList<Lane<*>>()
        try {
            coroutineScope {
                // Each model works through the photos at its own pace, so a quick one isn't
                // held up by a slow one; each has its own few decoded photos waiting.
                val text = reader?.let { r ->
                    lane(lanes, { r.read(it) }) { done -> textIndex.append(done.map { (p, t) -> IndexEntry(p.id, p.modifiedAt, t.orEmpty()) }) }
                }
                val faces = finder?.let { f ->
                    lane(lanes, { f.find(it) }) { done -> faceIndex.append(done.map { (p, found) -> FaceEntry(p.id, p.modifiedAt, found.orEmpty()) }) }
                }
                val fingerprints = describer?.let { d ->
                    lane(lanes, { packFingerprint(d.describe(it)) }) { done -> descriptionIndex.append(done.map { (p, f) -> Triple(p.id, p.modifiedAt, f) }) }
                }
                lanes.forEach { lane ->
                    lane.onBatch = {
                        setProgress(workDataOf(PROGRESS to batches.incrementAndGet()))
                        val done = lanes.minOf { it.done.get() }
                        if (inForeground.get()) notifications.update(done) else tryForeground(done)
                    }
                }
                try {
                    for (photo in queue) {
                        if (isStopped) break
                        val wanted = listOfNotNull(
                            text?.takeIf { needsText(photo) },
                            faces?.takeIf { needsFaces(photo) },
                            fingerprints?.takeIf { needsDescription(photo) },
                        )
                        if (wanted.isEmpty()) continue
                        val bitmap = withContext(Dispatchers.IO) {
                            runCatching {
                                // Text and faces need the photo at a good size; a description
                                // only needs 224 pixels, and a photo loaded that small is
                                // much quicker (the decoder skips most of the detail).
                                renderer.decode(photo, if (needsText(photo) || needsFaces(photo)) MAX_SIDE else DESCRIBE_SIDE)
                            }.getOrNull()
                        }
                        val shared = SharedBitmap(bitmap, wanted.size)
                        wanted.forEach { it.photos.send(Scan(photo, shared)) }
                    }
                } finally {
                    lanes.forEach { it.photos.close() }
                }
            }
        } finally {
            reader?.close()
            finder?.close()
            describer?.close()
            notifications.cancel()
        }
        return Result.success()
    }

    /** One photo on its way to a model, with the decoded picture it shares with the others. */
    private class Scan(val photo: Media, val image: SharedBitmap)

    /** A decoded photo shared by several models; freed once the last of them is done with it. */
    private class SharedBitmap(val bitmap: Bitmap?, users: Int) {
        private val left = AtomicInteger(users)

        fun release() {
            if (left.decrementAndGet() == 0) bitmap?.recycle()
        }
    }

    /** One model's queue of photos, and how many it has finished. */
    private class Lane<T>(val photos: Channel<Scan>) {
        val done = AtomicInteger()
        var onBatch: suspend () -> Unit = {}
    }

    /**
     * Starts a model working through its own queue: [look] runs on each photo (a failure counts
     * as nothing found), and results are handed to [save] every [BATCH] photos and at the end,
     * even when the work is being stopped.
     */
    private fun <T> CoroutineScope.lane(
        lanes: MutableList<Lane<*>>,
        look: (Bitmap) -> T,
        save: (List<Pair<Media, T?>>) -> Unit,
    ): Lane<T> {
        val lane = Lane<T>(Channel(AHEAD))
        lanes += lane
        launch(Dispatchers.Default) {
            val found = ArrayList<Pair<Media, T?>>()
            try {
                for (scan in lane.photos) {
                    val result = scan.image.bitmap?.let { runCatching { look(it) }.getOrNull() }
                    scan.image.release()
                    found += scan.photo to result
                    lane.done.incrementAndGet()
                    if (found.size >= BATCH) {
                        save(found)
                        found.clear()
                        lane.onBatch()
                    }
                }
            } finally {
                withContext(NonCancellable) { save(found) }
            }
        }
        return lane
    }

    /** Starting, re-scheduling and stopping the work. */
    companion object {
        private const val PERIODIC = "recognition"
        private const val NOW = "recognition-now"
        // Names used before faces were added; cancelled so old schedules don't linger.
        private val OLD_NAMES = listOf("text-index", "text-index-now")
        private const val KEY_TEXT = "text"
        private const val KEY_FACES = "faces"
        private const val KEY_DESCRIBE = "describe"
        private const val PROGRESS = "batches"
        private const val BATTERY_THREADS = 2
        private const val BATCH = 20
        // Photos decoded ahead for each model: enough to keep it busy, few enough for memory.
        private const val AHEAD = 3
        // Big enough for TinyCLIP's 224-pixel square from most photos' shorter side.
        private const val DESCRIBE_SIDE = 448
        // Photos are looked at no bigger than this on their longer side: plenty for text and
        // faces, quick to decode.
        private const val MAX_SIDE = 1280

        /** The names the work is scheduled under; screens watch them for progress. */
        val WORK_NAMES = listOf(PERIODIC, NOW)

        /**
         * Makes sure the work happens for what's turned on ([readText], [findFaces], [describe]): once soon,
         * and again every 12 hours for new photos, only while charging if [onlyWhileCharging].
         * With neither on, or in the plain edition, it stops instead. [restart] starts a run
         * under way over, for when the settings changed.
         */
        fun schedule(
            context: Context,
            readText: Boolean,
            findFaces: Boolean,
            describe: Boolean,
            onlyWhileCharging: Boolean,
            restart: Boolean,
        ) {
            val work = WorkManager.getInstance(context)
            OLD_NAMES.forEach(work::cancelUniqueWork)
            val text = readText && Edition.HAS_TEXT_SEARCH
            val people = findFaces && Edition.HAS_PEOPLE
            val descriptions = describe && Edition.HAS_DESCRIPTIONS
            if (!text) TextIndex(context).clear()
            if (!people) FaceIndex(context).clear()
            if (!descriptions) DescriptionIndex(context).clear()
            if (!text && !people && !descriptions) {
                WORK_NAMES.forEach(work::cancelUniqueWork)
                return
            }
            val constraints = Constraints.Builder()
                .setRequiresCharging(onlyWhileCharging)
                .setRequiresBatteryNotLow(true)
                .build()
            val data = workDataOf(KEY_TEXT to text, KEY_FACES to people, KEY_DESCRIBE to descriptions)
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
