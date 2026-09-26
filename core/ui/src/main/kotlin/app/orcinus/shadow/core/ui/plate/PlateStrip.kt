package app.orcinus.shadow.core.ui.plate

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.component.OrcaCanvasToolbar
import app.orcinus.shadow.core.designsystem.component.OrcaContextMenu
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme

/**
 * The plates of the project under the thumb. OrcaSlicer writes every plate's
 * number beside it (PartPlate::render_only_numbers) and draws its actions as
 * icons over it (render_icons), which a mouse hovers; on a phone the plates
 * are small and the icons smaller, so the numbers stand in a strip. A tap on
 * one selects the plate, as a click on the plate does; holding one opens the
 * plate's [actions], as its icons offer them.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlateStrip(
    count: Int,
    current: Int,
    onSelect: (Int) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    /** The actions of the plate at an index; [dismiss] closes them. */
    actions: (@Composable ColumnScope.(index: Int, dismiss: () -> Unit) -> Unit)? = null,
) {
    val colors = OrcaTheme.colors
    var menu by remember { mutableStateOf<Int?>(null) }
    OrcaCanvasToolbar(modifier) {
        repeat(count) { index ->
            val selected = index == current
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(horizontal = 2.dp, vertical = 4.dp)
                    .size(OrcaTheme.dimensions.minimumTouchTarget - 8.dp)
                    .background(if (selected) colors.accentSelected else Color.Transparent, OrcaTheme.shapes.control)
                    .border(1.dp, if (selected) colors.accent else Color.Transparent, OrcaTheme.shapes.control)
                    .semantics { this.selected = selected }
                    .combinedClickable(
                        enabled = enabled,
                        role = Role.Tab,
                        onClick = { if (!selected) onSelect(index) },
                        onLongClick = actions?.let { { menu = index } },
                    ),
            ) {
                Text(
                    // GLTexture::generate_from_text_string() for PartPlateList::m_idx_textures.
                    text = if (index < 9) "0${index + 1}" else "${index + 1}",
                    color = if (enabled) colors.onCanvasPanel else colors.textDisabledOnBox,
                    style = OrcaTheme.typography.head15,
                )
                if (menu == index && actions != null) {
                    OrcaContextMenu(expanded = true, position = IntOffset.Zero, onDismissRequest = { menu = null }) {
                        actions(index) { menu = null }
                    }
                }
            }
        }
    }
}
