package app.orcinus.shadow.core.ui.plate

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.orcinus.shadow.core.designsystem.component.OrcaMenuCheckItem
import app.orcinus.shadow.core.designsystem.component.OrcaSplitButton
import app.orcinus.shadow.core.model.SliceMode
import app.orcinus.shadow.core.ui.orca.orcaString

/**
 * MainFrame's slice button with its drop-down: "Slice plate" or "Slice all",
 * as the drop-down chose ([mode]); choosing changes what the button does and
 * slices nothing yet.
 */
@Composable
fun SliceButton(
    mode: SliceMode,
    enabled: Boolean,
    onSlice: () -> Unit,
    onModeChange: (SliceMode) -> Unit,
    modifier: Modifier = Modifier,
    /** What the button says instead, such as the progress of the slice. */
    text: String? = null,
) {
    OrcaSplitButton(
        text = text ?: orcaString(label(mode)),
        onClick = onSlice,
        menuDescription = orcaString("Slice all") + ", " + orcaString("Slice plate"),
        enabled = enabled,
        modifier = modifier,
        menu = { dismiss ->
            // The drop-down lists "Slice all" first.
            listOf(SliceMode.ALL, SliceMode.PLATE).forEach { choice ->
                OrcaMenuCheckItem(
                    text = orcaString(label(choice)),
                    checked = choice == mode,
                    onClick = {
                        dismiss()
                        onModeChange(choice)
                    },
                )
            }
        },
    )
}

private fun label(mode: SliceMode): String = when (mode) {
    SliceMode.PLATE -> "Slice plate"
    SliceMode.ALL -> "Slice all"
}
