package app.orcinus.shadow.core.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The writer of the toolpaths the preview shows, which the File menu's
 * "Export toolpaths as OBJ" uses (GCodeViewer::export_toolpaths_to_obj()): it
 * writes an OBJ file at the path it is given, with its materials beside it,
 * and answers whether it could. The preview sets it while it shows toolpaths
 * (MainFrame::can_export_toolpaths()) and clears it when it goes.
 */
val LocalToolpathsExport = staticCompositionLocalOf<MutableState<(suspend (path: String) -> Boolean)?>> { mutableStateOf(null) }
