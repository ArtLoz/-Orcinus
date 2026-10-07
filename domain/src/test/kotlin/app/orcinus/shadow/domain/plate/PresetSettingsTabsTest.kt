package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.BoundingSphere
import app.orcinus.shadow.core.model.BuildVolumeFit
import app.orcinus.shadow.core.model.ComparedPresets
import app.orcinus.shadow.core.model.ConfigExportKind
import app.orcinus.shadow.core.model.ConfigExportOptionsOutcome
import app.orcinus.shadow.core.model.ConfigOverwriteAnswer
import app.orcinus.shadow.core.model.CreateFilamentOptionsOutcome
import app.orcinus.shadow.core.model.CreateFilamentRequest
import app.orcinus.shadow.core.model.CreatePrinterOptionsOutcome
import app.orcinus.shadow.core.model.CreatePrinterRequest
import app.orcinus.shadow.core.model.CustomFilamentsOutcome
import app.orcinus.shadow.core.model.DialogIcon
import app.orcinus.shadow.core.model.EngineAvailability
import app.orcinus.shadow.core.model.EngineState
import app.orcinus.shadow.core.model.FilamentPresetsOutcome
import app.orcinus.shadow.core.model.ModelDimensions
import app.orcinus.shadow.core.model.ModelInspection
import app.orcinus.shadow.core.model.ObjectPart
import app.orcinus.shadow.core.model.ObjectPartId
import app.orcinus.shadow.core.model.PresetBundlesOutcome
import app.orcinus.shadow.core.model.PresetCreationOutcome
import app.orcinus.shadow.core.model.PresetSave
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.LayerRange
import app.orcinus.shadow.core.model.LayerRangeId
import app.orcinus.shadow.core.model.BedShape
import app.orcinus.shadow.core.model.BedShapeOutcome
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.PresetComparisonOutcome
import app.orcinus.shadow.core.model.ConfigTransferOutcome
import app.orcinus.shadow.core.model.PrinterConnectionOutcome
import app.orcinus.shadow.core.model.SearchCatalogOutcome
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.ModelSettingsOutcome
import app.orcinus.shadow.core.model.ModelSettingsRequest
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.OutputPath
import app.orcinus.shadow.core.model.PlateInstance
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateSliceResult
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.PresetChangeAction
import app.orcinus.shadow.core.model.PresetChoice
import app.orcinus.shadow.core.model.PresetGroup
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.PresetListItem
import app.orcinus.shadow.core.model.PresetNameOutcome
import app.orcinus.shadow.core.model.PresetNamesOutcome
import app.orcinus.shadow.core.model.PresetSettings
import app.orcinus.shadow.core.model.PresetSettingsOutcome
import app.orcinus.shadow.core.model.Presets
import app.orcinus.shadow.core.model.PresetsOutcome
import app.orcinus.shadow.core.model.ProfileId
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SettingState
import app.orcinus.shadow.core.model.SettingsDialog
import app.orcinus.shadow.core.model.SettingsGroup
import app.orcinus.shadow.core.model.SettingsLine
import app.orcinus.shadow.core.model.SettingsLineOption
import app.orcinus.shadow.core.model.SettingsMode
import app.orcinus.shadow.core.model.SettingsPage
import app.orcinus.shadow.core.model.SettingsItem
import app.orcinus.shadow.core.model.SettingsRequest
import app.orcinus.shadow.core.model.SettingsTab
import app.orcinus.shadow.core.model.SettingsTabOutcome
import app.orcinus.shadow.core.model.SettingsTabState
import app.orcinus.shadow.core.model.SetupFilamentsOutcome
import app.orcinus.shadow.core.model.SetupPrintersOutcome
import app.orcinus.shadow.core.model.SliceJobId
import app.orcinus.shadow.core.model.SliceStatistics
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.slicing.api.PresetManager
import app.orcinus.shadow.slicing.api.PresetSettingsEditor
import app.orcinus.shadow.core.model.GcodePlaceholderInfo
import app.orcinus.shadow.core.model.FlushVolumesChange
import app.orcinus.shadow.core.model.GcodePlaceholdersOutcome
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class PresetSettingsTabsTest {
    // Unconfined runs launched work immediately, so each test reads the final state.
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    /** The flushing volumes stay as they are. */
    private val NO_FLUSH_UPDATES = FlushVolumesUpdater { _, _ -> }

    @Test
    fun `the tab and the edited preset are described for the selected presets`() {
        val repository = FakeRepository(READY)
        val editor = FakeEditor()

        runSuspend { PresetSettingsTabs(editor, FakePresetManager(), NO_FLUSH_UPDATES, repository, scope).refresh() }

        val tab = repository.state.value.settingsTabs.getValue(PresetKind.PRINT)
        assertEquals(TAB, tab.tab)
        assertEquals(STANDARD, tab.settings)
        assertEquals(STRENGTH, tab.page)
        assertFalse(tab.changing)
        assertEquals(listOf<SettingsRequest>(SettingsRequest.Describe), editor.requests.map { it.request })
    }

    @Test
    fun `a change of the values is a change of the config, a tab only described is not`() {
        val repository = FakeRepository(READY)
        val editor = FakeEditor { call ->
            when (call.request) {
                is SettingsRequest.Change -> PresetSettingsOutcome.Success(MODIFIED, emptyList())
                else -> PresetSettingsOutcome.Success(STANDARD, emptyList())
            }
        }
        var changes = 0
        val tabs = PresetSettingsTabs(editor, FakePresetManager(), NO_FLUSH_UPDATES, repository, scope, onConfigChange = { changes++ })
        runSuspend { tabs.refresh() }
        assertEquals(0, changes)

        tabs.request(PresetKind.PRINT, SettingsRequest.Change("layer_height", "0"))
        assertEquals(1, changes)
    }

    @Test
    fun `a change updates the fields, keeps its notices, discards the sliced G-code, and relabels the process list`() {
        val repository = FakeRepository(READY.copy(result = RESULT))
        val notice = dialog("too_small_layer_height", question = false)
        val presets = FakePresetManager()
        val editor = FakeEditor { call ->
            when (call.request) {
                is SettingsRequest.Change -> PresetSettingsOutcome.Success(MODIFIED, listOf(notice))
                else -> PresetSettingsOutcome.Success(STANDARD, emptyList())
            }
        }
        val tabs = PresetSettingsTabs(editor, presets, NO_FLUSH_UPDATES, repository, scope)
        runSuspend { tabs.refresh() }
        presets.described = 0

        tabs.request(PresetKind.PRINT, SettingsRequest.Change("layer_height", "0"))

        val state = repository.state.value
        assertEquals(MODIFIED, state.settingsTabs.getValue(PresetKind.PRINT).settings)
        assertEquals(listOf(notice), state.settingsTabs.getValue(PresetKind.PRINT).notices)
        assertNull(state.result)
        assertEquals(1, presets.described)
        assertEquals("* process", state.presets?.processes?.single()?.label)

        tabs.dismissNotice(PresetKind.PRINT)

        assertTrue(repository.state.value.settingsTabs.getValue(PresetKind.PRINT).notices.isEmpty())
    }

    @Test
    fun `the G-code the editor was closed with is written on the page the tab shows`() {
        val repository = FakeRepository(READY.copy(result = RESULT))
        val editor = FakeEditor { call ->
            when (call.request) {
                is SettingsRequest.EditCustomGcode -> PresetSettingsOutcome.Success(MODIFIED, emptyList())
                else -> PresetSettingsOutcome.Success(STANDARD, emptyList())
            }
        }
        val tabs = PresetSettingsTabs(editor, FakePresetManager(), NO_FLUSH_UPDATES, repository, scope)
        runSuspend { tabs.refresh() }

        val edited = SettingsRequest.EditCustomGcode("process_change_extrusion_role_gcode", "G4 P0\n")
        tabs.request(PresetKind.PRINT, edited)

        val call = editor.requests.last()
        assertEquals(edited, call.request)
        assertEquals(STRENGTH, call.page)
        assertEquals(MODIFIED, repository.state.value.settingsTabs.getValue(PresetKind.PRINT).settings)
        // Another value of the same preset slices differently.
        assertNull(repository.state.value.result)
    }

    @Test
    fun `the ramming dialog's parameters are written on the page the filament tab shows`() {
        val repository = FakeRepository(READY)
        val editor = FakeEditor()
        val tabs = PresetSettingsTabs(editor, FakePresetManager(), NO_FLUSH_UPDATES, repository, scope)
        runSuspend { tabs.refresh() }
        tabs.request(PresetKind.FILAMENT, SettingsRequest.SelectPage(SPEED))

        val written = SettingsRequest.SetRammingParameters("150 80 6.3871 6.77419| 0.05 6.6 0.45 6.8")
        tabs.request(PresetKind.FILAMENT, written)

        val call = editor.requests.last()
        assertEquals(PresetKind.FILAMENT, call.kind)
        assertEquals(written, call.request)
    }

    @Test
    fun `a change of the long retractions works the flushing volumes out again`() {
        val repository = FakeRepository(READY)
        val updates = mutableListOf<Pair<FlushVolumesChange, Int>>()
        val tabs = PresetSettingsTabs(FakeEditor(), FakePresetManager(), { change, index -> updates += change to index }, repository, scope)
        runSuspend { tabs.refresh() }

        tabs.request(PresetKind.PRINT, SettingsRequest.Change("layer_height", "0.1"))
        tabs.request(PresetKind.PRINTER, SettingsRequest.Change("long_retractions_when_cut#0", "1"))
        tabs.request(PresetKind.FILAMENT, SettingsRequest.Change("filament_long_retractions_when_cut", "1"))

        assertEquals(List(2) { FlushVolumesChange.LONG_RETRACTION_CHANGED to -1 }, updates)
    }

    @Test
    fun `a printer setting the process tab depends on describes the process tab again`() {
        val repository = FakeRepository(READY)
        val editor = FakeEditor()
        val tabs = PresetSettingsTabs(editor, FakePresetManager(), NO_FLUSH_UPDATES, repository, scope)
        runSuspend { tabs.refresh() }
        editor.requests.clear()

        tabs.request(PresetKind.PRINTER, SettingsRequest.Change("single_extruder_multi_material", "1"))

        // Tab::on_value_change(): get_tab(TYPE_PRINT)->update().
        assertEquals(listOf(PresetKind.PRINTER, PresetKind.PRINT), editor.requests.map { it.kind })
        assertEquals(SettingsRequest.Describe, editor.requests.last().request)

        editor.requests.clear()
        tabs.request(PresetKind.PRINTER, SettingsRequest.Change("printable_height", "300"))

        assertEquals(listOf(PresetKind.PRINTER), editor.requests.map { it.kind })
    }

    @Test
    fun `the filament slots follow the extruder count of the printer`() {
        val repository = FakeRepository(READY)
        val applied = mutableListOf<PresetsOutcome>()
        val tabs = PresetSettingsTabs(FakeEditor(), FakePresetManager(), NO_FLUSH_UPDATES, repository, scope) {
            PresetsApplier { _, outcome -> applied += outcome }
        }
        runSuspend { tabs.refresh() }

        tabs.request(PresetKind.PRINTER, SettingsRequest.Change("printable_height", "300"))

        assertTrue(applied.isEmpty())

        // Tab::on_value_change(): set_num_filaments(), then
        // Sidebar::on_filament_count_change() rebuilds the filament combo boxes.
        tabs.request(PresetKind.PRINTER, SettingsRequest.Change("extruders_count", "2"))

        assertEquals(1, applied.size)
    }

    @Test
    fun `the bed is built anew after a change of its shape and after a reset of the printer`() {
        val repository = FakeRepository(READY)
        val applied = mutableListOf<PresetsOutcome>()
        val tabs = PresetSettingsTabs(FakeEditor(), FakePresetManager(), NO_FLUSH_UPDATES, repository, scope) {
            PresetsApplier { _, outcome -> applied += outcome }
        }
        runSuspend { tabs.refresh() }

        // Plater::on_config_change(): bed_shape_changed.
        tabs.request(PresetKind.PRINTER, SettingsRequest.Change("bed_exclude_area", "0x0,10x0,10x10"))
        assertEquals(1, applied.size)

        // The saved preset may hold another bed.
        tabs.request(PresetKind.PRINTER, SettingsRequest.Reset(emptyList()))
        assertEquals(2, applied.size)

        // A reset of the process leaves the bed.
        tabs.request(PresetKind.PRINT, SettingsRequest.Reset(emptyList()))
        assertEquals(2, applied.size)
    }

    @Test
    fun `requests carry the page the tab shows, and the page follows the one the engine toggled`() {
        val repository = FakeRepository(READY)
        val editor = FakeEditor { call -> PresetSettingsOutcome.Success(STANDARD.copy(activePage = call.page), emptyList()) }
        val tabs = PresetSettingsTabs(editor, FakePresetManager(), NO_FLUSH_UPDATES, repository, scope)
        runSuspend { tabs.refresh() }

        tabs.request(PresetKind.PRINT, SettingsRequest.SelectPage(SPEED))

        assertEquals(SPEED, repository.state.value.settingsTabs.getValue(PresetKind.PRINT).page)

        tabs.request(PresetKind.PRINT, SettingsRequest.Change("layer_height", "0.1"))

        assertEquals(SPEED, editor.requests.last().page)
    }

    @Test
    fun `the settings of an object are described with its overrides and kept with the object`() {
        val repository = FakeRepository(
            READY.copy(
                objects = listOf(CUBE),
                selectedInstances = setOf(PlateInstanceId(CUBE.mesh)),
                plateSettings = ModelSettings(mapOf("print_sequence" to "by object")),
            ),
        )
        val overridden = ModelSettings(mapOf("layer_height" to "0.3"))
        val editor = FakeEditor {
            PresetSettingsOutcome.Success(STANDARD.copy(kind = PresetKind.OBJECT, modelSettings = listOf(overridden)), emptyList())
        }
        val tabs = PresetSettingsTabs(editor, FakePresetManager(), NO_FLUSH_UPDATES, repository, scope)

        tabs.request(PresetKind.OBJECT, SettingsRequest.Change("layer_height", "0.3"))

        val call = editor.requests.last()
        assertEquals(PresetKind.OBJECT, call.kind)
        // The request carries what the object and the plate override.
        assertEquals(listOf(ModelSettings(mapOf("layer_height" to "0.28"))), call.model.settings)
        assertEquals(ModelSettings(mapOf("print_sequence" to "by object")), call.model.plate)
        // What the engine answered stays with the object, for the next request and for slicing.
        assertEquals(overridden, repository.state.value.objects.single().settings)
    }

    @Test
    fun `the settings of several selected objects are described and kept together`() {
        val other = CUBE.copy(
            instances = listOf(CUBE.instances.first().copy(inspection = CUBE.instances.first().inspection.copy(mesh = ScenePath("/scene/objects/other.mesh")))),
            settings = ModelSettings(mapOf("layer_height" to "0.24")),
        )
        val repository = FakeRepository(
            READY.copy(objects = listOf(CUBE, other), selectedInstances = setOf(PlateInstanceId(CUBE.mesh), PlateInstanceId(other.mesh))),
        )
        val answered = listOf(ModelSettings(mapOf("layer_height" to "0.3")), ModelSettings(mapOf("layer_height" to "0.3")))
        val editor = FakeEditor {
            PresetSettingsOutcome.Success(STANDARD.copy(kind = PresetKind.OBJECT, modelSettings = answered), emptyList())
        }
        val tabs = PresetSettingsTabs(editor, FakePresetManager(), NO_FLUSH_UPDATES, repository, scope)

        tabs.request(PresetKind.OBJECT, SettingsRequest.Change("layer_height", "0.3"))

        // The request carries what each of them overrides, in the plate's order.
        assertEquals(listOf(CUBE.settings, other.settings), editor.requests.last().model.settings)
        assertEquals(answered, repository.state.value.objects.map(PlateObject::settings))
    }

    @Test
    fun `the settings of a part are described on the ones of its object and kept with the part`() {
        val part = ObjectPart(
            shape = "Cylinder",
            type = VolumeType.MODIFIER,
            mesh = ScenePath("/scene/objects/part.mesh"),
            placement = Transform3(List(16) { if (it % 5 == 0) 1.0 else 0.0 }),
            settings = ModelSettings(mapOf("wall_loops" to "7")),
        )
        val owner = CUBE.copy(parts = listOf(part))
        val repository = FakeRepository(
            READY.copy(
                objects = listOf(owner),
                selectedInstances = setOf(PlateInstanceId(owner.mesh)),
                selectedPart = ObjectPartId(owner.mesh, 1),
            ),
        )
        val answered = ModelSettings(mapOf("wall_loops" to "9"))
        val editor = FakeEditor {
            PresetSettingsOutcome.Success(STANDARD.copy(kind = PresetKind.PART, modelSettings = listOf(answered)), emptyList())
        }
        val tabs = PresetSettingsTabs(editor, FakePresetManager(), NO_FLUSH_UPDATES, repository, scope)

        tabs.request(PresetKind.PART, SettingsRequest.Change("wall_loops", "9"))

        val call = editor.requests.last()
        assertEquals(PresetKind.PART, call.kind)
        // TabPrintPart: the part's own overrides sit on the ones of its object.
        assertEquals(listOf(part.settings), call.model.settings)
        assertEquals(owner.settings, call.model.parent)
        // The answer stays with the part, so slicing prints with it.
        assertEquals(answered, repository.state.value.objects.single().parts.single().settings)
    }

    @Test
    fun `the settings of a height range are described on the ones of its object and kept with the range`() {
        val range = LayerRange(0.0, 2.0)
        val owner = CUBE.copy(layerRanges = listOf(range), settings = ModelSettings(mapOf("layer_height" to "0.28")))
        val repository = FakeRepository(
            READY.copy(
                objects = listOf(owner),
                selectedInstances = setOf(PlateInstanceId(owner.mesh)),
                selectedRange = LayerRangeId(owner.mesh, 0),
            ),
        )
        // TabPrintLayer::notify_changed(): the range is given a layer height of its own.
        val answered = ModelSettings(mapOf("layer_height" to "0.28"))
        val editor = FakeEditor {
            PresetSettingsOutcome.Success(STANDARD.copy(kind = PresetKind.LAYER, modelSettings = listOf(answered)), emptyList())
        }
        val tabs = PresetSettingsTabs(editor, FakePresetManager(), NO_FLUSH_UPDATES, repository, scope)

        tabs.request(PresetKind.LAYER, SettingsRequest.Describe)

        val call = editor.requests.last()
        assertEquals(PresetKind.LAYER, call.kind)
        assertEquals(listOf(range.settings), call.model.settings)
        assertEquals(owner.settings, call.model.parent)
        assertEquals(answered, repository.state.value.objects.single().layerRanges.single().settings)
    }

    @Test
    fun `the height ranges selected together are described and kept together`() {
        val ranges = listOf(LayerRange(0.0, 2.0), LayerRange(2.0, 4.0, ModelSettings(mapOf("wall_loops" to "3"))), LayerRange(4.0, 6.0))
        val owner = CUBE.copy(layerRanges = ranges)
        val repository = FakeRepository(
            READY.copy(
                objects = listOf(owner),
                selectedInstances = setOf(PlateInstanceId(owner.mesh)),
                selectedRange = LayerRangeId(owner.mesh, 2),
                selectedRangeGroup = setOf(LayerRangeId(owner.mesh, 1), LayerRangeId(owner.mesh, 2)),
            ),
        )
        // TabPrintLayer edits the model configs of every selected range.
        val answered = listOf(ModelSettings(mapOf("wall_loops" to "4")), ModelSettings(mapOf("wall_loops" to "4")))
        val editor = FakeEditor {
            PresetSettingsOutcome.Success(STANDARD.copy(kind = PresetKind.LAYER, modelSettings = answered), emptyList())
        }
        val tabs = PresetSettingsTabs(editor, FakePresetManager(), NO_FLUSH_UPDATES, repository, scope)

        tabs.request(PresetKind.LAYER, SettingsRequest.Describe)

        assertEquals(listOf(ranges[1].settings, ranges[2].settings), editor.requests.last().model.settings)
        assertEquals(listOf(ranges[0].settings) + answered, repository.state.value.objects.single().layerRanges.map { it.settings })
    }

    @Test
    fun `a request of the Parameter Table stays on its row's item, a question it asks included, whatever is selected`() {
        val other = CUBE.copy(
            instances = listOf(CUBE.instances.first().copy(inspection = CUBE.instances.first().inspection.copy(mesh = ScenePath("/scene/objects/other.mesh")))),
            settings = ModelSettings(mapOf("layer_height" to "0.24")),
        )
        val repository = FakeRepository(READY.copy(objects = listOf(CUBE, other), selectedInstances = setOf(PlateInstanceId(other.mesh))))
        val spiral = dialog("spiral_mode", question = true)
        val answered = ModelSettings(mapOf("layer_height" to "0.3"))
        val editor = FakeEditor { call ->
            if ("spiral_mode" !in call.answers) {
                PresetSettingsOutcome.Question(spiral, loadsSelection = false)
            } else {
                PresetSettingsOutcome.Success(STANDARD.copy(kind = PresetKind.OBJECT, modelSettings = listOf(answered)), emptyList())
            }
        }
        val tabs = PresetSettingsTabs(editor, FakePresetManager(), NO_FLUSH_UPDATES, repository, scope)

        tabs.request(SettingsItem.Object(CUBE.mesh), SettingsRequest.Change("layer_height", "0.3"))
        tabs.answer(PresetKind.OBJECT, yes = true)

        // Both runs carried the row's object, not the selected one, and the answer stays with it.
        assertEquals(List(2) { listOf(CUBE.settings) }, editor.requests.map { it.model.settings })
        assertEquals(listOf(answered, other.settings), repository.state.value.objects.map(PlateObject::settings))
    }

    @Test
    fun `the Parameter Table writes only what differs from what a row takes, and refuses a density over 100 percent`() {
        val repository = FakeRepository(
            READY.copy(objects = listOf(CUBE), settingsTabs = READY.settingsTabs + (PresetKind.PRINT to SettingsTabState(PresetKind.PRINT, settings = STANDARD))),
        )
        val editor = FakeEditor()
        val tabs = PresetSettingsTabs(editor, FakePresetManager(), NO_FLUSH_UPDATES, repository, scope)
        val table = ObjectTableUseCase(
            repository,
            tabs,
            SelectPlateObjectUseCase(repository),
            SelectObjectPartUseCase(repository),
            SetPlateObjectPrintableUseCase(repository),
            RenamePlateItemUseCase(repository),
        )
        val cube = SettingsItem.Object(CUBE.mesh)

        // The object overrides 0.28 over the process preset's 0.2.
        table.change(cube, ObjectTableColumn.LAYER_HEIGHT, "0.28")
        table.change(cube, ObjectTableColumn.FILL_DENSITY, "150")
        assertTrue(editor.requests.isEmpty())

        table.change(cube, ObjectTableColumn.LAYER_HEIGHT, "0.20")
        table.change(cube, ObjectTableColumn.LAYER_HEIGHT, "0.3")
        table.reset(cube, ObjectTableColumn.LAYER_HEIGHT)

        assertEquals(
            listOf(SettingsRequest.Reset(listOf("layer_height")), SettingsRequest.Change("layer_height", "0.3"), SettingsRequest.Reset(listOf("layer_height"))),
            editor.requests.map { it.request },
        )
        assertTrue(editor.requests.all { it.kind == PresetKind.OBJECT })
    }

    @Test
    fun `an object's filament in the Parameter Table takes back the same filament its volumes had of their own, as one step of Undo`() {
        val part = ObjectPart("Cube", VolumeType.PART, ScenePath("/scene/objects/part.mesh"), Transform3.IDENTITY, ModelSettings(mapOf("extruder" to "2")))
        val modifier = part.copy(type = VolumeType.MODIFIER, mesh = ScenePath("/scene/objects/modifier.mesh"), settings = ModelSettings(mapOf("extruder" to "3")))
        val negative = part.copy(type = VolumeType.NEGATIVE, mesh = ScenePath("/scene/objects/negative.mesh"))
        val owner = CUBE.copy(parts = listOf(part, modifier, negative))
        val filaments = PROFILES.copy(filaments = List(3) { ProfileId("filament") })
        val repository = FakeRepository(READY.copy(presets = PRESETS.copy(selection = filaments), objects = listOf(owner)))
        val table = ObjectTableUseCase(
            repository,
            PresetSettingsTabs(FakeEditor(), FakePresetManager(), NO_FLUSH_UPDATES, repository, scope),
            SelectPlateObjectUseCase(repository),
            SelectObjectPartUseCase(repository),
            SetPlateObjectPrintableUseCase(repository),
            RenamePlateItemUseCase(repository),
        )

        table.setFilament(SettingsItem.Object(owner.mesh), 2)

        val changed = repository.state.value.objects.single()
        assertEquals("2", changed.settings.values["extruder"])
        // update_volume_values_from_object() goes by the value, whatever the volume's type.
        assertEquals(listOf(null, "3", null), changed.parts.map { it.settings.values["extruder"] })
        assertEquals(1, repository.state.value.history.undo.size)

        // A negative volume takes no filament, and the plate has no fourth one.
        table.setFilament(SettingsItem.Volume(ObjectPartId(owner.mesh, 3)), 1)
        table.setFilament(SettingsItem.Volume(ObjectPartId(owner.mesh, 1)), 4)
        assertEquals(1, repository.state.value.history.undo.size)
    }

    @Test
    fun `the settings of the plate are kept with the plate, and an object needs one selected`() {
        val repository = FakeRepository(READY.copy(objects = listOf(CUBE)))
        val overridden = ModelSettings(mapOf("curr_bed_type" to "Textured PEI Plate"))
        val editor = FakeEditor {
            PresetSettingsOutcome.Success(STANDARD.copy(kind = PresetKind.PLATE, modelSettings = listOf(overridden)), emptyList())
        }
        val tabs = PresetSettingsTabs(editor, FakePresetManager(), NO_FLUSH_UPDATES, repository, scope)

        // No object is selected, so only the plate's settings are edited.
        tabs.request(PresetKind.OBJECT, SettingsRequest.Describe)

        assertTrue(editor.requests.isEmpty())

        tabs.request(PresetKind.PLATE, SettingsRequest.Change("curr_bed_type", "Textured PEI Plate"))

        assertEquals(overridden, repository.state.value.plateSettings)
        // The settings of the objects are left as they are.
        assertEquals(CUBE.settings, repository.state.value.objects.single().settings)
    }

    @Test
    fun `Customize of a filament sequence in the plate's tab opens the plate settings with the sequences alone`() {
        val repository = FakeRepository(READY.copy(objects = listOf(CUBE)))
        val customized = ModelSettings(mapOf("first_layer_print_sequence" to "1"))
        val editor = FakeEditor {
            PresetSettingsOutcome.Success(STANDARD.copy(kind = PresetKind.PLATE, modelSettings = listOf(customized)), emptyList())
        }
        val tabs = PresetSettingsTabs(editor, FakePresetManager(), NO_FLUSH_UPDATES, repository, scope)

        tabs.request(PresetKind.PLATE, SettingsRequest.Change("first_layer_sequence_choice", "Auto"))
        assertFalse(repository.state.value.layerSequencePrompt)

        tabs.request(PresetKind.PLATE, SettingsRequest.Change("first_layer_sequence_choice", "Customize"))
        assertTrue(repository.state.value.layerSequencePrompt)

        // The dialog's Cancel, like its OK, closes it.
        SetPlateSettingsUseCase(repository).cancel()
        assertFalse(repository.state.value.layerSequencePrompt)
    }

    @Test
    fun `a change of the mode in one tab describes the other open tabs anew`() {
        val repository = FakeRepository(READY)
        val editor = FakeEditor()
        val tabs = PresetSettingsTabs(editor, FakePresetManager(), NO_FLUSH_UPDATES, repository, scope)
        tabs.request(PresetKind.PRINT, SettingsRequest.Describe)
        tabs.request(PresetKind.FILAMENT, SettingsRequest.Describe)
        editor.requests.clear()

        tabs.request(PresetKind.FILAMENT, SettingsRequest.SetMode(SettingsMode.SIMPLE))

        assertEquals(listOf(PresetKind.FILAMENT, PresetKind.PRINT), editor.requests.map { it.kind })
    }

    @Test
    fun `only the tabs the app has opened are described again`() {
        val repository = FakeRepository(READY)
        val editor = FakeEditor()
        val tabs = PresetSettingsTabs(editor, FakePresetManager(), NO_FLUSH_UPDATES, repository, scope)

        runSuspend { tabs.refresh() }

        assertEquals(listOf(PresetKind.PRINT), editor.requests.map { it.kind })

        tabs.request(PresetKind.FILAMENT, SettingsRequest.Describe)
        runSuspend { tabs.refresh() }

        assertEquals(listOf(PresetKind.PRINT, PresetKind.FILAMENT, PresetKind.PRINT, PresetKind.FILAMENT), editor.requests.map { it.kind })
    }

    @Test
    fun `a question stops the change until it is answered, and the change runs again with the answers so far`() {
        val repository = FakeRepository(READY)
        val spiral = dialog("spiral_mode", question = true)
        val prime = dialog("prime_tower", question = true)
        val editor = FakeEditor { call ->
            when {
                call.request is SettingsRequest.Describe -> PresetSettingsOutcome.Success(STANDARD, emptyList())
                "spiral_mode" !in call.answers -> PresetSettingsOutcome.Question(spiral, loadsSelection = false)
                "prime_tower" !in call.answers -> PresetSettingsOutcome.Question(prime, loadsSelection = false)
                else -> PresetSettingsOutcome.Success(MODIFIED, emptyList())
            }
        }
        val tabs = PresetSettingsTabs(editor, FakePresetManager(), NO_FLUSH_UPDATES, repository, scope)
        runSuspend { tabs.refresh() }

        tabs.request(PresetKind.PRINT, SettingsRequest.Change("spiral_mode", "1"))

        val tab = { repository.state.value.settingsTabs.getValue(PresetKind.PRINT) }
        assertEquals(spiral, tab().question?.dialog)
        assertEquals(STANDARD, tab().settings)
        // Nothing else runs while the question waits.
        tabs.request(PresetKind.PRINT, SettingsRequest.Change("wall_loops", "3"))
        assertEquals(2, editor.requests.size)

        tabs.answer(PresetKind.PRINT, yes = true)

        assertEquals(prime, tab().question?.dialog)

        tabs.answer(PresetKind.PRINT, yes = false)

        assertNull(tab().question)
        assertEquals(MODIFIED, tab().settings)
        assertEquals(SettingsRequest.Change("spiral_mode", "1"), editor.requests.last().request)
        assertEquals(mapOf("spiral_mode" to true, "prime_tower" to false), editor.requests.last().answers)
    }

    @Test
    fun `a question from loading the selection is answered by describing the tab again`() {
        val repository = FakeRepository(READY)
        val loaded = dialog("fuzzy_skin_mode", question = true)
        val editor = FakeEditor { call ->
            if (call.request is SettingsRequest.Delete) {
                PresetSettingsOutcome.Question(loaded, loadsSelection = true)
            } else if ("fuzzy_skin_mode" !in call.answers) {
                PresetSettingsOutcome.Success(STANDARD, emptyList())
            } else {
                PresetSettingsOutcome.Success(STANDARD.copy(preset = "parent"), emptyList())
            }
        }
        val tabs = PresetSettingsTabs(editor, FakePresetManager(), NO_FLUSH_UPDATES, repository, scope)
        runSuspend { tabs.refresh() }

        tabs.request(PresetKind.PRINT, SettingsRequest.Delete)
        tabs.answer(PresetKind.PRINT, yes = true)

        assertEquals(SettingsRequest.Describe, editor.requests.last().request)
        assertEquals(mapOf("fuzzy_skin_mode" to true), editor.requests.last().answers)
        assertEquals("parent", repository.state.value.settingsTabs.getValue(PresetKind.PRINT).settings?.preset)
    }

    @Test
    fun `nothing is requested while the plate is busy, and a failure is reported`() {
        val repository = FakeRepository(READY.copy(changingPresets = true))
        val editor = FakeEditor { PresetSettingsOutcome.Failure("broken") }
        val tabs = PresetSettingsTabs(editor, FakePresetManager(), NO_FLUSH_UPDATES, repository, scope)

        tabs.request(PresetKind.PRINT, SettingsRequest.Change("layer_height", "0.1"))

        assertTrue(editor.requests.isEmpty())

        runSuspend { tabs.refresh() }

        val tab = repository.state.value.settingsTabs.getValue(PresetKind.PRINT)
        assertEquals("broken", tab.problem)
        assertNull(tab.settings)
    }

    private fun <T> runSuspend(block: suspend () -> T): T {
        var completion: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) {
                completion = result
            }
        })
        return checkNotNull(completion).getOrThrow()
    }

    private class FakeRepository(initial: PlateState) : PlateRepository {
        private val flow = MutableStateFlow(initial)
        override val state: StateFlow<PlateState> = flow
        override fun update(transform: (PlateState) -> PlateState) = flow.update(transform)
    }

    /** One request the tab made of the engine. */
    private data class EditorRequest(
        val kind: PresetKind,
        val page: String,
        val request: SettingsRequest,
        val answers: Map<String, Boolean>,
        /** The overrides of an object or of the plate the request carried. */
        val model: ModelSettingsRequest = ModelSettingsRequest(),
    )

    private class FakeEditor(
        private val answer: (EditorRequest) -> PresetSettingsOutcome = { PresetSettingsOutcome.Success(STANDARD, emptyList()) },
    ) : PresetSettingsEditor {
        val requests = mutableListOf<EditorRequest>()

        private fun run(
            kind: PresetKind,
            page: String,
            request: SettingsRequest,
            answers: Map<String, Boolean>,
            model: ModelSettingsRequest = ModelSettingsRequest(),
        ): PresetSettingsOutcome {
            val call = EditorRequest(kind, page, request, answers, model)
            requests += call
            return answer(call)
        }

        override suspend fun settingsTab(kind: PresetKind) = SettingsTabOutcome.Success(TAB.copy(kind = kind))

        override suspend fun settings(kind: PresetKind, page: String, answers: Map<String, Boolean>, model: ModelSettingsRequest) =
            run(kind, page, SettingsRequest.Describe, answers, model)

        override suspend fun changeSetting(
            kind: PresetKind,
            page: String,
            id: String,
            text: String,
            answers: Map<String, Boolean>,
            model: ModelSettingsRequest,
        ) = run(kind, page, SettingsRequest.Change(id, text), answers, model)

        override suspend fun resetSettings(
            kind: PresetKind,
            page: String,
            ids: List<String>,
            answers: Map<String, Boolean>,
            model: ModelSettingsRequest,
        ) = run(kind, page, SettingsRequest.Reset(ids), answers, model)

        override suspend fun pasteModelSettings(clipboard: ModelSettings, target: ModelSettings, parent: ModelSettings?) =
            ModelSettingsOutcome.Failure("not used")

        override suspend fun defaultLayerConfig(objectSettings: ModelSettings) = ModelSettingsOutcome.Failure("not used")

        override suspend fun setSettingOverride(kind: PresetKind, page: String, id: String, enabled: Boolean, answers: Map<String, Boolean>) =
            run(kind, page, SettingsRequest.SetOverride(id, enabled), answers)

        override suspend fun setCompatiblePresets(kind: PresetKind, page: String, key: String, presets: List<String>, answers: Map<String, Boolean>) =
            run(kind, page, SettingsRequest.SetCompatible(key, presets), answers)

        override suspend fun compatiblePresetChoices(kind: PresetKind, key: String) = PresetNamesOutcome.Failure("not used")

        override suspend fun searchCatalog() = SearchCatalogOutcome.Failure("no catalogue")

        override suspend fun printerConnection() = PrinterConnectionOutcome.Failure("no printer")

        override suspend fun savePrinterConnection(settings: ModelSettings, name: String) = PresetSettingsOutcome.Failure("no printer")

        override suspend fun importPresets(paths: List<String>, answers: Map<String, ConfigOverwriteAnswer>) =
            ConfigTransferOutcome.Failure("no import")

        override suspend fun configExportOptions(kind: ConfigExportKind) = ConfigExportOptionsOutcome.Failure("no export")

        override suspend fun exportConfigs(kind: ConfigExportKind, names: List<String>, directory: String) =
            ConfigTransferOutcome.Failure("no export")

        override suspend fun presetBundles() = PresetBundlesOutcome.Failure("no bundles")

        override suspend fun deletePresetBundle(id: String) = PresetBundlesOutcome.Failure("no bundles")

        override suspend fun comparePresets(left: ComparedPresets, right: ComparedPresets, showAll: Boolean) =
            PresetComparisonOutcome.Failure("no comparison")

        override suspend fun gcodePlaceholders(kind: PresetKind, key: String) = GcodePlaceholdersOutcome.Failure("no placeholders")

        override suspend fun gcodePlaceholder(key: String, presets: Boolean) = GcodePlaceholderInfo(emptyList(), "", emptyList(), undefined = true)

        override suspend fun editCustomGcode(kind: PresetKind, page: String, key: String, value: String, answers: Map<String, Boolean>) =
            run(kind, page, SettingsRequest.EditCustomGcode(key, value), answers)

        override suspend fun setRammingParameters(kind: PresetKind, page: String, parameters: String, answers: Map<String, Boolean>) =
            run(kind, page, SettingsRequest.SetRammingParameters(parameters), answers)

        override suspend fun bedShape() = BedShapeOutcome.Failure("no bed shape")

        override suspend fun setBedShape(shape: BedShape, answers: Map<String, Boolean>) =
            PresetSettingsOutcome.Failure("no bed shape")

        override suspend fun setSettingsMode(kind: PresetKind, mode: SettingsMode, model: ModelSettingsRequest) =
            run(kind, "", SettingsRequest.SetMode(mode), emptyMap(), model)

        override suspend fun setSettingsVariant(
            kind: PresetKind,
            page: String,
            variant: Int,
            answers: Map<String, Boolean>,
            model: ModelSettingsRequest,
        ) = run(kind, page, SettingsRequest.SetVariant(variant), answers, model)

        override suspend fun settingTooltip(kind: PresetKind, id: String) = listOf(OrcaText(id))

        override suspend fun checkPresetName(kind: PresetKind, name: String) = PresetNameOutcome.Failure("not used")

        override suspend fun savePreset(kind: PresetKind, name: String, detach: Boolean, saveToProject: Boolean) =
            run(kind, "", SettingsRequest.Save(PresetSave(name, saveToProject, detach)), emptyMap())

        override suspend fun deletePreset(kind: PresetKind, answers: Map<String, Boolean>) = run(kind, "", SettingsRequest.Delete, answers)
    }

    private class FakePresetManager : PresetManager {
        var described = 0

        override suspend fun presets(): PresetsOutcome {
            described++
            return PresetsOutcome.Success(PRESETS.copy(processes = listOf(PresetListItem("process", "* process", PresetGroup.SYSTEM, "", selected = true))))
        }

        override suspend fun selectPreset(choice: PresetChoice, answers: List<PresetChangeAction>) = PresetsOutcome.Failure("not used")

        override suspend fun transferPresetOptions(kind: PresetKind, from: String, to: String, options: List<String>) =
            PresetsOutcome.Failure("not used")

        override suspend fun createFilamentOptions(type: String, baseFilament: String) =
            CreateFilamentOptionsOutcome.Failure("not used")

        override suspend fun createFilament(request: CreateFilamentRequest, answers: Map<String, Boolean>) =
            PresetCreationOutcome.Failure("not used")

        override suspend fun customFilaments() = CustomFilamentsOutcome.Failure("not used")

        override suspend fun filamentPresets(filamentId: String) = FilamentPresetsOutcome.Failure("not used")

        override suspend fun deleteFilamentPreset(preset: String, answers: Map<String, Boolean>) =
            PresetCreationOutcome.Failure("not used")

        override suspend fun createPrinterOptions(request: CreatePrinterRequest) = CreatePrinterOptionsOutcome.Failure("not used")

        override suspend fun checkPrinterPage(request: CreatePrinterRequest, answers: Map<String, Boolean>) =
            PresetCreationOutcome.Failure("not used")

        override suspend fun createPrinter(request: CreatePrinterRequest, answers: Map<String, Boolean>) =
            PresetCreationOutcome.Failure("not used")

        override suspend fun addFilament(color: String?) = PresetsOutcome.Failure("not used")

        override suspend fun removeFilament(index: Int) = PresetsOutcome.Failure("not used")

        override suspend fun selectFilament(index: Int, name: ProfileId, action: PresetChangeAction) = PresetsOutcome.Failure("not used")

        override suspend fun setFilamentColor(index: Int, color: String) = PresetsOutcome.Failure("not used")

        override suspend fun setupPrinters() = SetupPrintersOutcome.Failure("not used")

        override suspend fun setupFilaments(models: List<String>) = SetupFilamentsOutcome.Failure("not used")

        override suspend fun applySetup(models: List<String>, filaments: List<String>, keepChanges: Boolean) = PresetsOutcome.Failure("not used")

        override suspend fun applyDefaultSetup() = PresetsOutcome.Failure("not used")
    }

    private companion object {
        const val STRENGTH = "Strength"
        const val SPEED = "Speed"
        val PROFILES = SlicingProfileSelection(ProfileId("printer"), ProfileId("filament"), ProfileId("process"))
        val PRESETS = Presets(
            selection = PROFILES,
            setupRequired = false,
            printers = emptyList(),
            filaments = emptyList(),
            processes = listOf(PresetListItem("process", "process", PresetGroup.SYSTEM, "", selected = true)),
            nozzleDiameters = listOf("0.4"),
            nozzleDiameter = "0.4",
        )
        val READY = PlateState(engine = EngineState(EngineAvailability.READY), presets = PRESETS)
        val RESULT = PlateSliceResult(
            jobId = SliceJobId("job"),
            objects = emptyList(),
            gcode = OutputPath("/gcode/cube.gcode"),
            statistics = SliceStatistics(100, 731, 1209.0),
        )
        val TAB = SettingsTab(PresetKind.PRINT, definitions = emptyMap())
        val CUBE = PlateObject.CalibrationCube(
            instances = listOf(
                PlateInstance(
                    ModelInspection(
                facetCount = 12,
                dimensions = ModelDimensions(20.0, 20.0, 20.0),
                boxCenter = Vector3(0.0, 0.0, 10.0),
                mesh = ScenePath("/scene/objects/cube.mesh"),
                placement = Transform3(List(16) { if (it % 5 == 0) 1.0 else 0.0 }),
                fit = BuildVolumeFit.INSIDE,
                boundingSphere = BoundingSphere(Vector3(0.0, 0.0, 10.0), 17.32),
                rotationDegrees = Vector3(0.0, 0.0, 0.0),
                        unscaledDimensions = ModelDimensions(20.0, 20.0, 20.0),
                    ),
                ),
            ),
            settings = ModelSettings(mapOf("layer_height" to "0.28")),
        )
        val PAGES = listOf(
            SettingsPage(
                title = STRENGTH,
                label = listOf(OrcaText(STRENGTH)),
                icon = "custom-gcode_strength",
                groups = listOf(SettingsGroup("Walls", "param_wall", listOf(SettingsLine(options = listOf(SettingsLineOption("wall_loops", "wall_loops")))))),
            ),
            SettingsPage(title = SPEED, label = listOf(OrcaText(SPEED)), icon = "custom-gcode_speed", groups = emptyList()),
        )
        val STANDARD = PresetSettings(
            kind = PresetKind.PRINT,
            preset = "process",
            label = "process",
            dirty = false,
            isDefault = false,
            isSystem = true,
            hasParent = true,
            canDelete = false,
            mode = SettingsMode.SIMPLE,
            pages = PAGES,
            activePage = STRENGTH,
            settings = listOf(SettingState("layer_height", "layer_height", "0.2", modified = false, system = true, enabled = true, visible = true)),
            saveName = "process",
            saveNameCopySuffix = true,
        )
        val MODIFIED = STANDARD.copy(
            label = "* process",
            dirty = true,
            settings = listOf(SettingState("layer_height", "layer_height", "0.08", modified = true, system = false, enabled = true, visible = true)),
        )

        fun dialog(id: String, question: Boolean) =
            SettingsDialog(id, DialogIcon.WARNING, emptyList(), listOf(OrcaText(id)), question, yes = null, no = null)
    }
}
