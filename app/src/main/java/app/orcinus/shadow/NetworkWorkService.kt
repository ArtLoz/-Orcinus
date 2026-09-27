package app.orcinus.shadow

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Network work the user started that runs on while the app's screen is left:
 * a cloud login that waits while the browser is in front, an upload to a
 * printer. Android 15 and later cut an app off the network as soon as it is
 * in the background (the APP_BACKGROUND firewall chain); the desktop app,
 * whose login dialogs wait behind the browser and whose uploads run in a queue
 * behind its window, never has to mind that. While some such work runs,
 * [NetworkWorkService] keeps the app in the foreground state the network is
 * allowed in.
 */
internal class NetworkWork(private val context: Context) {
    /** [block], with the network kept for it; [title] is what the notification says meanwhile. */
    suspend fun <T> keep(title: String, block: suspend () -> T): T {
        running.update { it + 1 }
        try {
            // The user has just asked for the work, so the app is in front and may start the service.
            context.startForegroundService(Intent(context, NetworkWorkService::class.java).putExtra(EXTRA_TITLE, title))
        } catch (error: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException: the work still runs while the app stays in front.
            Log.w(TAG, "Network work without a foreground service", error)
        }
        try {
            return block()
        } finally {
            running.update { it - 1 }
        }
    }

    internal companion object {
        /** How many pieces of work are running; the service stops when none is. */
        val running = MutableStateFlow(0)
        const val EXTRA_TITLE = "app.orcinus.shadow.extra.NETWORK_WORK_TITLE"
        private const val TAG = "NetworkWork"
    }
}

/**
 * The foreground service of [NetworkWork]. It enters the foreground as soon as
 * it is started — so a piece of work that ends at once never leaves it
 * started but not in the foreground — and leaves when no work runs.
 */
class NetworkWorkService : Service() {
    private val scope = MainScope()
    private var lastStartId = 0
    private var watching = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        val notification = notification(intent?.getStringExtra(NetworkWork.EXTRA_TITLE).orEmpty())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (!watching) {
            watching = true
            scope.launch {
                NetworkWork.running.collect { count ->
                    // A later start, for work that began meanwhile, keeps the service.
                    if (count == 0 && stopSelfResult(lastStartId)) stopForeground(STOP_FOREGROUND_REMOVE)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(title: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.network_notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        // Back to the app, where a login waits for the browser.
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            builder.setContentIntent(PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE))
        }
        return builder.build()
    }

    private companion object {
        // The slicing service's notification is 1, in the slicer's process.
        const val NOTIFICATION_ID = 2
        const val CHANNEL_ID = "network"
    }
}
