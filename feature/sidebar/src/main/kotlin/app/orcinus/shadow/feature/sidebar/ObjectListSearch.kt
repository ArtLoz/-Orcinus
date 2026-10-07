package app.orcinus.shadow.feature.sidebar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.R as DesignR
import app.orcinus.shadow.core.designsystem.component.OrcaIconButton
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.LayerRangeId
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.ObjectPartId
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SettingDefinition
import app.orcinus.shadow.core.model.hasConnectors
import app.orcinus.shadow.core.model.isCut
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.volumeAt
import app.orcinus.shadow.core.ui.R as UiR
import app.orcinus.shadow.core.ui.displayName
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.plate.volumeName

/** What picking a row of the object list does: each is the tap of that row. */
internal sealed interface ObjectListTarget {
    data class Plate(val index: Int) : ObjectListTarget

    data class PlateSettings(val index: Int) : ObjectListTarget

    data object Outside : ObjectListTarget

    /** An object's row, which picks its first copy. */
    data class Object(val copy: PlateInstanceId) : ObjectListTarget

    data class ObjectSettings(val copy: PlateInstanceId) : ObjectListTarget

    data class Connectors(val copy: PlateInstanceId) : ObjectListTarget

    data class Part(val id: ObjectPartId) : ObjectListTarget

    data class PartSettings(val id: ObjectPartId) : ObjectListTarget

    data class Layers(val mesh: ScenePath) : ObjectListTarget

    data class Range(val id: LayerRangeId) : ObjectListTarget

    data class RangeSettings(val id: LayerRangeId) : ObjectListTarget

    data class Copy(val id: PlateInstanceId) : ObjectListTarget
}

/** A row of the object list (an ObjectDataViewModelNode): what picks it, the name it shows, and the rows under it. */
internal class ObjectListNode(
    val target: ObjectListTarget,
    val name: String,
    val children: List<ObjectListNode> = emptyList(),
)

/** A row named after the rows it hangs under, as ObjectDataViewModel's assembly_name_list holds it. */
internal data class ObjectListName(val target: ObjectListTarget, val name: String)

/**
 * A row the search found (search_found_list): its name, where the text stands
 * in it (the "<b>" marks SearchItem::OnPaint() draws bold), and the place of
 * the row among the list's rows.
 */
internal data class ObjectListFound(
    val target: ObjectListTarget,
    val name: String,
    val matches: List<IntRange>,
    val row: Int,
)

internal object ObjectListSearch {
    /**
     * ObjectDataViewModel::assembly_name(): every row of every plate in the
     * list's order, a plate by its own name and any other row by the names of
     * the rows it hangs under and its own, joined with ":".
     */
    fun names(plates: List<ObjectListNode>): List<ObjectListName> = buildList {
        fun visit(node: ObjectListNode, name: String) {
            add(ObjectListName(node.target, name))
            node.children.forEach { visit(it, "$name:${it.name}") }
        }
        plates.forEach { visit(it, it.name) }
    }

    /**
     * ObjectDataViewModel::search_object(): the rows whose name holds [text],
     * whatever its case, with every place it stands in; no text finds every row.
     */
    fun search(names: List<ObjectListName>, text: String): List<ObjectListFound> = names.mapIndexedNotNull { row, (target, name) ->
        if (text.isEmpty()) return@mapIndexedNotNull ObjectListFound(target, name, emptyList(), row)
        val matches = buildList {
            var from = 0
            while (true) {
                val at = name.indexOf(text, from, ignoreCase = true)
                if (at < 0) break
                add(at until at + text.length)
                from = at + text.length
            }
        }
        if (matches.isEmpty()) null else ObjectListFound(target, name, matches, row)
    }
}

/**
 * The rows [objectListItems] lists, in its order and with the names they show,
 * as the search goes through them. The list has no "Instances" row, so the
 * copies of an object hang under the object itself.
 */
@Composable
internal fun objectListTree(state: SidebarUiState): List<ObjectListNode> {
    val plateDefinitions = state.plateSettings.tab?.definitions.orEmpty()
    val objectDefinitions = (state.objectSettings.tab ?: state.processSettings.tab)?.definitions.orEmpty()
    val partDefinitions = (state.partSettings.tab ?: state.processSettings.tab)?.definitions.orEmpty()
    val rangeDefinitions = (state.rangeSettings.tab ?: state.processSettings.tab)?.definitions.orEmpty()
    val plates = state.plates.map { plate ->
        val settings = settingsNode(ObjectListTarget.PlateSettings(plate.index), plate.overrides, plateDefinitions)
        val objects = plate.objects.map { objectNode(it, objectDefinitions, partDefinitions, rangeDefinitions) }
        ObjectListNode(ObjectListTarget.Plate(plate.index), plate.itemName(), listOfNotNull(settings) + objects)
    }
    val outside = state.outsideObjects.map { objectNode(it, objectDefinitions, partDefinitions, rangeDefinitions) }
    return plates + ObjectListNode(ObjectListTarget.Outside, orcaString("Outside"), outside)
}

@Composable
private fun objectNode(
    plateObject: PlateObject,
    definitions: Map<String, SettingDefinition>,
    partDefinitions: Map<String, SettingDefinition>,
    rangeDefinitions: Map<String, SettingDefinition>,
): ObjectListNode {
    val mesh = plateObject.mesh
    val first = PlateInstanceId(mesh, 0)
    val children = buildList {
        if (plateObject.isCut && plateObject.hasConnectors && plateObject.parts.isNotEmpty()) {
            add(ObjectListNode(ObjectListTarget.Connectors(first), orcaString("Cut connectors")))
        }
        if (plateObject.listsVolumes()) (0..plateObject.parts.size).forEach { at ->
            val part = plateObject.volumeAt(at) ?: return@forEach
            if (plateObject.isCut && part.cutInfo.connector) return@forEach
            val id = ObjectPartId(mesh, at)
            val settings = settingsNode(ObjectListTarget.PartSettings(id), part.settings, partDefinitions)
            add(ObjectListNode(ObjectListTarget.Part(id), plateObject.volumeName(at), listOfNotNull(settings)))
        }
        if (plateObject.layerRanges.isNotEmpty()) {
            val ranges = plateObject.layerRanges.mapIndexed { at, range ->
                val id = LayerRangeId(mesh, at)
                val settings = settingsNode(ObjectListTarget.RangeSettings(id), range.settings, rangeDefinitions)
                ObjectListNode(ObjectListTarget.Range(id), stringResource(R.string.object_layer_range, range.bottom, range.top), listOfNotNull(settings))
            }
            add(ObjectListNode(ObjectListTarget.Layers(mesh), orcaString("Layers"), ranges))
        }
        if (plateObject.instances.size > 1) plateObject.instances.indices.forEach { index ->
            add(ObjectListNode(ObjectListTarget.Copy(PlateInstanceId(mesh, index)), stringResource(R.string.object_instance_name, index + 1)))
        }
        settingsNode(ObjectListTarget.ObjectSettings(first), plateObject.settings, definitions)?.let { add(it) }
    }
    return ObjectListNode(ObjectListTarget.Object(first), plateObject.displayName(), children)
}

@Composable
private fun settingsNode(target: ObjectListTarget, settings: ModelSettings, definitions: Map<String, SettingDefinition>): ObjectListNode? =
    settings.categories(definitions).takeIf { it.isNotEmpty() }?.let { ObjectListNode(target, settingsItemName(it)) }

/** Sidebar::jump_to_object(): ObjectList::selected_object() selects the row alone, as a tap on it does. */
internal fun ObjectListActions.jumpTo(target: ObjectListTarget) {
    when (target) {
        is ObjectListTarget.Plate -> selectPlate(target.index)
        is ObjectListTarget.PlateSettings -> selectPlateSettings(target.index)
        ObjectListTarget.Outside -> Unit
        is ObjectListTarget.Object -> select(target.copy, false)
        is ObjectListTarget.ObjectSettings -> selectSettings(target.copy)
        is ObjectListTarget.Connectors -> selectConnectors(target.copy)
        is ObjectListTarget.Part -> selectPart(target.id)
        is ObjectListTarget.PartSettings -> selectPartSettings(target.id)
        is ObjectListTarget.Layers -> addRange(target.mesh, null)
        is ObjectListTarget.Range -> selectRange(target.id)
        is ObjectListTarget.RangeSettings -> selectRangeSettings(target.id)
        is ObjectListTarget.Copy -> select(target.id, false)
    }
}

/**
 * The search bar above the object list (Sidebar's m_search_bar) and what it
 * finds (Search::SearchObjectDialog): the search opens when the bar takes the
 * focus, and then the rows it finds stand in for the list's.
 */
@Stable
internal class ObjectListSearchState(private val listState: LazyListState, private val focusManager: FocusManager) {
    var text by mutableStateOf("")
    var active by mutableStateOf(false)
        private set

    /** The index of the row a pick scrolls the list to once its rows are back. */
    var reveal by mutableStateOf<Int?>(null)

    /** SearchObjectDialog::Popup(): the bar took the focus, and the search starts empty. */
    fun open() {
        if (active) return
        active = true
        text = ""
    }

    /** SearchObjectDialog::Die() and wxCUSTOMEVT_EXIT_SEARCH: the search closes, the bar empties and lets the focus go. */
    fun close() {
        dismiss()
        focusManager.clearFocus()
    }

    /** SearchObjectDialog::Dismiss(): the focus went elsewhere. */
    fun dismiss() {
        active = false
        text = ""
    }

    /**
     * SearchItem::on_mouse_left_up(): the search closes and the row is picked.
     * The list's rows come back where the found ones stand, the first of them
     * where the first found one is.
     */
    fun choose(at: Int, found: ObjectListFound, onChoose: (ObjectListTarget) -> Unit) {
        val shown = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == foundKey(at) }
        reveal = shown?.let { it.index - at + found.row }
        close()
        onChoose(found.target)
    }
}

@Composable
internal fun rememberObjectListSearch(listState: LazyListState): ObjectListSearchState {
    val focusManager = LocalFocusManager.current
    val search = remember(listState, focusManager) { ObjectListSearchState(listState, focusManager) }
    BackHandler(enabled = search.active, onBack = search::close)
    // ObjectList::ensure_current_item_visible() of the picked row.
    LaunchedEffect(search.reveal) {
        val index = search.reveal ?: return@LaunchedEffect
        try {
            listState.animateScrollToItem(index)
        } finally {
            // A finger on the list stops the scroll; the next pick scrolls again.
            if (search.reveal == index) search.reveal = null
        }
    }
    return search
}

/** The rows the search goes through while it is open, each named after the rows it hangs under. */
@Composable
internal fun objectSearchRows(state: SidebarUiState, search: ObjectListSearchState): List<ObjectListName> =
    if (search.active) ObjectListSearch.names(objectListTree(state)) else emptyList()

/**
 * The search bar, and while the search is open the [rows] its text finds in
 * place of the object list. The text is read here, so that typing only
 * rebuilds the list.
 */
internal fun LazyListScope.objectSearchItems(
    search: ObjectListSearchState,
    rows: List<ObjectListName>,
    onChoose: (ObjectListTarget) -> Unit,
) {
    item(key = SEARCH_KEY) { ObjectSearchBar(search) }
    if (!search.active) return
    val found = ObjectListSearch.search(rows, search.text)
    if (found.isEmpty()) {
        item(key = "$SEARCH_KEY:empty") {
            Text(
                stringResource(UiR.string.settings_search_empty),
                color = OrcaTheme.colors.textSide,
                style = OrcaTheme.typography.body13,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
    items(found.size, key = ::foundKey) { at ->
        FoundRow(found[at]) { search.choose(at, found[at], onChoose) }
    }
}

private const val SEARCH_KEY = "object-search"

private fun foundKey(at: Int): String = "$SEARCH_KEY:$at"

/**
 * Plater's search bar: the search icon, and the hint until something is typed.
 * It takes the accent for its border while the search is open, as the
 * desktop bar does; the cross closes the search.
 */
@Composable
private fun ObjectSearchBar(search: ObjectListSearchState) {
    val colors = OrcaTheme.colors
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(40.dp)
            .clip(shape)
            .background(colors.buttonBackground)
            .border(1.dp, if (search.active) colors.accent else Color.Transparent, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { focus.requestFocus() }
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(DesignR.drawable.orca_search), contentDescription = null, tint = colors.textSide, modifier = Modifier.size(OrcaTheme.dimensions.iconSmall))
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        ) {
            if (search.text.isEmpty()) {
                Text(
                    orcaString("Search plate, object and part."),
                    color = colors.textSide,
                    style = OrcaTheme.typography.body14,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = search.text,
                onValueChange = { search.text = it },
                singleLine = true,
                textStyle = OrcaTheme.typography.body14.copy(color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .onFocusChanged { if (it.isFocused) search.open() else search.dismiss() },
            )
        }
        if (search.active) {
            OrcaIconButton(icon = DesignR.drawable.orca_cross, contentDescription = stringResource(UiR.string.clear_search), onClick = search::close)
        }
    }
}

/** SearchItem: the row's name, with the text it was found by in bold; a touch tints it as the desktop item's hover does. */
@Composable
private fun FoundRow(found: ObjectListFound, onClick: () -> Unit) {
    val colors = OrcaTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (pressed) colors.accentSelected else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = buildAnnotatedString {
                append(found.name)
                found.matches.forEach { addStyle(SpanStyle(fontWeight = FontWeight.Bold), it.first, it.last + 1) }
            },
            color = colors.text,
            style = OrcaTheme.typography.body14,
        )
    }
}
