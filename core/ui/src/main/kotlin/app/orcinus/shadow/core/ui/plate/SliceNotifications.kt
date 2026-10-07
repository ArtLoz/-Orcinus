package app.orcinus.shadow.core.ui.plate

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.R as DesignR
import app.orcinus.shadow.core.designsystem.component.LocalOrcaNotificationLabels
import app.orcinus.shadow.core.designsystem.component.OrcaNotification
import app.orcinus.shadow.core.designsystem.component.OrcaNotificationLabels
import app.orcinus.shadow.core.designsystem.component.OrcaNotificationLevel
import app.orcinus.shadow.core.designsystem.component.OrcaNotificationLink
import app.orcinus.shadow.core.designsystem.component.OrcaNotificationText
import app.orcinus.shadow.core.designsystem.component.OrcaProgressNotification
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.SliceNotice
import app.orcinus.shadow.core.model.SliceNoticeLevel
import app.orcinus.shadow.core.ui.R
import app.orcinus.shadow.core.ui.orca.OrcaHintsFile
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.orca.orcaText
import kotlinx.coroutines.delay

/**
 * The labels of every notification in [content], in the app's language:
 * OrcaSlicer's "Close" and "More", and the minimize button's own.
 */
@Composable
fun ProvideOrcaNotificationLabels(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalOrcaNotificationLabels provides OrcaNotificationLabels(
            close = orcaString("Close"),
            more = orcaString("More"),
            minimize = stringResource(R.string.notification_minimize),
        ),
        content = content,
    )
}

/**
 * SlicingProgressNotification's completed state: the complete icon and
 * "Slice ok." in bold, for three seconds (get_duration()) after every slice
 * that went through ([completions] counts them), with its close button. A
 * slice that finished before the canvas showed is not announced again. It
 * stays composed while a slice runs ([sliceRunning]), when the progress takes
 * its place, so the slice that ends is announced. Hovering it starts its time
 * again (EState::Hovered); on a touch screen a touch does, so the daily tips
 * under it can be read.
 */
@Composable
fun SliceCompletedNotification(completions: Int, sliceRunning: Boolean = false, tips: (@Composable () -> Unit)? = null) =
    SliceEndedNotification(completions, sliceRunning, tips) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(DesignR.drawable.orca_notification_slicing_complete), contentDescription = null, modifier = Modifier.size(24.dp))
            Row(Modifier.padding(start = 6.dp)) { OrcaNotificationText(orcaString("Slice ok."), emphasized = true) }
        }
    }

/**
 * SlicingProgressNotification's cancelled state (set_slicing_progress_canceled()):
 * "Slicing Canceled" in bold for three seconds after every slice that was
 * cancelled ([cancellations] counts them), as the completed state shows.
 */
@Composable
fun SliceCancelledNotification(cancellations: Int, sliceRunning: Boolean = false, tips: (@Composable () -> Unit)? = null) =
    SliceEndedNotification(cancellations, sliceRunning, tips) {
        OrcaNotificationText(orcaString("Slicing Canceled"), emphasized = true)
    }

/** The completed and the cancelled state of SlicingProgressNotification, after every slice that ended so ([count]). */
@Composable
private fun SliceEndedNotification(count: Int, sliceRunning: Boolean, tips: (@Composable () -> Unit)?, content: @Composable () -> Unit) {
    var announced by remember { mutableIntStateOf(count) }
    var shown by remember { mutableStateOf(false) }
    var touches by remember { mutableIntStateOf(0) }
    LaunchedEffect(count) {
        if (count == announced) return@LaunchedEffect
        announced = count
        shown = true
    }
    LaunchedEffect(shown, touches) {
        if (!shown) return@LaunchedEffect
        delay(SLICE_ENDED_MILLIS)
        shown = false
    }
    if (!shown || sliceRunning) return
    val touched = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            touches++
        }
    }
    OrcaNotification(modifier = touched, onClose = { shown = false }, collapsible = false) {
        content()
        // The daily tips stay under the completed and the cancelled state.
        tips?.invoke()
    }
}

/**
 * SlicingProgressNotification while a slice runs: its status and progress
 * with Cancel, and DailyTipsPanel under them ([showHints], show_hints, and
 * its keeping). Every slice that begins draws another hint.
 */
@Composable
fun SlicingNotification(
    job: Any,
    title: String,
    detail: String?,
    progress: Float,
    cancelLabel: String,
    onCancel: () -> Unit,
    showHints: Boolean,
    onShowHints: (Boolean) -> Unit,
) {
    LaunchedEffect(job) { OrcaHintsFile.loaded?.size?.let(DailyTips::random) }
    OrcaProgressNotification(
        title = title,
        detail = detail,
        progress = progress,
        cancelLabel = cancelLabel,
        onCancel = onCancel,
        below = { DailyTipsPanel(showHints, onShowHints, Modifier.widthIn(max = 280.dp)) },
    )
}

/**
 * NotificationManager::push_exporting_finished_notification(): "Exported
 * successfully" and the file's [name], for 20 seconds, with its close button.
 * Its "Open Folder." has no counterpart: Android has no common way to show the
 * folder of a document.
 */
@Composable
fun ExportFinishedNotification(name: String, onClose: () -> Unit) {
    LaunchedEffect(name) {
        delay(EXPORT_FINISHED_MILLIS)
        onClose()
    }
    OrcaNotification(onClose = onClose, collapsible = false) {
        OrcaNotificationText(orcaString("Exported successfully"))
        OrcaNotificationText(name)
    }
}

/**
 * NotificationManager::bbl_show_seqprintinfo_notification(): the plate prints
 * by object, so arranging it keeps the nozzle off the printed objects. It
 * stays until its close button.
 */
@Composable
fun SeqPrintInfoNotification(onClose: () -> Unit) {
    OrcaNotification(onClose = onClose) {
        OrcaNotificationText(orcaString("Print By Object: \nSuggest to use auto-arrange to avoid collisions when printing."))
    }
}

/**
 * NotificationManager::push_simplify_suggestion_notification(): the object
 * [name] is slow to process; "Simplify model" opens the Simplify gizmo on it
 * ([onSimplify]) and closes the notification. It stays until then or its
 * close button.
 */
@Composable
fun SimplifySuggestionNotification(name: String, onSimplify: () -> Unit, onClose: () -> Unit) {
    OrcaNotification(onClose = onClose) {
        OrcaNotificationText(
            orcaText(
                OrcaText(
                    "Processing model '%1%' with more than 1M triangles could be slow. It is highly recommended to simplify the model.",
                    listOf(name),
                ),
            ),
        )
        OrcaNotificationLink(orcaString("Simplify model"), onClick = onSimplify)
    }
}

/**
 * NotificationManager::UpdatedItemsInfoNotification of the objects a load
 * brought as parts of a cut object ([count], 0 for none): "%1$d object was
 * loaded as a part of cut object.", for ten seconds after every load that
 * told of them ([loads]), with its close button. One that told before the
 * canvas showed is not told again.
 */
@Composable
fun UpdatedItemsInfoNotification(count: Int, loads: Int) {
    var told by remember { mutableIntStateOf(loads) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(loads) {
        if (loads == told) return@LaunchedEffect
        told = loads
        shown = true
        delay(UPDATED_ITEMS_INFO_MILLIS)
        shown = false
    }
    if (!shown || count <= 0) return
    OrcaNotification(onClose = { shown = false }) {
        OrcaNotificationText(
            orcaText(
                OrcaText(
                    "%1\$d object was loaded as a part of cut object.",
                    listOf(count.toString()),
                    msgidPlural = "%1\$d objects were loaded as parts of cut object.",
                    count = count.toLong(),
                ),
            ),
        )
    }
}

/**
 * NotificationType::PresetUpdateAvailable: "Configuration can update now.",
 * whose "Detail." opens MsgUpdateConfig ([onDetail]) and closes it. It stays
 * until then or its close button.
 */
@Composable
fun ProfileUpdateAvailableNotification(onDetail: () -> Unit, onClose: () -> Unit) {
    OrcaNotification(onClose = onClose) {
        OrcaNotificationText(orcaString("Configuration can update now."))
        OrcaNotificationLink(orcaString("Detail."), onClick = onDetail)
    }
}

/**
 * NotificationType::PresetUpdateFinished of a forced update: the [vendor]'s
 * configuration package and the [version] it was updated to, until its close
 * button.
 */
@Composable
fun ProfileUpdateFinishedNotification(vendor: String, version: String, onClose: () -> Unit) {
    OrcaNotification(onClose = onClose) {
        OrcaNotificationText(orcaString("Configuration package: ") + vendor + orcaString(" updated to ") + version)
    }
}

/** SlicingProgressNotification::get_duration() of the completed and the cancelled state. */
private const val SLICE_ENDED_MILLIS = 3_000L

/** push_updated_item_info_notification()'s 10 seconds. */
private const val UPDATED_ITEMS_INFO_MILLIS = 10_000L

/** ExportFinishedNotification's 20 seconds, for a file that is not on a removable drive. */
private const val EXPORT_FINISHED_MILLIS = 20_000L

/**
 * NotificationManager::push_slicing_warning_notification(),
 * push_slicing_serious_warning_notification() and
 * push_slicing_error_notification() of the sliced plate: "Warning:",
 * "Serious warning:" or "Error:" above the text, and "Jump to [object]" when
 * the notice names an object that is still on the plate ([objectName]).
 * Closed, it stays away until the next slice pushes it again.
 */
@Composable
fun SliceNoticeNotification(notice: SliceNotice, objectName: String?, onJumpTo: () -> Unit) {
    var closed by remember(notice) { mutableStateOf(false) }
    if (closed) return
    val (level, title) = when (notice.level) {
        SliceNoticeLevel.WARNING -> OrcaNotificationLevel.Warning to orcaString("Warning:")
        SliceNoticeLevel.SERIOUS_WARNING -> OrcaNotificationLevel.SeriousWarning to orcaString("Serious warning:")
        SliceNoticeLevel.ERROR -> OrcaNotificationLevel.Error to orcaString("Error:")
    }
    OrcaNotification(level = level, onClose = { closed = true }) {
        OrcaNotificationText(title, emphasized = true)
        OrcaNotificationText(orcaText(notice.text).trimEnd())
        objectName?.let { OrcaNotificationLink(orcaString("Jump to") + " [$it]", onClick = onJumpTo) }
    }
}

/**
 * The process names post-processing scripts (post_process), which the
 * desktop app runs on the G-code it exports and sends
 * (run_post_process_scripts()); the app cannot run them, and says so until
 * its close button, as a slicing warning.
 */
@Composable
fun PostProcessSkippedNotification() {
    var closed by remember { mutableStateOf(false) }
    if (closed) return
    OrcaNotification(level = OrcaNotificationLevel.Warning, onClose = { closed = true }) {
        OrcaNotificationText(orcaString("Warning:"), emphasized = true)
        OrcaNotificationText(stringResource(R.string.post_process_skipped))
    }
}
