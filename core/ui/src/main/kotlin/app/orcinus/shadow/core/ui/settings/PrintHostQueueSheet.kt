package app.orcinus.shadow.core.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.R as DesignR
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonSize
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaIconButton
import app.orcinus.shadow.core.designsystem.component.OrcaSheetHandle
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.PrintHostJob
import app.orcinus.shadow.core.model.PrintHostJobState
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.orca.orcaText
import java.util.Locale

/**
 * The way to the upload queue where the G-code is sent from: Orca's
 * PrintHostQueueDialog opens with Ctrl+J. While an upload goes out, a ring
 * around the button shows how far it got.
 */
@Composable
fun PrintHostQueueButton(jobs: List<PrintHostJob>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OrcaTheme.colors
    val uploading = jobs.firstOrNull { it.state == PrintHostJobState.UPLOADING }
    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            uploading != null -> CircularProgressIndicator(
                progress = { uploading.progress / 100f },
                color = colors.accent,
                trackColor = colors.separator,
                strokeWidth = 2.dp,
                modifier = Modifier.size(34.dp),
            )
            jobs.any { it.state == PrintHostJobState.QUEUED } -> CircularProgressIndicator(
                color = colors.accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(34.dp),
            )
        }
        OrcaIconButton(
            icon = DesignR.drawable.orca_printer,
            contentDescription = orcaString("Print host upload queue"),
            onClick = onClick,
            tint = if (jobs.any { it.state == PrintHostJobState.ERROR }) colors.error else colors.textSide,
        )
    }
}

/**
 * PrintHostQueueDialog as a sheet: every upload since the app started, its
 * file, host and size, its status and progress; one that waits or goes out
 * can be cancelled, and one that failed shows its error message.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintHostQueueSheet(jobs: List<PrintHostJob>, onCancel: (Int) -> Unit, onDismiss: () -> Unit) {
    val colors = OrcaTheme.colors
    var shownError by remember { mutableStateOf<List<OrcaText>?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.window,
        dragHandle = { OrcaSheetHandle() },
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                text = orcaString("Print host upload queue"),
                color = colors.text,
                style = OrcaTheme.typography.head16,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LazyColumn(Modifier.padding(bottom = 12.dp)) {
                // The newest first, where the user just sent it.
                items(jobs.asReversed(), key = PrintHostJob::id) { job ->
                    JobRow(job, onCancel = { onCancel(job.id) }, onShowError = { shownError = job.error })
                }
            }
        }
    }
    shownError?.let { error ->
        PrintHostErrorDialog(error, onDismiss = { shownError = null })
    }
}

/** A row of the queue: the ID, Filename, Host, Size, Status and Progress columns. */
@Composable
private fun JobRow(job: PrintHostJob, onCancel: () -> Unit, onShowError: () -> Unit) {
    val colors = OrcaTheme.colors
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${job.id}. ${job.uploadPath}",
                    color = colors.text,
                    style = OrcaTheme.typography.body14,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = job.host + " · " + sizeText(job.size) + " · " + statusText(job),
                    color = if (job.state == PrintHostJobState.ERROR) colors.error else colors.textSide,
                    style = OrcaTheme.typography.body12,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                job.state.cancellable -> OrcaIconButton(
                    icon = DesignR.drawable.orca_notification_close,
                    contentDescription = orcaString("Cancel upload"),
                    onClick = onCancel,
                )
                job.state == PrintHostJobState.ERROR -> OrcaButton(
                    text = orcaString("Show error message"),
                    onClick = onShowError,
                    style = OrcaButtonStyle.Regular,
                    size = OrcaButtonSize.Compact,
                )
            }
        }
        if (job.state == PrintHostJobState.UPLOADING) {
            LinearProgressIndicator(
                progress = { job.progress / 100f },
                color = colors.accent,
                trackColor = colors.separator,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
        }
    }
}

/** set_state(): the Status column, with the Progress column's percentage while the file goes out. */
@Composable
private fun statusText(job: PrintHostJob): String = when (job.state) {
    PrintHostJobState.QUEUED -> orcaString("Queued")
    PrintHostJobState.UPLOADING -> orcaString("Uploading") + " ${job.progress}%"
    PrintHostJobState.ERROR -> orcaString("Error")
    PrintHostJobState.CANCELLED -> orcaString("Canceled")
    PrintHostJobState.COMPLETED -> orcaString("Completed")
}

/** append_job(): the file's size as "%.2fMB", "unknown" when it could not be read. */
private fun sizeText(size: Long): String = if (size < 0) "unknown" else String.format(Locale.ROOT, "%.2fMB", size / 1024.0 / 1024.0)

/** show_error() of an upload: on_error()'s "Error uploading to print host:" and the host's message. */
@Composable
fun PrintHostErrorDialog(error: List<OrcaText>, onDismiss: () -> Unit) {
    val colors = OrcaTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { OrcaButton(orcaString("OK"), onClick = onDismiss) },
        title = { Text(orcaString("Error"), style = OrcaTheme.typography.head16) },
        text = { Text(orcaText(error), color = colors.text, style = OrcaTheme.typography.body14) },
        containerColor = colors.window,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
    )
}

/**
 * What the queue's ends do on the screen, once each: an upload that failed
 * shows its error (PrintHostQueueDialog::on_error()), one that went through
 * runs [onCompleted] — the Device tab, a page the host opens — as
 * PrintHostJobQueue::priv::perform_job() does, and a cancelled one is only
 * noted.
 */
@Composable
fun PrintHostJobsReport(jobs: List<PrintHostJob>, onAcknowledge: (Int) -> Unit, onCompleted: (PrintHostJob) -> Unit) {
    val ended = jobs.firstOrNull { !it.reported && !it.state.cancellable }
    val completed by rememberUpdatedState(onCompleted)
    val acknowledge by rememberUpdatedState(onAcknowledge)
    LaunchedEffect(ended?.id, ended?.state) {
        val job = ended ?: return@LaunchedEffect
        when (job.state) {
            PrintHostJobState.COMPLETED -> {
                acknowledge(job.id)
                completed(job)
            }
            PrintHostJobState.CANCELLED -> acknowledge(job.id)
            else -> Unit
        }
    }
    if (ended?.state == PrintHostJobState.ERROR) {
        PrintHostErrorDialog(ended.error, onDismiss = { onAcknowledge(ended.id) })
    }
}
