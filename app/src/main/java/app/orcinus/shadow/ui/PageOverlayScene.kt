package app.orcinus.shadow.ui

import androidx.activity.compose.BackHandler
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
        return PageOverlayScene(key = last.contentKey, entry = last, below = entries.dropLast(1), onBack = onBack)
    }
}

/**
 * The page over the entries below it. NavDisplay handles Back for the scene
 * under an overlay, which has nothing before it, so the page closes itself on
 * Back, as the dialog of Navigation's DialogScene does; otherwise Back would
 * reach the workspace's tabs or leave the app.
 */
private class PageOverlayScene<T : Any>(
    override val key: Any,
    private val entry: NavEntry<T>,
    private val below: List<NavEntry<T>>,
    private val onBack: () -> Unit,
) : OverlayScene<T> {
    override val entries: List<NavEntry<T>> = listOf(entry)

    override val previousEntries: List<NavEntry<T>> = below

    override val overlaidEntries: List<NavEntry<T>> = below

    override val content: @Composable (() -> Unit) = {
        BackHandler(onBack = onBack)
        entry.Content()
    }

    // As DialogScene: the scene is its entries; a new onBack of the same stack changes nothing.
    override fun equals(other: Any?): Boolean =
        this === other || (other is PageOverlayScene<*> && key == other.key && entry == other.entry && below == other.below)

    override fun hashCode(): Int = (key.hashCode() * 31 + entry.hashCode()) * 31 + below.hashCode()
}
