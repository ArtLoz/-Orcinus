package app.orcinus.shadow

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.util.Log
import app.orcinus.shadow.core.model.PrintHostJob
import app.orcinus.shadow.core.model.PrintHostJobState
import app.orcinus.shadow.core.ui.orca.OrcaCatalog
import app.orcinus.shadow.core.ui.orca.OrcaCatalogs
import app.orcinus.shadow.core.ui.orca.orcaLanguageOf
import java.util.Locale
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
 * started but not in the foreground — and leaves when no work runs. While the
 * upload queue has a job, its notification is the job's, as Orca's
 * PrintHostUploadNotification shows it: "<file> -> <host>", the percentage
 * and megabytes uploaded, and a button that cancels the upload.
 */
class NetworkWorkService : Service() {
    private val scope = MainScope()
    private var lastStartId = 0
    private var watching = false
    private var foreground = false
    private var title = ""
    private var catalog = OrcaCatalog.EMPTY

    private val queue get() = (application as OrcinusApplication).container.printHostQueue

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        if (intent?.action == ACTION_CANCEL_UPLOAD) {
            // The notification's cancel button: PrintHostJobQueue::cancel().
            queue.cancel(intent.getIntExtra(EXTRA_JOB, 0))
            if (!foreground && NetworkWork.running.value == 0) stopSelf(startId)
            return START_NOT_STICKY
        }
        title = intent?.getStringExtra(NetworkWork.EXTRA_TITLE).orEmpty()
        val notification = notification(queue.jobs.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        foreground = true
        if (!watching) {
            watching = true
            scope.launch {
                NetworkWork.running.collect { count ->
                    // A later start, for work that began meanwhile, keeps the service.
                    if (count == 0 && stopSelfResult(lastStartId)) {
                        foreground = false
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    }
                }
            }
            scope.launch {
                catalog = OrcaCatalogs.load(this@NetworkWorkService, orcaLanguageOf(resources.configuration.locales[0] ?: Locale.getDefault()).catalog)
                // The upload's progress, at most a few times a second, which is as often as Android shows it; the
                // state flow keeps only the latest jobs meanwhile.
                queue.jobs.collect { jobs ->
                    if (foreground) getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(jobs))
                    delay(UPDATE_INTERVAL_MILLIS)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(jobs: List<PrintHostJob>): Notification {
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
        // Back to the app, where a login waits for the browser and the queue lists the uploads.
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            builder.setContentIntent(PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE))
        }
        val job = jobs.firstOrNull { it.state == PrintHostJobState.UPLOADING } ?: jobs.firstOrNull { it.state == PrintHostJobState.QUEUED }
        if (job != null) upload(builder, job)
        return builder.build()
    }

    /** PrintHostUploadNotification of [job]: its text, its bar, and its cancel button. */
    private fun upload(builder: Notification.Builder, job: PrintHostJob) {
        // get_upload_job_text()
        builder.setContentTitle("${job.uploadPath} -> ${job.host}")
        if (job.state == PrintHostJobState.UPLOADING) {
            // render_bar(): "45% - 1.23 of 2.71MB uploaded", untranslated in Orca.
            val size = job.size.coerceAtLeast(0) / MEGABYTE
            builder.setContentText(String.format(Locale.ROOT, "%d%% - %.2f of %.2fMB uploaded", job.progress, size * job.progress / 100, size))
            builder.setProgress(100, job.progress, false)
        } else {
            builder.setContentText(catalog.translate("Queued"))
            builder.setProgress(0, 0, true)
        }
        val cancel = PendingIntent.getService(
            this,
            job.id,
            Intent(this, NetworkWorkService::class.java).setAction(ACTION_CANCEL_UPLOAD).putExtra(EXTRA_JOB, job.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        builder.addAction(
            Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel), catalog.translate("Cancel upload"), cancel).build(),
        )
    }

    private companion object {
        // The slicing service's notification is 1, in the slicer's process.
        const val NOTIFICATION_ID = 2
        const val CHANNEL_ID = "network"
        const val ACTION_CANCEL_UPLOAD = "app.orcinus.shadow.action.CANCEL_UPLOAD"
        const val EXTRA_JOB = "app.orcinus.shadow.extra.UPLOAD_JOB"
        const val UPDATE_INTERVAL_MILLIS = 500L
        const val MEGABYTE = 1024.0 * 1024.0
    }
}
