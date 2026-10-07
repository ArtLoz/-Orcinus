package app.orcinus.shadow.core.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.R as DesignR
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaIconButton
import app.orcinus.shadow.core.designsystem.component.OrcaFullScreenDialog
import app.orcinus.shadow.core.designsystem.component.OrcaSheet
import app.orcinus.shadow.core.designsystem.component.OrcaTextField
import app.orcinus.shadow.core.designsystem.icon.orcaIcon
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.GcodePlaceholderInfo
import app.orcinus.shadow.core.model.GcodePlaceholders
import app.orcinus.shadow.core.model.GcodePlaceholdersOutcome
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.orca.orcaText

/**
 * OrcaSlicer's EditGCodeDialog: a custom G-code with the placeholders the
 * slicer fills in, grouped as the desktop dialog groups them. A tap chooses a
 * placeholder and describes it; the add button, or a double tap as the desktop
 * app's double click, writes it into the G-code. A wide screen keeps the list
 * beside the editor as the desktop dialog does; a phone gives the editor the
 * screen and opens the list from its add button. OK writes the G-code into
 * the preset ([onApply]); closing the dialog otherwise leaves it as it was.
 */
@Composable
fun EditGcodeDialog(
    key: String,
    loadPlaceholders: suspend () -> GcodePlaceholdersOutcome,
    describePlaceholder: suspend (key: String, presets: Boolean) -> GcodePlaceholderInfo,
    onApply: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OrcaTheme.colors
    var loaded by remember { mutableStateOf<GcodePlaceholders?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var gcode by remember { mutableStateOf(TextFieldValue()) }
    LaunchedEffect(key) {
        when (val outcome = loadPlaceholders()) {
            is GcodePlaceholdersOutcome.Success -> {
                loaded = outcome.placeholders
                // m_gcode_editor->SetInsertionPointEnd()
                gcode = TextFieldValue(outcome.placeholders.value, TextRange(outcome.placeholders.value.length))
            }
            is GcodePlaceholdersOutcome.Failure -> problem = outcome.message
        }
    }
    val placeholders = loaded
    val tree = remember(placeholders) { placeholders?.let { GcodePlaceholderTree(it.placeholders) } }
    val browser = remember(tree) { tree?.let(::PlaceholderBrowserState) }
    val editorFocus = remember { FocusRequester() }
    var listOpen by remember { mutableStateOf(false) }
    val insert: (Int) -> Unit = { index ->
        if (tree != null) {
            gcode = GcodePlaceholderTree.insert(gcode, tree.nodes[index].text)
            listOpen = false
            // m_gcode_editor->SetFocus()
            runCatching { editorFocus.requestFocus() }
        }
    }

    // EditGCodeDialog::EditGCodeDialog(): fit_in_display(*this, {100 * em, 70 * em}).
    OrcaFullScreenDialog(onDismissRequest = onDismiss, width = 1000.dp, height = 700.dp) {
        Column(
            Modifier
                .fillMaxSize()
                .background(colors.window),
        ) {
            FullScreenDialogTopBar(
                title = orcaText(OrcaText("Edit Custom G-code (%1%)", listOf(key))),
                okEnabled = placeholders != null,
                onCancel = onDismiss,
                onOk = { onApply(gcode.text) },
            )
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
            ) {
                val wide = maxWidth >= WIDE_LAYOUT
                Column(Modifier.fillMaxSize()) {
                    problem?.let {
                        Text(it, color = colors.error, style = OrcaTheme.typography.body13, modifier = Modifier.padding(12.dp))
                    }
                    if (tree == null || browser == null) {
                        if (problem == null) CircularProgressIndicator(color = colors.accent, modifier = Modifier.padding(16.dp))
                        return@Column
                    }
                    if (wide) {
                        // The desktop dialog's grid: the list, the add button, the editor.
                        Text(
                            text = orcaString("Built-in placeholders (Double click item to add to G-code)") + ":",
                            color = colors.text,
                            style = OrcaTheme.typography.body13,
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
                        )
                        Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 4.dp)) {
                            PlaceholderBrowser(tree, browser, onInsert = insert, modifier = Modifier.weight(1f))
                            Box(Modifier.align(Alignment.CenterVertically)) {
                                AddPlaceholderButton(enabled = browser.selected != null) { browser.selected?.let(insert) }
                            }
                            GcodeEditor(gcode, { gcode = it }, editorFocus, Modifier.weight(2f))
                        }
                        PlaceholderDescription(tree, browser, describePlaceholder, Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                    } else {
                        GcodeEditor(gcode, { gcode = it }, editorFocus, Modifier.weight(1f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            OrcaButton(
                                text = orcaString("Add selected placeholder to G-code"),
                                onClick = { listOpen = true },
                                style = OrcaButtonStyle.Regular,
                                icon = DesignR.drawable.orca_add_copies,
                            )
                        }
                    }
                }
            }
        }
        if (listOpen && tree != null && browser != null) {
            PlaceholderSheet(tree, browser, describePlaceholder, onInsert = insert, onDismiss = { listOpen = false })
        }
    }
}

/**
 * A full-screen dialog's caption with Cancel and OK (DialogButtons): a bar over
 * a phone's screen, and the title bar of the dialog's window on a larger one
 * (OrcaFullScreenDialog), where no system bar is under it to pad.
 */
@Composable
internal fun FullScreenDialogTopBar(
    title: String,
    okEnabled: Boolean,
    onCancel: () -> Unit,
    onOk: () -> Unit,
    okText: String? = null,
    showOk: Boolean = true,
) {
    val colors = OrcaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.tabBar)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .height(OrcaTheme.dimensions.tabBarHeight)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OrcaIconButton(
            icon = DesignR.drawable.orca_topbar_close,
            contentDescription = orcaString("Cancel"),
            onClick = onCancel,
            tint = colors.onTabBar,
        )
        Text(
            text = title,
            color = colors.onTabBar,
            style = OrcaTheme.typography.head15,
            // The key of the G-code follows a long caption.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp, end = 12.dp)
                .semantics { heading() },
        )
        if (showOk) {
            OrcaButton(text = okText ?: orcaString("OK"), onClick = onOk, enabled = okEnabled, modifier = Modifier.padding(end = 4.dp))
        }
    }
}

/** m_gcode_editor: the G-code in the code font. */
@Composable
private fun GcodeEditor(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit, focus: FocusRequester, modifier: Modifier) {
    val colors = OrcaTheme.colors
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    var viewport by remember { mutableIntStateOf(0) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    // The cursor stays in view: after a placeholder is written in, and when the
    // keyboard makes the editor smaller, as wxTextCtrl shows its insertion point.
    LaunchedEffect(value.selection, value.text, viewport, layout) {
        val text = layout ?: return@LaunchedEffect
        if (viewport <= 0) return@LaunchedEffect
        val cursor = text.getCursorRect(value.selection.end.coerceIn(0, text.layoutInput.text.length))
        val inset = with(density) { EDITOR_PADDING.roundToPx() }
        val top = cursor.top.toInt() + inset
        val bottom = cursor.bottom.toInt() + inset * 2
        when {
            bottom > scroll.value + viewport -> scroll.scrollTo(bottom - viewport)
            top < scroll.value -> scroll.scrollTo(top)
        }
    }
    Box(
        modifier
            .fillMaxSize()
            .padding(8.dp)
            .border(1.dp, colors.border, OrcaTheme.shapes.control)
            .onSizeChanged { viewport = it.height }
            .verticalScroll(scroll),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = OrcaTheme.typography.body13.copy(color = colors.text, fontFamily = FontFamily.Monospace),
            cursorBrush = SolidColor(colors.accent),
            onTextLayout = { layout = it },
            modifier = Modifier
                .fillMaxWidth()
                // The whole box takes the touch that places the cursor.
                .heightIn(min = with(density) { viewport.toDp() })
                .padding(EDITOR_PADDING)
                .focusRequester(focus),
        )
    }
}

/** The add button between the list and the editor ("add_copies"). */
@Composable
private fun AddPlaceholderButton(enabled: Boolean, onClick: () -> Unit) {
    OrcaIconButton(
        icon = DesignR.drawable.orca_add_copies,
        contentDescription = orcaString("Add selected placeholder to G-code"),
        onClick = onClick,
        enabled = enabled,
        tint = Color.Unspecified,
    )
}

/** What the list keeps while the dialog is open: the expanded groups, the search, the chosen placeholder. */
private class PlaceholderBrowserState(tree: GcodePlaceholderTree) {
    var expanded by mutableStateOf(tree.initiallyExpanded)
    var query by mutableStateOf("")
    var selected by mutableStateOf<Int?>(null)
}

/**
 * The list as a sheet over the editor of a phone, with the description and the
 * add button under it; a narrow window of a tablet opens it as a dialog.
 */
@Composable
private fun PlaceholderSheet(
    tree: GcodePlaceholderTree,
    browser: PlaceholderBrowserState,
    describePlaceholder: suspend (String, Boolean) -> GcodePlaceholderInfo,
    onInsert: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OrcaTheme.colors
    OrcaSheet(onDismissRequest = onDismiss, skipPartiallyExpanded = true) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                text = orcaString("Built-in placeholders (Double click item to add to G-code)") + ":",
                color = colors.text,
                style = OrcaTheme.typography.body13,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            PlaceholderBrowser(tree, browser, onInsert, Modifier.heightIn(max = 440.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                PlaceholderDescription(tree, browser, describePlaceholder, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                OrcaButton(
                    text = orcaString("Add"),
                    onClick = { browser.selected?.let(onInsert) },
                    enabled = browser.selected != null,
                    icon = DesignR.drawable.orca_add_copies,
                )
            }
        }
    }
}

/** m_search_bar and m_params_list: the search, and the groups with their placeholders. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaceholderBrowser(
    tree: GcodePlaceholderTree,
    browser: PlaceholderBrowserState,
    onInsert: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OrcaTheme.colors
    Column(modifier) {
        OrcaTextField(
            value = browser.query,
            onValueChange = { browser.query = it },
            hint = orcaString("Search G-code placeholders"),
            trailing = if (browser.query.isEmpty()) null else {
                {
                    OrcaIconButton(
                        icon = DesignR.drawable.orca_im_text_search_close,
                        contentDescription = orcaString("Clear"),
                        onClick = { browser.query = "" },
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        val rows = tree.rows(browser.expanded, browser.query)
        LazyColumn(Modifier.fillMaxWidth()) {
            items(rows, key = { it.index }) { row ->
                val node = tree.nodes[row.index]
                val group = tree.isGroup(row.index)
                val open = row.index in browser.expanded || browser.query.isNotEmpty()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (browser.selected == row.index) colors.accentSelected else Color.Transparent)
                        .combinedClickable(
                            role = Role.Button,
                            onClick = {
                                if (group) {
                                    browser.expanded = if (row.index in browser.expanded) browser.expanded - row.index else browser.expanded + row.index
                                } else {
                                    browser.selected = row.index
                                }
                            },
                            onDoubleClick = if (group) null else {
                                {
                                    browser.selected = row.index
                                    onInsert(row.index)
                                }
                            },
                        )
                        .heightIn(min = 36.dp)
                        .padding(start = 12.dp + 16.dp * row.depth, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                        if (group) {
                            Icon(
                                painter = painterResource(DesignR.drawable.orca_hms_arrow),
                                contentDescription = null,
                                tint = colors.textSide,
                                modifier = Modifier
                                    .size(12.dp)
                                    .rotate(if (open) 90f else 0f),
                            )
                        }
                    }
                    orcaIcon(node.icon)?.let { icon ->
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(16.dp),
                        )
                    }
                    val text = if (group) orcaText(node.label) else node.text
                    Text(
                        text = buildAnnotatedString {
                            append(text)
                            row.highlight?.let { range ->
                                if (range.last < text.length) {
                                    addStyle(SpanStyle(background = colors.accent, color = Color.White), range.first, range.last + 1)
                                }
                            }
                        },
                        color = colors.text,
                        style = OrcaTheme.typography.body13.copy(
                            fontWeight = if (group) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = if (group) OrcaTheme.typography.body13.fontFamily else FontFamily.Monospace,
                        ),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}

/**
 * m_param_label and m_param_description: the label and the type of the chosen
 * placeholder, and what its definition says about it.
 */
@Composable
private fun PlaceholderDescription(
    tree: GcodePlaceholderTree,
    browser: PlaceholderBrowserState,
    describePlaceholder: suspend (String, Boolean) -> GcodePlaceholderInfo,
    modifier: Modifier = Modifier,
) {
    val colors = OrcaTheme.colors
    val selected = browser.selected
    var info by remember { mutableStateOf<GcodePlaceholderInfo?>(null) }
    LaunchedEffect(selected) {
        info = null
        if (selected != null) info = describePlaceholder(tree.nodes[selected].key, tree.inPresets(selected))
    }
    val described = info
    val label = when {
        selected == null -> orcaString("Select placeholder")
        described == null -> tree.nodes[selected].key
        described.undefined -> "Undef optptr"
        else -> "${orcaText(described.label).ifEmpty { tree.nodes[selected].key }}\n(${described.type})"
    }
    Column(modifier) {
        Text(label, color = colors.text, style = OrcaTheme.typography.head13)
        described?.description?.takeIf { it.isNotEmpty() }?.let { description ->
            Text(
                text = orcaText(description),
                color = colors.text,
                style = OrcaTheme.typography.body13,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .heightIn(max = 120.dp)
                    .verticalScroll(rememberScrollState()),
            )
        }
    }
}

/** Between the editor's frame and its text. */
private val EDITOR_PADDING = 8.dp

/** From this width on the list stays beside the editor, as in the desktop dialog. */
private val WIDE_LAYOUT = 720.dp
