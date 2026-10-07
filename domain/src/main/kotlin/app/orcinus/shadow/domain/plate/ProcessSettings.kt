package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.FlushOption
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.ModelSettingsOutcome
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SettingsClipboard
import app.orcinus.shadow.core.model.SettingsItem
import app.orcinus.shadow.core.model.SettingsItemKind
import app.orcinus.shadow.core.model.flushesInto
import app.orcinus.shadow.core.model.kind
import app.orcinus.shadow.core.model.selectedObjectMeshes
import app.orcinus.shadow.core.model.volumeAt
import app.orcinus.shadow.core.model.withLayerRangeAt
import app.orcinus.shadow.core.model.withSettings
import app.orcinus.shadow.core.model.withVolumeAt
import app.orcinus.shadow.slicing.api.PresetSettingsEditor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * "Copy Process Settings" (ObjectList::copy_settings_to_clipboard): the
 * settings of the item's object, with the item's own over them, wait for an
 * item of the same kind to take them.
 */
class CopyProcessSettingsUseCase(private val repository: PlateRepository) {
    operator fun invoke(item: SettingsItem) = repository.update { state ->
        val owner = state.objects.withMesh(item.mesh) ?: return@update state
        val own = owner.settingsOf(item) ?: return@update state
        val settings = if (item is SettingsItem.Object) own else ModelSettings(owner.settings.values + own.values)
        // The object list's clipboard holds settings now; the ranges it kept stay.
        state.copy(settingsClipboard = SettingsClipboard(item.kind, settings), listClipboard = state.listClipboard?.copy(holdsRanges = false))
    }
}

/**
 * "Paste Process Settings" (ObjectList::paste_settings_into_list): the item's
 * settings become the copied ones, which the engine works out as the desktop
 * app does, as one step of Undo ("Paste settings"). The settings tabs show the
 * item's new settings, and G-code sliced before no longer applies.
 */
class PasteProcessSettingsUseCase(
    private val editor: PresetSettingsEditor,
    private val repository: PlateRepository,
    private val settingsTabs: PresetSettingsTabs,
    private val applicationScope: CoroutineScope,
) {
    operator fun invoke(item: SettingsItem) {
        val state = repository.state.value
        val clipboard = state.settingsClipboard?.takeIf { it.kind == item.kind } ?: return
        val owner = state.objects.withMesh(item.mesh)
        val target = owner?.settingsOf(item)
        if (state.busy || owner == null || target == null) return
        applicationScope.launch {
            val parent = owner.settings.takeUnless { item is SettingsItem.Object }
            val pasted = editor.pasteModelSettings(clipboard.settings, target, parent) as? ModelSettingsOutcome.Success ?: return@launch
            var changed = false
            repository.update { current ->
                val latest = current.objects.withMesh(item.mesh)
                // The plate moved on meanwhile: the item's settings are no longer the ones pasted into.
                if (current.busy || latest?.settingsOf(item) != target) return@update current
                changed = true
                current.recorded().copy(objects = current.objects.replaced(latest.withSettingsOf(item, pasted.settings)), result = null)
            }
            if (changed) settingsTabs.refresh()
        }
    }

    /**
     * paste_settings_into_list() over the objects the selection holds, or
     * the volumes of one object it holds: each takes the copied settings, as
     * one step of Undo.
     */
    fun selected() {
        val state = repository.state.value
        val parts = state.selectedParts().takeIf { it.size > 1 }
        val items = parts?.map(SettingsItem::Volume) ?: state.selectedObjectMeshes().map(SettingsItem::Object)
        val clipboard = state.settingsClipboard?.takeIf { it.kind == (if (parts != null) SettingsItemKind.VOLUME else SettingsItemKind.OBJECT) } ?: return
        if (state.busy || items.isEmpty()) return
        val targets = items.mapNotNull { item -> state.objects.withMesh(item.mesh)?.settingsOf(item)?.let { item to it } }
        applicationScope.launch {
            val pasted = targets.mapNotNull { (item, target) ->
                // A volume's settings sit on its object's.
                val parent = state.objects.withMesh(item.mesh)?.settings?.takeUnless { item is SettingsItem.Object }
                (editor.pasteModelSettings(clipboard.settings, target, parent) as? ModelSettingsOutcome.Success)?.let { Triple(item, target, it.settings) }
            }
            var changed = false
            repository.update { current ->
                if (current.busy) return@update current
                // An object that changed meanwhile keeps its change.
                val updated = pasted.mapNotNull { (item, target, settings) ->
                    current.objects.withMesh(item.mesh)?.takeIf { it.settingsOf(item) == target }?.withSettingsOf(item, settings)
                }
                if (updated.isEmpty()) return@update current
                changed = true
                current.recorded().copy(objects = updated.fold(current.objects) { objects, plateObject -> objects.replaced(plateObject) }, result = null)
            }
            if (changed) settingsTabs.refresh()
        }
    }
}

/**
 * The object menu's Flush Options (MenuFactory::append_menu_items_flush_options):
 * the check item switches the option for the object, starting from the value
 * the object has, or the process preset's when it has none. The desktop app
 * sets the object's config without a step of Undo; the settings shown follow.
 */
class SetFlushOptionUseCase(
    private val repository: PlateRepository,
    private val settingsTabs: PresetSettingsTabs,
    private val applicationScope: CoroutineScope,
) {
    operator fun invoke(mesh: ScenePath, option: FlushOption) {
        var changed = false
        repository.update { state ->
            val target = state.objects.withMesh(mesh)
            if (state.busy || target == null || !state.flushing.primeTower) return@update state
            val current = target.flushesInto(option, state.flushing.flushInto)
            changed = true
            state.copy(
                objects = state.objects.replaced(target.withSettings(ModelSettings(target.settings.values + (option.key to configBool(!current))))),
                result = null,
            )
        }
        if (changed) applicationScope.launch { settingsTabs.refresh() }
    }
}

/**
 * "Enable painted fuzzy skin for this object" of GLGizmoFuzzySkin's warning:
 * the object's fuzzy skin becomes "Painted only" (FuzzySkinType::None), so
 * what is painted takes effect. The desktop app sets the object's config
 * without a step of Undo; the settings shown follow.
 */
class EnablePaintedFuzzySkinUseCase(
    private val repository: PlateRepository,
    private val settingsTabs: PresetSettingsTabs,
    private val applicationScope: CoroutineScope,
) {
    operator fun invoke(mesh: ScenePath) {
        var changed = false
        repository.update { state ->
            val target = state.objects.withMesh(mesh)
            if (state.busy || target == null) return@update state
            changed = true
            state.copy(
                objects = state.objects.replaced(target.withSettings(ModelSettings(target.settings.values + (FUZZY_SKIN to FUZZY_SKIN_PAINTED_ONLY)))),
                result = null,
            )
        }
        if (changed) applicationScope.launch { settingsTabs.refresh() }
    }
}

/** The fuzzy_skin option, and its "Painted only" as OrcaSlicer writes it into a project. */
private const val FUZZY_SKIN = "fuzzy_skin"
private const val FUZZY_SKIN_PAINTED_ONLY = "none"

/** ConfigOptionBool as OrcaSlicer writes it into a project. */
private fun configBool(value: Boolean): String = if (value) "1" else "0"

/** ObjectList::get_item_config(): the settings of the item itself. */
internal fun PlateObject.settingsOf(item: SettingsItem): ModelSettings? = when (item) {
    is SettingsItem.Object -> settings
    is SettingsItem.Volume -> volumeAt(item.id.index)?.settings
    is SettingsItem.Layer -> layerRanges.getOrNull(item.id.index)?.settings
}

internal fun PlateObject.withSettingsOf(item: SettingsItem, settings: ModelSettings): PlateObject = when (item) {
    is SettingsItem.Object -> withSettings(settings)
    is SettingsItem.Volume -> volumeAt(item.id.index)?.let { withVolumeAt(item.id.index, it.copy(settings = settings)) } ?: this
    is SettingsItem.Layer -> layerRanges.getOrNull(item.id.index)?.let { withLayerRangeAt(item.id.index, it.copy(settings = settings)) } ?: this
}
