package app.orcinus.shadow.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * OrcaSlicer's colours by role. Light values and their dark counterparts come
 * from OrcaSlicer's GUI: the dark-mode table in Widgets/StateColor.cpp, the
 * widget styles (Button, SwitchButton, ComboBox, TextInput), the main window
 * (MainFrame, BBLTopbar), and the 3D canvas (GLCanvas3D, 3DBed, ImGuiWrapper).
 */
@Immutable
data class OrcaColors(
    // Brand
    /** ORCA colour: primary buttons, selected tab, checked controls. */
    val accent: Color,
    /** Hovered or pressed primary button. */
    val accentPressed: Color,
    /** Background of a focused combo box or input, the accent at 10 %. */
    val accentSubtle: Color,
    /** Background of a checked list item, the accent at 25 %. */
    val accentSelected: Color,
    val onAccent: Color,
    val secondary: Color,
    val info: Color,
    val error: Color,
    /** Alert button when pressed. */
    val alert: Color,

    // Window chrome
    val titleBar: Color,
    val tabBar: Color,
    val onTabBar: Color,
    val onTabBarInactive: Color,

    // Surfaces
    val window: Color,
    /** Strip behind the sidebar panels. */
    val sidebarBackground: Color,
    val sidebarTitleTop: Color,
    val sidebarTitleBottom: Color,
    /** Side tab bar and similar low panels. */
    val sideTabBar: Color,
    val buttonBackground: Color,
    val buttonBackgroundPressed: Color,
    val controlDisabledBackground: Color,
    val border: Color,
    val separator: Color,
    val switchTrack: Color,
    val switchThumb: Color,

    // Text
    /** Button and input text. */
    val text: Color,
    /** Parameter labels and sidebar titles. */
    val textLabel: Color,
    val textSoft: Color,
    val textDimmed: Color,
    /** Units and hints next to inputs. */
    val textSide: Color,
    val textDisabled: Color,
    val textDisabledOnBox: Color,

    // 3D canvas
    val canvas: Color,
    val plate: Color,
    val plateGrid: Color,
    /** ImGui windows over the canvas: toolbars and notifications. */
    val canvasPanel: Color,
    val canvasPanelSeparator: Color,
    val onCanvasPanel: Color,
    /** G-code legend and other dark translucent canvas panels. */
    val legend: Color,
    val onLegend: Color,
    val isDark: Boolean,
)

/** Converts an Orca float RGBA component quadruple into a Compose colour. */
private fun rgba(red: Float, green: Float, blue: Float, alpha: Float = 1f) = Color(red, green, blue, alpha)

val OrcaLightColors = OrcaColors(
    accent = Color(0xFF009688),
    accentPressed = Color(0xFF26A69A),
    accentSubtle = Color(0xFFE5F0EE),
    accentSelected = Color(0xFFBFE1DE),
    onAccent = Color(0xFFFEFEFE),
    secondary = Color(0xFFFF6F00),
    info = Color(0xFF1F8EEA),
    error = Color(0xFFD01B1B),
    alert = Color(0xFFE14747),

    titleBar = Color(0xFF262E30),
    tabBar = Color(0xFF3B4446),
    onTabBar = Color(0xFFFEFEFE),
    onTabBarInactive = Color(0xFFFEFEFE),

    window = Color(0xFFFFFFFF),
    sidebarBackground = Color(0xFFCECECE),
    sidebarTitleTop = Color(0xFFF8F8F8),
    sidebarTitleBottom = Color(0xFFF1F1F1),
    sideTabBar = Color(0xFFFEFFFF),
    buttonBackground = Color(0xFFDFDFDF),
    buttonBackgroundPressed = Color(0xFFD4D4D4),
    controlDisabledBackground = Color(0xFFF0F0F1),
    border = Color(0xFFDBDBDB),
    separator = Color(0xFFEEEEEE),
    switchTrack = Color(0xFFD9D9D9),
    switchThumb = Color(0xFFFFFEFE),

    text = Color(0xFF262E30),
    textLabel = Color(0xFF363636),
    textSoft = Color(0xFF323A3D),
    textDimmed = Color(0xFF6B6A6A),
    textSide = Color(0xFF6B6B6A),
    textDisabled = Color(0xFF6B6B6B),
    textDisabledOnBox = Color(0xFFACACAC),

    canvas = rgba(0.906f, 0.906f, 0.906f),
    plate = rgba(0.3255f, 0.337f, 0.337f),
    plateGrid = rgba(0.9f, 0.9f, 0.9f, 0.6f),
    canvasPanel = rgba(1f, 1f, 1f),
    canvasPanelSeparator = rgba(0.93f, 0.93f, 0.93f),
    onCanvasPanel = Color(0xFF262E30),
    legend = rgba(0.1f, 0.1f, 0.1f, 0.8f),
    onLegend = Color(0xFFFFFFFF),
    isDark = false,
)

val OrcaDarkColors = OrcaColors(
    accent = Color(0xFF00675B),
    accentPressed = Color(0xFF008172),
    accentSubtle = Color(0xFF283232),
    accentSelected = Color(0xFF223C3C),
    onAccent = Color(0xFFFEFEFE),
    secondary = Color(0xFFD15B00),
    info = Color(0xFF2778D2),
    error = Color(0xFFBB2A3A),
    alert = Color(0xFFE14747),

    titleBar = Color(0xFF262E30),
    tabBar = Color(0xFF2D2D30),
    onTabBar = Color(0xFFFEFEFE),
    onTabBarInactive = Color(0xFFFEFEFE),

    window = Color(0xFF2D2D31),
    sidebarBackground = Color(0xFF54545B),
    sidebarTitleTop = Color(0xFF36363C),
    sidebarTitleBottom = Color(0xFF36363B),
    sideTabBar = Color(0xFF242428),
    buttonBackground = Color(0xFF3E3E45),
    buttonBackgroundPressed = Color(0xFF4D4D54),
    controlDisabledBackground = Color(0xFF333337),
    border = Color(0xFF4A4A51),
    separator = Color(0xFF4C4C55),
    switchTrack = Color(0xFF27272A),
    switchThumb = Color(0xFFD9D9D9),

    text = Color(0xFFEFEFF0),
    textLabel = Color(0xFFB2B3B5),
    textSoft = Color(0xFFE5E5E4),
    textDimmed = Color(0xFF909090),
    textSide = Color(0xFFB3B3B5),
    textDisabled = Color(0xFF818183),
    textDisabledOnBox = Color(0xFF65656A),

    canvas = rgba(0.329f, 0.329f, 0.353f),
    plate = rgba(0.255f, 0.255f, 0.283f),
    plateGrid = rgba(0.9f, 0.9f, 0.9f, 0.6f),
    canvasPanel = Color(0xFF2D2D31),
    canvasPanelSeparator = rgba(0.24f, 0.24f, 0.27f),
    onCanvasPanel = Color(0xFFEFEFF0),
    legend = rgba(0.1f, 0.1f, 0.1f, 0.8f),
    onLegend = Color(0xFFFFFFFF),
    isDark = true,
)
