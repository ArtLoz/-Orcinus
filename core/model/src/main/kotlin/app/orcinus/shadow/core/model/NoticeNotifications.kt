package app.orcinus.shadow.core.model

/** NotificationManager's level of a notice the 3D view shows (NotificationLevel). */
enum class NoticeNotificationLevel {
    /** ErrorNotificationLevel: it stays until it is closed. */
    ERROR,

    /** RegularNotificationLevel and PrintInfoNotificationLevel: it fades after ten seconds. */
    REGULAR,
}

/**
 * The engine's notices OrcaSlicer shows as notifications of the 3D view
 * rather than message boxes, with their level: load_files()'s
 * bbl_show_3mf_warn_notification() of a project's invalid values (an error),
 * _calib_pa_pattern()'s push_notification() of the accelerations or the speed
 * it took (regular), and the painting a repair removed
 * (CustomSupportsAndSeamRemovedAfterRepair, print info). Null for a message
 * box. The ids are the engine's, which a load of several files follows with
 * the file's place.
 */
val SettingsDialog.notificationLevel: NoticeNotificationLevel?
    get() = when {
        question -> null
        id.startsWith("3mf_invalid_values") -> NoticeNotificationLevel.ERROR
        REGULAR_NOTICES.any(id::startsWith) -> NoticeNotificationLevel.REGULAR
        else -> null
    }

private val REGULAR_NOTICES = listOf("pa_pattern_accelerations", "pa_pattern_speeds", "paint_removed")
