package app.orcinus.shadow.core.ui.plate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.orcinus.shadow.core.designsystem.component.OrcaNotification
import app.orcinus.shadow.core.designsystem.component.OrcaNotificationLevel
import app.orcinus.shadow.core.designsystem.component.OrcaNotificationText
import app.orcinus.shadow.core.model.NoticeNotificationLevel
import app.orcinus.shadow.core.model.SettingsDialog
import app.orcinus.shadow.core.model.notificationLevel
import app.orcinus.shadow.core.ui.orca.orcaText
import kotlinx.coroutines.delay

/**
 * A notice of the engine that OrcaSlicer shows on the 3D view rather than as
 * a message box (SettingsDialog.notificationLevel), with its close button: an
 * error stays until it is closed, a regular one fades after its ten seconds
 * (NotificationManager::get_standard_duration()).
 */
@Composable
fun NoticeNotification(notice: SettingsDialog, onClose: () -> Unit) {
    val level = notice.notificationLevel ?: return
    if (level == NoticeNotificationLevel.REGULAR) {
        LaunchedEffect(notice) {
            delay(REGULAR_NOTIFICATION_MILLIS)
            onClose()
        }
    }
    OrcaNotification(
        level = if (level == NoticeNotificationLevel.ERROR) OrcaNotificationLevel.Error else OrcaNotificationLevel.Regular,
        onClose = onClose,
    ) {
        OrcaNotificationText(orcaText(notice.text))
    }
}

private const val REGULAR_NOTIFICATION_MILLIS = 10_000L
