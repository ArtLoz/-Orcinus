package app.orcinus.shadow.core.ui.plate

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.R as DesignR
import app.orcinus.shadow.core.designsystem.component.OrcaIconButton
import app.orcinus.shadow.core.designsystem.component.OrcaNotification
import app.orcinus.shadow.core.designsystem.component.OrcaNotificationText
import app.orcinus.shadow.core.designsystem.component.OrcaProgressNotification
import app.orcinus.shadow.core.ui.orca.OrcaHintsFile
import app.orcinus.shadow.core.ui.orca.orcaString
import kotlinx.coroutines.delay

/**
 * SlicingProgressNotification's completed state: the complete icon and
 * "Slice ok." in bold, for three seconds (get_duration()) after every slice
 * that went through ([completions] counts them), with its close button. A
 * slice that finished before the canvas showed is not announced again.
 */
@Composable
fun SliceCompletedNotification(completions: Int, tips: (@Composable () -> Unit)? = null) {
    var announced by remember { mutableIntStateOf(completions) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(completions) {
        if (completions == announced) return@LaunchedEffect
        announced = completions
        shown = true
        delay(SLICE_COMPLETED_MILLIS)
        shown = false
    }
    if (!shown) return
    OrcaNotification(action = { CloseButton { shown = false } }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(DesignR.drawable.orca_notification_slicing_complete), contentDescription = null, modifier = Modifier.size(24.dp))
            Row(Modifier.padding(start = 6.dp)) { OrcaNotificationText(orcaString("Slice ok."), emphasized = true) }
        }
        // The daily tips stay under the completed state.
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
    OrcaNotification(action = { CloseButton(onClose) }) {
        OrcaNotificationText(orcaString("Exported successfully"))
        OrcaNotificationText(name)
    }
}

/** PopNotification::render_close_button() */
@Composable
private fun RowScope.CloseButton(onClick: () -> Unit) {
    OrcaIconButton(icon = DesignR.drawable.orca_notification_close, contentDescription = orcaString("Close"), onClick = onClick)
}

private const val SLICE_COMPLETED_MILLIS = 3_000L

/** ExportFinishedNotification's 20 seconds, for a file that is not on a removable drive. */
private const val EXPORT_FINISHED_MILLIS = 20_000L
