package app.orcinus.shadow.slicing.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import app.orcinus.shadow.core.model.SliceProgress
import app.orcinus.shadow.core.model.SliceRequest
import java.io.File
import kotlin.math.roundToInt

/** The ongoing notification of the foreground slicing job. */
internal class SliceNotifications(
    private val context: Context,
    private val serviceClass: Class<out Service>,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private var active = false
    private var lastPercent = -1
    private var lastDetail: String? = null

    init {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.slicing_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    @Synchronized
    fun started(request: SliceRequest): Notification {
        active = true
        lastPercent = -1
        lastDetail = null
        return build(request, percent = 0, detail = null)
    }

    /** Re-posts the notification only when the visible percent or text changes. */
    @Synchronized
    fun update(request: SliceRequest, progress: SliceProgress) {
        val percent = (progress.fraction * 100).roundToInt()
        if (!active || (percent == lastPercent && progress.detail == lastDetail)) return
        lastPercent = percent
        lastDetail = progress.detail
        manager.notify(NOTIFICATION_ID, build(request, percent, progress.detail))
    }

    /** Called after stopForeground; later progress no longer re-posts the notification. */
    @Synchronized
    fun stopped() {
        active = false
        manager.cancel(NOTIFICATION_ID)
    }

    private fun build(request: SliceRequest, percent: Int, detail: String?): Notification {
        val cancelIntent = PendingIntent.getService(
            context,
            0,
            Intent(context, serviceClass)
                .setAction(SlicerService.ACTION_CANCEL)
                .putExtra(SlicerService.EXTRA_JOB_ID, request.jobId.value),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_slicing_notification)
            .setContentTitle(
                context.getString(R.string.slicing_notification_title, File(request.output.value).nameWithoutExtension),
            )
            .setContentText(detail ?: context.getString(R.string.slicing_notification_preparing))
            .setProgress(100, percent, detail == null)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                Notification.Action.Builder(
                    null as Icon?,
                    context.getString(R.string.slicing_notification_cancel),
                    cancelIntent,
                ).build(),
            )
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launch ->
            builder.setContentIntent(
                PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE),
            )
        }
        return builder.build()
    }

    companion object {
        const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "slicing"
    }
}
