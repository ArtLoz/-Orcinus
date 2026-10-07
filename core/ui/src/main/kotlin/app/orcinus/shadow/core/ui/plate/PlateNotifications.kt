package app.orcinus.shadow.core.ui.plate

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import app.orcinus.shadow.core.designsystem.component.OrcaNotification
import app.orcinus.shadow.core.designsystem.component.OrcaNotificationLevel
import app.orcinus.shadow.core.designsystem.component.OrcaNotificationLink
import app.orcinus.shadow.core.designsystem.component.OrcaNotificationText
import app.orcinus.shadow.core.model.PlateNotice
import app.orcinus.shadow.core.model.PlateNoticeKind
import app.orcinus.shadow.core.model.PlateProblem
import app.orcinus.shadow.core.model.PlateProblemKind
import app.orcinus.shadow.core.model.error
import app.orcinus.shadow.core.model.warning
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.title

// The notifications of the plate that the 3D editor and the preview both show
// (NotificationManager keeps them while the preview shows, set_in_preview()).

/**
 * Plater::priv::process_validation_warning() and push_validate_error_notification():
 * "WARNING:" or "Error:" ([title]) over the message, and "Jump to" with the
 * name of the object it is about ([objectName]) and its setting ([option]).
 */
@Composable
fun ValidationNotification(
    level: OrcaNotificationLevel,
    title: String,
    text: String,
    objectName: String?,
    option: String,
    onJumpTo: () -> Unit,
) {
    val link = if (objectName != null || option.isNotEmpty()) {
        orcaString("Jump to") + (objectName?.let { " [$it]" }.orEmpty()) + option.takeIf { it.isNotEmpty() }?.let { " ($it)" }.orEmpty()
    } else {
        null
    }
    OrcaNotification(level = level) {
        OrcaNotificationText(title, emphasized = true)
        OrcaNotificationText(text.trimEnd())
        link?.let { OrcaNotificationLink(it, onClick = onJumpTo) }
    }
}

/**
 * GLCanvas3D::EWarning::ObjectClashed, push_plater_error_notification() of
 * construct_error_string(): the objects laid over the plate's boundary or
 * above its height, by name.
 */
@Composable
fun ObjectClashedNotification(names: List<String>) {
    OrcaNotification(level = OrcaNotificationLevel.Error) {
        OrcaNotificationText(orcaString("Error:"), emphasized = true)
        OrcaNotificationText(
            (
                orcaString("Following objects are laid over the boundary of plate or exceeds the height limit:\n") +
                    names.joinToString("") { it + "\n" } +
                    orcaString("Please solve the problem by moving it totally on or off the plate, and confirming that the height is within the build volume.\n")
                ).trimEnd(),
        )
    }
}

/** NotificationManager::push_plater_warning_notification(): "Warning:" above [text], and the notification's own link. */
@Composable
fun PlaterWarningNotification(text: String, link: @Composable () -> Unit = {}) {
    OrcaNotification(level = OrcaNotificationLevel.Warning) {
        OrcaNotificationText(orcaString("Warning:"), emphasized = true)
        OrcaNotificationText(text.trimEnd())
        link()
    }
}

/**
 * GLCanvas3D::reload_scene()'s warnings of the plate's filaments, "Warning:"
 * above the text, with "Click Wiki for help." of BBLMixUsePLAAndPETG: the
 * dual-nozzle PLA and PETG guide, in Chinese for a Chinese language.
 */
@Composable
fun PlateNoticeNotification(notice: PlateNotice) {
    val uriHandler = LocalUriHandler.current
    val wikiRegion = if (LocalConfiguration.current.locales[0].language == "zh") "zh" else "en"
    PlaterWarningNotification(notice.text) {
        if (notice.kind == PlateNoticeKind.MIX_PLA_PETG) {
            OrcaNotificationLink(orcaString("Click Wiki for help."), onClick = {
                uriHandler.openUri("https://wiki.bambulab.com/$wikiRegion/filament-acc/filament/pla-and-petg-dual-extrusion")
            })
        }
    }
}

/**
 * The plate's [problem] with its close button: a slicing error as
 * push_slicing_error_notification() shows it, "Error:" above its text and
 * "Jump to" the objects it names that are still on the plate ([objectNames]);
 * the plater error and warning of the meshes' boolean operations under
 * "Error:" and "Warning:"; the plates' information of arranging and
 * orienting, and the app's own problems, with their text.
 */
@Composable
fun PlateProblemNotification(problem: PlateProblem, objectNames: List<String>, onJumpTo: () -> Unit, onClose: () -> Unit) {
    val kind = problem.kind
    val header = when {
        kind.error -> orcaString("Error:")
        kind == PlateProblemKind.MESH_BOOLEAN_FAILED -> orcaString("Warning:")
        else -> null
    }
    OrcaNotification(
        level = when {
            kind.error -> OrcaNotificationLevel.Error
            kind.warning -> OrcaNotificationLevel.Warning
            else -> OrcaNotificationLevel.Regular
        },
        action = { CloseButton(onClose) },
    ) {
        if (header != null) {
            OrcaNotificationText(header, emphasized = true)
            OrcaNotificationText(problem.title().trimEnd())
        } else {
            OrcaNotificationText(problem.title(), emphasized = true)
            problem.detail?.takeIf { it.isNotBlank() }?.let { OrcaNotificationText(it) }
        }
        if (objectNames.isNotEmpty()) {
            OrcaNotificationLink(orcaString("Jump to") + " [" + objectNames.joinToString(", ") + "]", onClick = onJumpTo)
        }
    }
}
