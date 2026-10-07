package app.orcinus.shadow.feature.prepare

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.orcinus.shadow.core.designsystem.component.OrcaNotification
import app.orcinus.shadow.core.designsystem.component.OrcaNotificationLevel
import app.orcinus.shadow.core.designsystem.component.OrcaNotificationText
import app.orcinus.shadow.core.designsystem.component.OrcaProgressNotification
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.PlateJob
import app.orcinus.shadow.core.model.PlateJobProgress
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.orca.orcaText
import kotlinx.coroutines.delay

/**
 * NotificationProgressIndicator of ArrangeJob, OrientJob and FillBedJob: the
 * status the job tells (Ctl::update_status()), "Arranging", "Orienting..." or
 * "Filling" at its start and with the name of the object it got to after,
 * the bar with its percent, and Cancel. Its last status closes it at once
 * (ProgressIndicatorNotification::init() of PIS_COMPLETED), so "Arranging
 * done." and the job's other last words never show.
 */
@Composable
internal fun PlateJobNotification(progress: PlateJobProgress, onCancel: () -> Unit) {
    val name = progress.name
    val status = when (progress.job) {
        // ArrangeJob::process(): the name follows "Arranging" directly.
        PlateJob.ARRANGE -> orcaString("Arranging") + name
        PlateJob.ORIENT -> if (name.isEmpty()) orcaString("Orienting...") else orcaString("Orienting") + " " + name
        PlateJob.FILL_BED -> if (name.isEmpty()) orcaString("Filling") else orcaString("Filling") + " " + name
    }
    OrcaProgressNotification(
        title = status,
        // ProgressBarNotification::render_bar(): the percent under the bar.
        detail = "${progress.percent}%",
        progress = progress.percent / 100f,
        cancelLabel = orcaString("Cancel"),
        onCancel = onCancel,
    )
}

/**
 * NotificationType::ArrangeOngoing, "Arranging...", which ArrangeJob::prepare()
 * pushes: a regular notification, gone after its ten seconds
 * (get_standard_duration()) unless the arrangement's finalize() closes it
 * first, or its close button.
 */
@Composable
internal fun ArrangeOngoingNotification(job: Long, onClose: (Long) -> Unit) {
    LaunchedEffect(job) {
        delay(REGULAR_NOTIFICATION_MILLIS)
        onClose(job)
    }
    OrcaNotification(onClose = { onClose(job) }) {
        OrcaNotificationText(orcaString("Arranging..."))
    }
}

/** ArrangeJob::check_unprintable()'s warning (BBLPlateInfo) of the object [name], which has no area. */
@Composable
internal fun ZeroSizeObjectNotification(name: String, onClose: () -> Unit) {
    OrcaNotification(level = OrcaNotificationLevel.Warning, onClose = onClose) {
        OrcaNotificationText(orcaText(OrcaText("Object %s has zero size and can't be arranged.", listOf(name))))
    }
}

/** get_standard_duration() of RegularNotificationLevel: ten seconds. */
private const val REGULAR_NOTIFICATION_MILLIS = 10_000L
