package app.orcinus.shadow.feature.project

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.orcinus.shadow.core.designsystem.R as DesignR
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaComboField
import app.orcinus.shadow.core.designsystem.component.OrcaIconButton
import app.orcinus.shadow.core.designsystem.component.OrcaMenuItem
import app.orcinus.shadow.core.designsystem.layout.OrcaSidebarToggleSpace
import app.orcinus.shadow.core.designsystem.component.OrcaTextField
import app.orcinus.shadow.core.designsystem.component.OrcaUnderlineTabs
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.AuxiliaryFile
import app.orcinus.shadow.core.model.AuxiliaryFolder
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.ProjectInfo
import app.orcinus.shadow.core.ui.orca.OrcaWebText
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.orca.rememberOrcaWebText
import app.orcinus.shadow.core.ui.orca.orcaText
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The Project tab with its view model; [shown] tells when the tab is the one shown. */
@Composable
fun ProjectRoute(viewModel: ProjectViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.shown() }
    LaunchedEffect(viewModel) {
        viewModel.documents.collect { document ->
            val intent = Intent(Intent.ACTION_VIEW)
                .setData(Uri.parse(document.value))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try {
                context.startActivity(Intent.createChooser(intent, null))
            } catch (_: ActivityNotFoundException) {
            }
        }
    }
    if (state.editing) {
        BackHandler(onBack = viewModel::done)
        ProjectEditor(state, viewModel)
    } else {
        ProjectPage(state.info, onEdit = viewModel::edit, onOpen = viewModel::open)
    }
}

/**
 * The project page (resources/web/model/index.html): "No model information"
 * with "Edit Project Info", or the model's information, its accessories and
 * the profile's information, which the chips above scroll to and follow, as
 * the page's left menu does.
 */
@Composable
private fun ProjectPage(info: ProjectInfo, onEdit: () -> Unit, onOpen: (AuxiliaryFile) -> Unit) {
    val text = rememberOrcaWebText()
    val colors = OrcaTheme.colors
    if (!info.hasContent) {
        // EmptyArea
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.window)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Image(painterResource(R.drawable.project_null), contentDescription = null)
            Text(text("orca2", "No model information"), color = colors.text, style = OrcaTheme.typography.head16)
            OrcaButton(text("orca1", "Edit Project Info"), onClick = onEdit)
        }
        return
    }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val sections = remember { mutableStateMapOf<Int, Int>() }
    // OnMenuSelected(): the section whose edge is nearest the top of the page.
    val selected = sections.entries.minByOrNull { (_, top) -> kotlin.math.abs(top - scroll.value) }?.key ?: 0
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.window),
    ) {
        Row(
            modifier = Modifier
                .height(OrcaSidebarToggleSpace)
                .padding(start = OrcaSidebarToggleSpace),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val menu = listOf(text("t95", "Model Information"), text("t96", "Accessories"), text("t97", "Profile Information"))
            OrcaUnderlineTabs(
                titles = menu,
                selectedIndex = selected,
                onSelect = { index -> sections[index]?.let { top -> scope.launch { scroll.animateScrollTo(top) } } },
                modifier = Modifier.weight(1f),
            )
            OrcaIconButton(DesignR.drawable.orca_edit, contentDescription = text("orca1", "Edit Project Info"), onClick = onEdit)
            Spacer(Modifier.width(8.dp))
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Section({ sections[0] = it }) { ModelBasic(info, text) }
            Section({ sections[1] = it }) { ModelAccessories(info, text, onOpen) }
            Section({ sections[2] = it }) { ModelProfile(info, text) }
        }
    }
}

/** An InfoBlock of the page, which tells where it starts for the menu. */
@Composable
private fun Section(onTop: (Int) -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { onTop(it.positionInParent().y.toInt()) },
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** Model_Basic: name, author (by the upload type), licence badge, pictures and description. */
@Composable
private fun ColumnScope.ModelBasic(info: ProjectInfo, text: OrcaWebText) {
    val colors = OrcaTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text("t98", "Model name") + ": " + info.modelName, color = colors.text, style = OrcaTheme.typography.head16)
            // ShowModelInfo(): the author's title follows the upload type.
            val authorType = when (info.origin.lowercase()) {
                "remix" -> text("t93", "Remixed by")
                "shared" -> text("t94", "Shared by")
                else -> text("t92", "Model Author")
            }
            Text(authorType + ": " + info.author, color = colors.textSide, style = OrcaTheme.typography.body14)
        }
        licenceBadge(info.license)?.let { Image(painterResource(it), contentDescription = info.license, modifier = Modifier.height(36.dp)) }
    }
    Pictures(info.filesIn(AuxiliaryFolder.MODEL_PICTURES))
    Text(text("t100", "Model description"), color = colors.text, style = OrcaTheme.typography.head14)
    Text(htmlText(info.description), color = colors.text, style = OrcaTheme.typography.body14)
}

/** Model_Accessories: the bill of materials, the assembly guide and the others, each hidden while empty. */
@Composable
private fun ColumnScope.ModelAccessories(info: ProjectInfo, text: OrcaWebText, onOpen: (AuxiliaryFile) -> Unit) {
    val colors = OrcaTheme.colors
    val groups = listOf(
        text("t101", "BOM") to info.filesIn(AuxiliaryFolder.BILL_OF_MATERIALS),
        text("t102", "Assembly Guide") to info.filesIn(AuxiliaryFolder.ASSEMBLY_GUIDE),
        text("t103", "Other") to info.filesIn(AuxiliaryFolder.OTHERS),
    )
    Text(text("t96", "Accessories") + " (" + groups.sumOf { it.second.size } + ")", color = colors.text, style = OrcaTheme.typography.head14)
    groups.filter { it.second.isNotEmpty() }.forEach { (title, files) ->
        Text(title + " (" + files.size + ")", color = colors.textSide, style = OrcaTheme.typography.body14, modifier = Modifier.padding(top = 8.dp))
        files.forEach { file -> AccessoryRow(file, onOpen) }
    }
}

/** Model_Profile: the profile's name, author, pictures and description. */
@Composable
private fun ColumnScope.ModelProfile(info: ProjectInfo, text: OrcaWebText) {
    val colors = OrcaTheme.colors
    Text(text("t104", "Profile name") + ": " + info.profileName, color = colors.text, style = OrcaTheme.typography.head16)
    Text(text("t105", "Profile Author") + ": " + info.profileAuthor, color = colors.textSide, style = OrcaTheme.typography.body14)
    Pictures(info.filesIn(AuxiliaryFolder.PROFILE_PICTURES))
    Text(text("t106", "Profile description"), color = colors.text, style = OrcaTheme.typography.head14)
    Text(htmlText(info.profileDescription), color = colors.text, style = OrcaTheme.typography.body14)
}

/** ConstructFileHtml(): a picture's own image, or the icon of its type, its name, and the button that opens it. */
@Composable
private fun AccessoryRow(file: AuxiliaryFile, onOpen: (AuxiliaryFile) -> Unit) {
    val colors = OrcaTheme.colors
    val tail = file.name.substringAfterLast('.', "").lowercase()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(OrcaTheme.shapes.control)
            .clickable { onOpen(file) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (tail in IMAGE_TAILS) {
                FileImage(file, Modifier.size(48.dp), ContentScale.Crop)
            } else {
                val icon = when (tail) {
                    in EXCEL_TAILS -> R.drawable.project_excel
                    in PDF_TAILS -> R.drawable.project_pdf
                    else -> R.drawable.project_default
                }
                Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(40.dp))
            }
        }
        Text(file.name, color = colors.text, style = OrcaTheme.typography.body14, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Model_Preview_Image: the swiper of the pictures, which turns by itself every
 * three seconds while it has more than one, with the page's dots; a picture
 * touched opens full size, as the page's viewer does.
 */
@Composable
private fun Pictures(files: List<AuxiliaryFile>) {
    if (files.isEmpty()) return
    val pager = rememberPagerState { files.size }
    var viewed by remember { mutableStateOf<AuxiliaryFile?>(null) }
    if (files.size > 1) {
        LaunchedEffect(files) {
            while (true) {
                delay(3_000)
                pager.animateScrollToPage((pager.currentPage + 1) % files.size)
            }
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pager,
            pageSpacing = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f),
        ) { page ->
            FileImage(files[page], Modifier.fillMaxSize().clickable { viewed = files[page] }, ContentScale.Fit)
        }
        if (files.size > 1) {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                files.indices.forEach { index ->
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (index == pager.currentPage) OrcaTheme.colors.accent else OrcaTheme.colors.border),
                    )
                }
            }
        }
    }
    viewed?.let { file ->
        Dialog(onDismissRequest = { viewed = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable { viewed = null },
                contentAlignment = Alignment.Center,
            ) {
                FileImage(file, Modifier.fillMaxWidth(), ContentScale.Fit)
            }
        }
    }
}

/**
 * AuxiliaryPanel: "Return", and the tabs "Basic Info" (DesignerPanel),
 * "Pictures", "Bill of Materials", "Assembly Guide" and "Others"
 * (AuFolderPanel), each folder's files with their own commands.
 */
@Composable
private fun ProjectEditor(state: ProjectUiState, viewModel: ProjectViewModel) {
    val colors = OrcaTheme.colors
    val folders = listOf(AuxiliaryFolder.MODEL_PICTURES, AuxiliaryFolder.BILL_OF_MATERIALS, AuxiliaryFolder.ASSEMBLY_GUIDE, AuxiliaryFolder.OTHERS)
    val titles = listOf(orcaString("Basic Info"), orcaString("Pictures"), orcaString("Bill of Materials"), orcaString("Assembly Guide"), orcaString("Others"))
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.window),
    ) {
        Row(
            modifier = Modifier
                .height(OrcaSidebarToggleSpace)
                .padding(start = OrcaSidebarToggleSpace),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrcaButton(orcaString("Return"), onClick = viewModel::done, icon = DesignR.drawable.orca_assemble_return)
        }
        OrcaUnderlineTabs(titles = titles, selectedIndex = state.editorTab, onSelect = viewModel::selectTab)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.editorTab == 0) {
                DesignerPage(state.info, viewModel)
            } else {
                FolderPage(folders[state.editorTab - 1], state.info, viewModel)
            }
        }
    }
    state.renaming?.let { file -> RenameDialog(file, state.renameError, onRename = viewModel::rename, onCancel = viewModel::cancelRename) }
}

/** DesignerPanel: Author, Model Name, License and Description, each written as it changes. */
@Composable
private fun DesignerPage(info: ProjectInfo, viewModel: ProjectViewModel) {
    val colors = OrcaTheme.colors
    // update_info(): the fields read the model's information when the panel opens.
    var designer by rememberSaveable { mutableStateOf(info.designer) }
    var modelName by rememberSaveable { mutableStateOf(if (info.modelInfo) info.modelName else "") }
    var description by rememberSaveable { mutableStateOf(if (info.modelInfo) info.description else "") }
    val license = info.license.takeIf { info.modelInfo && it in ProjectInfo.LICENSES } ?: ""
    Text(orcaString("Author"), color = colors.text, style = OrcaTheme.typography.body14)
    OrcaTextField(value = designer, onValueChange = { designer = it; viewModel.setDesigner(it) })
    Text(orcaString("Model Name"), color = colors.text, style = OrcaTheme.typography.body14)
    OrcaTextField(value = modelName, onValueChange = { modelName = it; viewModel.setModelName(it) })
    Text(orcaString("License"), color = colors.text, style = OrcaTheme.typography.body14)
    Box {
        var expanded by remember { mutableStateOf(false) }
        OrcaComboField(text = license, onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = colors.window) {
            ProjectInfo.LICENSES.forEach { choice ->
                OrcaMenuItem(
                    text = choice,
                    onClick = {
                        expanded = false
                        viewModel.setLicense(choice)
                    },
                )
            }
        }
    }
    Text(orcaString("Description:"), color = colors.text, style = OrcaTheme.typography.body14)
    androidx.compose.foundation.text.BasicTextField(
        value = description,
        onValueChange = { description = it; viewModel.setDescription(it) },
        textStyle = OrcaTheme.typography.body14.copy(color = colors.text),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp)
            .border(1.dp, colors.border, OrcaTheme.shapes.control)
            .padding(10.dp),
    )
}

/**
 * AuFolderPanel: "Add File" and the folder's files. A file opens with the app
 * the system opens it with (AuFile::on_dclick()); its menu sets a picture as
 * the cover, renames it or deletes it, as the file's mask and corner do.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FolderPage(folder: AuxiliaryFolder, info: ProjectInfo, viewModel: ProjectViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.import(folder, uris.map { ExternalDocumentReference(it.toString()) })
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AddFileTile { picker.launch(MIME_TYPES.getValue(folder)) }
        info.filesIn(folder).forEach { file ->
            FileTile(
                folder = folder,
                file = file,
                cover = folder == AuxiliaryFolder.MODEL_PICTURES && info.modelInfo && info.coverFile == file.name,
                onOpen = { viewModel.open(file) },
                onCover = { viewModel.setCover(file) },
                onRename = { viewModel.startRename(file) },
                onDelete = { viewModel.delete(file) },
            )
        }
    }
}

/** AuFile of the AddFileButton type. */
@Composable
private fun AddFileTile(onClick: () -> Unit) {
    val colors = OrcaTheme.colors
    Column(
        modifier = Modifier
            .size(TILE_WIDTH, TILE_HEIGHT)
            .clip(OrcaTheme.shapes.control)
            .border(1.dp, colors.border, OrcaTheme.shapes.control)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Image(painterResource(DesignR.drawable.orca_auxiliary_add_file), contentDescription = null, modifier = Modifier.size(32.dp))
        Text(orcaString("Add File"), color = colors.textSide, style = OrcaTheme.typography.body14)
    }
}

@Composable
private fun FileTile(
    folder: AuxiliaryFolder,
    file: AuxiliaryFile,
    cover: Boolean,
    onOpen: () -> Unit,
    onCover: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = OrcaTheme.colors
    Column(
        modifier = Modifier
            .width(TILE_WIDTH)
            .clip(OrcaTheme.shapes.control)
            .background(colors.sidebarBackground)
            .clickable(onClick = onOpen),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(TILE_HEIGHT - 40.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (folder == AuxiliaryFolder.MODEL_PICTURES) {
                FileImage(file, Modifier.fillMaxSize(), ContentScale.Fit)
            } else {
                Image(painterResource(placeholderOf(folder, file)), contentDescription = null, modifier = Modifier.size(96.dp))
            }
            // AuFile::PaintForeground(): the cover's mark, "Cover" in white on it; the
            // desktop's corner holds the delete button here, which a phone always shows.
            if (cover) {
                // auxiliary_cover.svg: a rounded rectangle of #009688.
                Text(
                    orcaString("Cover"),
                    color = androidx.compose.ui.graphics.Color.White,
                    style = OrcaTheme.typography.body12,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(COVER_COLOR, androidx.compose.foundation.shape.RoundedCornerShape(5.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            // AuFile::PaintForeground(): the delete button in the corner.
            Image(
                painterResource(DesignR.drawable.orca_auxiliary_delete),
                contentDescription = orcaString("Delete"),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .clickable(onClick = onDelete)
                    .padding(8.dp)
                    .size(24.dp),
            )
        }
        // The file's edit mask, "Set as cover" and "Rename", which the desktop shows under the pointer.
        if (folder == AuxiliaryFolder.MODEL_PICTURES) {
            MaskButton(orcaString("Set as cover"), onCover, Modifier.fillMaxWidth())
        }
        MaskButton(orcaString("Rename"), onRename, Modifier.fillMaxWidth())
        Text(
            text = file.name,
            color = colors.text,
            style = OrcaTheme.typography.body14,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun MaskButton(label: String, onClick: () -> Unit, modifier: Modifier) {
    Text(
        text = label,
        color = OrcaTheme.colors.accent,
        style = OrcaTheme.typography.body13,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/** AuFile::enter_rename_mode(): the name before its first dot, to give another, with why it is refused. */
@Composable
private fun RenameDialog(file: AuxiliaryFile, error: List<app.orcinus.shadow.core.model.OrcaText>?, onRename: (String) -> Unit, onCancel: () -> Unit) {
    val colors = OrcaTheme.colors
    var name by remember(file) { mutableStateOf(file.name.substringBefore('.')) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(orcaString("Rename"), style = OrcaTheme.typography.head16) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OrcaTextField(value = name, onValueChange = { name = it })
                error?.let { Text(orcaText(it), color = colors.error, style = OrcaTheme.typography.body13) }
            }
        },
        confirmButton = { OrcaButton(orcaString("OK"), onClick = { onRename(name) }) },
        dismissButton = { OrcaButton(orcaString("Cancel"), onClick = onCancel, style = OrcaButtonStyle.Regular) },
        containerColor = colors.window,
    )
}

/** A picture of the project, read from its file, the size the view asks. */
@Composable
private fun FileImage(file: AuxiliaryFile, modifier: Modifier, scale: ContentScale) {
    val bitmap by produceState<ImageBitmap?>(null, file.path) {
        value = withContext(Dispatchers.IO) {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path.value, options)
            // Pictures are kept far smaller than cameras take them.
            var sample = 1
            while (options.outWidth / (sample * 2) >= MAX_PICTURE && options.outHeight / (sample * 2) >= MAX_PICTURE) sample *= 2
            BitmapFactory.decodeFile(file.path.value, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }
    }
    bitmap?.let { Image(it, contentDescription = file.name, modifier = modifier, contentScale = scale) } ?: Box(modifier)
}

/** The page shows the description as HTML (html_decode()), its lines kept (convert_newlines_to_br()). */
private fun htmlText(html: String): AnnotatedString = AnnotatedString.fromHtml(html.replace("\n", "<br>"))

/** ShowModelInfo(): the licence's badge; none for one it does not know. */
@DrawableRes
private fun licenceBadge(license: String): Int? = when (license.uppercase()) {
    "CC0" -> R.drawable.project_cc_zero
    "BY" -> R.drawable.project_by
    "BY-SA" -> R.drawable.project_by_sa
    "BY-ND" -> R.drawable.project_by_nd
    "BY-NC" -> R.drawable.project_by_nc
    "BY-NC-SA" -> R.drawable.project_by_nc_sa
    "BY-NC-ND" -> R.drawable.project_by_nc_nd
    else -> null
}

/** AuFile::AuFile(): the placeholder of a file that is not a picture. */
@DrawableRes
private fun placeholderOf(folder: AuxiliaryFolder, file: AuxiliaryFile): Int {
    val extension = file.name.substringAfterLast('.', "")
    return when {
        folder == AuxiliaryFolder.BILL_OF_MATERIALS && (extension == "xls" || extension == "xlsx") -> DesignR.drawable.orca_placeholder_excel
        folder == AuxiliaryFolder.BILL_OF_MATERIALS && extension == "pdf" -> DesignR.drawable.orca_placeholder_pdf
        folder == AuxiliaryFolder.ASSEMBLY_GUIDE -> DesignR.drawable.orca_placeholder_pdf
        else -> DesignR.drawable.orca_placeholder_txt
    }
}

/** ConstructFileHtml()'s tails. */
private val IMAGE_TAILS = setOf("jpg", "jpeg", "bmp", "gif", "svg", "png")
private val EXCEL_TAILS = setOf("xlsx", "xlsm", "xlsb", "csv", "xls", "xltx", "xltm", "xlt", "xlam", "xla")
private val PDF_TAILS = setOf("pdf", "fdf", "xfdf", "xdp", "ppdf", "ofd")

/** on_import_file()'s wildcards, as the document picker's types. */
private val MIME_TYPES = mapOf(
    AuxiliaryFolder.MODEL_PICTURES to arrayOf("image/png", "image/jpeg", "image/bmp", "image/x-ms-bmp"),
    AuxiliaryFolder.BILL_OF_MATERIALS to arrayOf(
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/pdf",
    ),
    AuxiliaryFolder.ASSEMBLY_GUIDE to arrayOf("application/pdf"),
    AuxiliaryFolder.OTHERS to arrayOf("text/plain"),
)

private val TILE_WIDTH = 150.dp
private val COVER_COLOR = androidx.compose.ui.graphics.Color(0xFF009688)
private val TILE_HEIGHT = 170.dp
private const val MAX_PICTURE = 1024
