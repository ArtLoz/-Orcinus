package app.orcinus.shadow.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Corner radii of OrcaSlicer's widgets (Widgets/Button.cpp, ImGuiWrapper). */
@Immutable
data class OrcaShapes(
    /** Choice, parameter, icon, and expanded buttons; inputs and combo boxes. */
    val control: RoundedCornerShape = RoundedCornerShape(4.dp),
    /** Compact buttons. */
    val compact: RoundedCornerShape = RoundedCornerShape(8.dp),
    /** Window buttons: the rounded dialog and action buttons. */
    val window: RoundedCornerShape = RoundedCornerShape(12.dp),
    /** ImGui windows over the 3D canvas. */
    val canvasPanel: RoundedCornerShape = RoundedCornerShape(3.dp),
)

/** Spacing and sizes, in Orca's device-independent pixels. */
@Immutable
data class OrcaDimensions(
    val spacingTiny: Dp = 4.dp,
    val spacingSmall: Dp = 8.dp,
    val spacingMedium: Dp = 12.dp,
    val spacingLarge: Dp = 16.dp,
    val tabBarHeight: Dp = 48.dp,
    val sidebarWidth: Dp = 320.dp,
    val sidebarTitleHeight: Dp = 40.dp,
    val controlHeight: Dp = 32.dp,
    val parameterControlHeight: Dp = 28.dp,
    val iconSmall: Dp = 16.dp,
    val icon: Dp = 20.dp,
    val iconLarge: Dp = 24.dp,
    val checkBox: Dp = 18.dp,
    /** Touch target of every interactive control. */
    val minimumTouchTarget: Dp = 48.dp,
)
