package app.orcinus.shadow.core.ui.navigation

/**
 * A page that covers the window but leaves the page under it composed, the way
 * OrcaSlicer opens its dialogs over the plater: the 3D view keeps its OpenGL
 * surface and scene, so coming back draws the plate at once. A feature marks
 * its entry with this; the app shell renders it over the workspace.
 */
val overlayPageMetadata: Map<String, Any> = mapOf(OVERLAY_PAGE to true)

/** Whether an entry's metadata marks it as a page over the one below it. */
fun Map<String, Any>.isOverlayPage(): Boolean = this[OVERLAY_PAGE] == true

private const val OVERLAY_PAGE = "app.orcinus.shadow.navigation.overlayPage"
