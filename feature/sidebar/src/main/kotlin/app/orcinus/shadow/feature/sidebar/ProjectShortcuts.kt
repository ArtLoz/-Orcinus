package app.orcinus.shadow.feature.sidebar

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.ShortcutAction
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.plate.NameErrorDialog
import app.orcinus.shadow.core.ui.shortcuts.ShortcutHandler

/**
 * MainFrame's wxEVT_CHAR_HOOK for the File menu: Ctrl+N, Ctrl+O, Ctrl+S,
 * Ctrl+Shift+S, Ctrl+I and Ctrl+G do what the sidebar's File menu and its
 * plate menu's Add Models do, each when its item is enabled (can_save(),
 * can_save_as(), can_open_project(), can_add_models(), can_export_gcode()).
 * It stands beside the sidebar, which a collapsed panel no longer shows, and
 * shares its view model.
 */
@Composable
fun ProjectShortcuts(createViewModel: () -> SidebarViewModel) {
    val viewModel = viewModel { createViewModel() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val untitled = orcaString("Untitled")
    val openPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.openProject(ExternalDocumentReference(uri.toString()))
    }
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.addModels(uris.map { ExternalDocumentReference(it.toString()) })
    }
    val projectPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PROJECT_MIME_TYPE)) { uri ->
        if (uri != null) viewModel.saveProject(ExternalDocumentReference(uri.toString()))
    }
    val slicedPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PROJECT_MIME_TYPE)) { uri ->
        if (uri != null) viewModel.exportSliced(ExternalDocumentReference(uri.toString()), all = false)
    }
    var nameError by remember { mutableStateOf<String?>(null) }
    nameError?.let { message -> NameErrorDialog(message, onDismiss = { nameError = null }) }
    ShortcutHandler { action ->
        when (action) {
            ShortcutAction.NewProject -> if (state.canStartProject) viewModel.newProject()
            ShortcutAction.OpenProject -> if (state.canStartProject) openPicker.launch(arrayOf("*/*"))
            ShortcutAction.SaveProject -> if (state.canSaveProject && state.projectDirty) {
                if (viewModel.projectNeedsDocument) projectPicker.launch(viewModel.projectFileName(untitled)) else viewModel.saveProject()
            }
            ShortcutAction.SaveProjectAs -> if (state.canSaveProject) projectPicker.launch(viewModel.projectFileName(untitled))
            ShortcutAction.ImportModels -> if (state.canStartProject && state.canSaveProject) modelPicker.launch(arrayOf("*/*"))
            ShortcutAction.ExportSlicedFile -> if (state.canExportSliced) {
                viewModel.slicedNameError()?.let { nameError = it }
                    ?: slicedPicker.launch(viewModel.slicedName() ?: ((state.projectName ?: untitled) + ".gcode.3mf"))
            }
            else -> return@ShortcutHandler false
        }
        true
    }
}

/** The type of a 3MF project, and of a sliced file. */
private const val PROJECT_MIME_TYPE = "model/3mf"
