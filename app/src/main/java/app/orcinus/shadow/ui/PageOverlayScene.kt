package app.orcinus.shadow.ui

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import app.orcinus.shadow.core.ui.navigation.isOverlayPage

/**
 * A page that covers the window but leaves the page under it composed, the way
 * OrcaSlicer opens its dialogs over the plater. The 3D view of the workspace
 * keeps its OpenGL surface and its scene, so coming back draws the plate at
 * once instead of building it again.
 */
class PageOverlaySceneStrategy<T : Any> : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val last = entries.lastOrNull() ?: return null
        if (entries.size < 2 || !last.metadata.isOverlayPage()) return null
        return PageOverlayScene(key = last.contentKey, entry = last, below = entries.dropLast(1))
    }
}

private data class PageOverlayScene<T : Any>(
    override val key: Any,
    private val entry: NavEntry<T>,
    private val below: List<NavEntry<T>>,
) : OverlayScene<T> {
    override val entries: List<NavEntry<T>> = listOf(entry)

    override val previousEntries: List<NavEntry<T>> = below

    override val overlaidEntries: List<NavEntry<T>> = below

    override val content: @Composable (() -> Unit) = { entry.Content() }
}
