package app.roll.gallery.recognition

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.work.ForegroundInfo
import app.roll.gallery.MainActivity
import app.roll.gallery.R

/**
 * The quiet notification shown while photos are being looked at for search, which Android
 * requires for a foreground job. It shows how many of [total] photos are done and opens
 * Gallery when tapped. Without notification permission Android still runs the job; the
 * notification just isn't shown (it's listed in the quick settings' active apps instead).
 */
class ScanNotification(private val context: Context, private val total: Int) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.scan_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    /** What WorkManager needs to run the job in the foreground, as media processing. */
    fun foregroundInfo(done: Int): ForegroundInfo =
        ForegroundInfo(ID, build(done), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)

    /** Shows that [done] photos have been looked at. */
    fun update(done: Int) {
        manager.notify(ID, build(done))
    }

    /** Takes the notification away once the job ends. */
    fun cancel() {
        manager.cancel(ID)
    }

    private fun build(done: Int): Notification {
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.scan_title))
            .setContentText(context.getString(R.string.scan_progress, done, total))
            .setProgress(total, done, false)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private companion object {
        const val CHANNEL = "scan"
        const val ID = 1
    }
}
