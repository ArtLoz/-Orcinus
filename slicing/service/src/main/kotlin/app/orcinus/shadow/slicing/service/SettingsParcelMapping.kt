package app.orcinus.shadow.slicing.service

import app.orcinus.shadow.core.model.AppConfigOutcome
import app.orcinus.shadow.core.model.BedShape
import app.orcinus.shadow.core.model.BedShapeKind
import app.orcinus.shadow.core.model.BedShapeOutcome
import app.orcinus.shadow.core.model.ConfigExportEntry
import app.orcinus.shadow.core.model.ConfigExportOptionsOutcome
import app.orcinus.shadow.core.model.ConfigTransferOutcome
import app.orcinus.shadow.core.model.CreateFilamentOptionsOutcome
import app.orcinus.shadow.core.model.CreatePrinterOptionsOutcome
import app.orcinus.shadow.core.model.CreatePrinterRequest
import app.orcinus.shadow.core.model.CustomFilament
import app.orcinus.shadow.core.model.CustomFilamentsOutcome
import app.orcinus.shadow.core.model.DialogIcon
import app.orcinus.shadow.core.model.FilamentPresetChoice
import app.orcinus.shadow.core.model.FilamentPresetList
import app.orcinus.shadow.core.model.FilamentPresetsOutcome
import app.orcinus.shadow.core.model.GcodePlaceholder
import app.orcinus.shadow.core.model.GcodePlaceholderInfo
import app.orcinus.shadow.core.model.GcodePlaceholderType
import app.orcinus.shadow.core.model.GcodePlaceholders
import app.orcinus.shadow.core.model.GcodePlaceholdersOutcome
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.ModelSettingsRequest
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.core.model.PresetChange
import app.orcinus.shadow.core.model.PresetComparisonOutcome
import app.orcinus.shadow.core.model.PresetCreationOutcome
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.PresetKindComparison
import app.orcinus.shadow.core.model.PresetNameCheck
import app.orcinus.shadow.core.model.PresetNameOutcome
import app.orcinus.shadow.core.model.PresetNameValidation
import app.orcinus.shadow.core.model.PresetNamesOutcome
import app.orcinus.shadow.core.model.PresetSettings
import app.orcinus.shadow.core.model.PresetSettingsOutcome
import app.orcinus.shadow.core.model.PrinterConnection
import app.orcinus.shadow.core.model.PrinterConnectionOutcome
import app.orcinus.shadow.core.model.SearchCatalogOutcome
import app.orcinus.shadow.core.model.SearchOption
import app.orcinus.shadow.core.model.SettingChoice
import app.orcinus.shadow.core.model.SettingControl
import app.orcinus.shadow.core.model.SettingDefinition
import app.orcinus.shadow.core.model.SettingState
import app.orcinus.shadow.core.model.SettingType
import app.orcinus.shadow.core.model.SettingWidget
import app.orcinus.shadow.core.model.SettingsDialog
import app.orcinus.shadow.core.model.SettingsGroup
import app.orcinus.shadow.core.model.SettingsLine
import app.orcinus.shadow.core.model.SettingsLineOption
import app.orcinus.shadow.core.model.SettingsMode
import app.orcinus.shadow.core.model.SettingsPage
import app.orcinus.shadow.core.model.SettingsTab
import app.orcinus.shadow.core.model.SettingsTabOutcome

// The settings tabs' models as parcels, and back.

internal fun SettingsTabOutcome.toParcel() = SettingsTabParcel().also {
    when (this) {
        is SettingsTabOutcome.Failure -> it.error = message
        is SettingsTabOutcome.Success -> {
            it.kind = tab.kind.name
            it.definitions = tab.definitions.values.map(SettingDefinition::toParcel).toTypedArray()
        }
    }
}

internal fun SettingsTabParcel.toSettingsTabOutcome(): SettingsTabOutcome {
    error?.let { return SettingsTabOutcome.Failure(it) }
    return SettingsTabOutcome.Success(
        SettingsTab(
            kind = PresetKind.valueOf(checkNotNull(kind)),
            definitions = definitions.orEmpty().map { it.toDefinition() }.associateBy(SettingDefinition::key),
        ),
    )
}

private fun SettingsPage.toParcel() = SettingsPageParcel().also {
    it.title = title
    it.label = label.toParcels()
    it.icon = icon
    it.groups = groups.map { group ->
        SettingsGroupParcel().also { parcel ->
            parcel.title = group.title
            parcel.icon = group.icon
            parcel.lines = group.lines.map(SettingsLine::toParcel).toTypedArray()
        }
    }.toTypedArray()
}

private fun SettingsPageParcel.toPage() = SettingsPage(
    title = title,
    label = label.toTexts(),
    icon = icon,
    groups = groups.map { group -> SettingsGroup(group.title, group.icon, group.lines.map { it.toLine() }) },
)

private fun SettingsLine.toParcel() = SettingsLineParcel().also {
    it.label = label
    it.tooltip = tooltip
    it.options = options.map { option ->
        SettingsLineOptionParcel().also { parcel ->
            parcel.id = option.id
            parcel.key = option.key
            parcel.index = option.index
            parcel.label = option.label
            parcel.fullWidth = option.fullWidth
            parcel.isCode = option.isCode
            parcel.multiline = option.multiline
            parcel.height = option.height
            parcel.editCustomGcode = option.editCustomGcode
        }
    }.toTypedArray()
    it.separator = separator
    it.widget = widget.name
    it.hasOverride = hasOverride
}

private fun SettingsLineParcel.toLine() = SettingsLine(
    label = label.orEmpty(),
    tooltip = tooltip.orEmpty(),
    separator = separator,
    widget = SettingWidget.valueOf(widget),
    hasOverride = hasOverride,
    options = options.map {
        SettingsLineOption(
            id = it.id,
            key = it.key,
            index = it.index,
            label = it.label,
            fullWidth = it.fullWidth,
            isCode = it.isCode,
            multiline = it.multiline,
            height = it.height,
            editCustomGcode = it.editCustomGcode,
        )
    },
)

private fun SettingDefinition.toParcel() = SettingDefinitionParcel().also {
    it.key = key
    it.type = type.name
    it.label = label
    it.sidetext = sidetext
    it.category = category
    it.mode = mode.name
    it.control = control.name
    it.enumValues = enumValues.toTypedArray()
    it.enumLabels = enumLabels.toTypedArray()
    it.multiline = multiline
    it.fullWidth = fullWidth
    it.isCode = isCode
    it.height = height
}

private fun SettingDefinitionParcel.toDefinition() = SettingDefinition(
    key = key,
    type = SettingType.valueOf(type),
    label = label,
    sidetext = sidetext,
    category = category.orEmpty(),
    mode = SettingsMode.valueOf(mode),
    control = SettingControl.valueOf(control),
    enumValues = enumValues.toList(),
    enumLabels = enumLabels.toList(),
    multiline = multiline,
    fullWidth = fullWidth,
    isCode = isCode,
    height = height,
)

/** The settings an object or the plate overrides, which travel with a request. */
internal fun ModelSettings.toParcel() = ModelSettingsParcel().also {
    it.keys = values.keys.toTypedArray()
    it.values = values.values.toTypedArray()
}

internal fun ModelSettingsParcel?.toModelSettings(): ModelSettings {
    val parcel = this ?: return ModelSettings()
    val keys = parcel.keys.orEmpty()
    val values = parcel.values.orEmpty()
    return ModelSettings(keys.indices.filter { it < values.size }.associate { keys[it] to values[it] })
}

/** What a request of an object's or the plate's settings carries. */
internal fun modelRequestOf(models: Array<ModelSettingsParcel>?, plate: ModelSettingsParcel?, parent: ModelSettingsParcel?) =
    ModelSettingsRequest(
        settings = models.orEmpty().map { it.toModelSettings() },
        plate = plate.toModelSettings(),
        parent = parent.toModelSettings(),
    )

internal fun PresetSettingsOutcome.toParcel() = PresetSettingsParcel().also {
    when (this) {
        is PresetSettingsOutcome.Failure -> it.error = message
        is PresetSettingsOutcome.Question -> {
            it.question = question.toParcel()
            it.questionLoadsSelection = loadsSelection
        }
        is PresetSettingsOutcome.Success -> {
            it.kind = settings.kind.name
            it.preset = settings.preset
            it.label = settings.label
            it.dirty = settings.dirty
            it.isDefault = settings.isDefault
            it.isSystem = settings.isSystem
            it.hasParent = settings.hasParent
            it.canDelete = settings.canDelete
            it.mode = settings.mode.name
            it.pages = settings.pages.map(SettingsPage::toParcel).toTypedArray()
            it.activePage = settings.activePage
            it.variants = settings.variants.toTypedArray()
            it.variant = settings.variant
            it.settings = settings.settings.map(SettingState::toParcel).toTypedArray()
            it.saveName = settings.saveName
            it.saveNameCopySuffix = settings.saveNameCopySuffix
            it.notices = notices.map(SettingsDialog::toParcel).toTypedArray()
            it.modelSettings = settings.modelSettings?.map(ModelSettings::toParcel)?.toTypedArray()
        }
    }
}

internal fun PresetSettingsParcel.toPresetSettingsOutcome(): PresetSettingsOutcome {
    error?.let { return PresetSettingsOutcome.Failure(it) }
    question?.let { return PresetSettingsOutcome.Question(it.toDialog(), questionLoadsSelection) }
    return PresetSettingsOutcome.Success(
        PresetSettings(
            kind = PresetKind.valueOf(checkNotNull(kind)),
            preset = preset.orEmpty(),
            label = label.orEmpty(),
            dirty = dirty,
            isDefault = isDefault,
            isSystem = isSystem,
            hasParent = hasParent,
            canDelete = canDelete,
            mode = SettingsMode.valueOf(checkNotNull(mode)),
            pages = pages.orEmpty().map { it.toPage() },
            activePage = activePage.orEmpty(),
            variants = variants.orEmpty().toList(),
            variant = variant,
            settings = settings.orEmpty().map { it.toState() },
            saveName = saveName.orEmpty(),
            saveNameCopySuffix = saveNameCopySuffix,
            modelSettings = modelSettings?.map { it.toModelSettings() },
        ),
        notices = notices.orEmpty().map { it.toDialog() },
    )
}

private fun SettingState.toParcel() = SettingStateParcel().also {
    it.id = id
    it.key = key
    it.value = value
    it.modified = modified
    it.system = system
    it.enabled = enabled
    it.visible = visible
    it.choiceValues = choices?.map(SettingChoice::value)?.toTypedArray()
    it.choiceLabels = choices?.map(SettingChoice::label)?.toTypedArray()
    it.nullable = nullable
    it.isNil = isNil
    it.mixed = mixed
    it.overrideEnabled = overrideEnabled
    it.listValues = listValues.toTypedArray()
}

private fun SettingStateParcel.toState() = SettingState(
    id = id,
    key = key,
    value = value,
    modified = modified,
    system = system,
    enabled = enabled,
    visible = visible,
    choices = choiceValues?.let { values -> values.zip(choiceLabels.orEmpty(), ::SettingChoice) },
    nullable = nullable,
    isNil = isNil,
    mixed = mixed,
    overrideEnabled = overrideEnabled,
    listValues = listValues.orEmpty().toList(),
)

internal fun SettingsDialog.toParcel() = SettingsDialogParcel().also {
    it.id = id
    it.icon = icon.name
    it.title = title.toParcels()
    it.text = text.toParcels()
    it.question = question
    it.yes = yes?.toParcel()
    it.no = no?.toParcel()
    it.checkbox = checkbox?.toParcel()
    it.checked = checked
}

internal fun SettingsDialogParcel.toDialog() = SettingsDialog(
    id = id,
    icon = DialogIcon.valueOf(icon),
    title = title.toTexts(),
    text = text.toTexts(),
    question = question,
    yes = yes?.toText(),
    no = no?.toText(),
    checkbox = checkbox?.toText(),
    checked = checked,
)

internal fun List<OrcaText>.toParcels(): Array<OrcaTextParcel> = map(OrcaText::toParcel).toTypedArray()

internal fun Array<OrcaTextParcel>?.toTexts(): List<OrcaText> = orEmpty().map { it.toText() }

private fun OrcaText.toParcel() = OrcaTextParcel().also {
    it.context = context
    it.msgid = msgid
    it.msgidPlural = msgidPlural
    it.count = count
    it.args = args.toTypedArray()
    it.translateArgs = translateArgs
}

private fun OrcaTextParcel.toText() = OrcaText(
    msgid = msgid,
    args = args.toList(),
    translateArgs = translateArgs,
    context = context,
    msgidPlural = msgidPlural,
    count = count,
)

internal fun PresetNameOutcome.toParcel() = PresetNameParcel().also {
    when (this) {
        is PresetNameOutcome.Failure -> it.error = message
        is PresetNameOutcome.Success -> {
            it.check = validation.check.name
            it.info = validation.info.toParcels()
        }
    }
}

internal fun PresetNameParcel.toPresetNameOutcome(): PresetNameOutcome {
    error?.let { return PresetNameOutcome.Failure(it) }
    return PresetNameOutcome.Success(PresetNameValidation(PresetNameCheck.valueOf(checkNotNull(check)), info.toTexts()))
}

internal fun AppConfigOutcome.toParcel() = AppConfigParcel().also {
    when (this) {
        is AppConfigOutcome.Failure -> it.error = message
        is AppConfigOutcome.Success -> {
            it.keys = values.keys.toTypedArray()
            it.values = values.values.toTypedArray()
        }
    }
}

internal fun AppConfigParcel.toAppConfigOutcome(): AppConfigOutcome {
    error?.let { return AppConfigOutcome.Failure(it) }
    return AppConfigOutcome.Success(keys.orEmpty().zip(values.orEmpty()).toMap())
}

/** The recent projects in the values, the most recent first; an error for none. */
internal fun List<String>?.toRecentProjectsParcel() = AppConfigParcel().also {
    if (this == null) it.error = "OrcaSlicer could not read its recent projects" else it.values = toTypedArray()
}

internal fun AppConfigParcel.toRecentProjects(): List<String>? = if (error != null) null else values.orEmpty().toList()

internal fun PresetNamesOutcome.toParcel() = PresetNamesParcel().also {
    when (this) {
        is PresetNamesOutcome.Failure -> it.error = message
        is PresetNamesOutcome.Success -> it.names = names.toTypedArray()
    }
}

internal fun PresetNamesParcel.toPresetNamesOutcome(): PresetNamesOutcome {
    error?.let { return PresetNamesOutcome.Failure(it) }
    return PresetNamesOutcome.Success(names.orEmpty().toList())
}

/** The answers to a change's questions as the service takes them: parallel arrays. */
internal fun answersOf(ids: Array<String>, answers: BooleanArray): Map<String, Boolean> =
    ids.zip(answers.toList()).toMap(LinkedHashMap())

/** BedShapeDialog: the printable area of the edited printer. */
internal fun BedShapeOutcome.toParcel() = BedShapeParcel().also {
    when (this) {
        is BedShapeOutcome.Failure -> it.error = message
        is BedShapeOutcome.Success -> {
            it.kind = shape.kind.name
            it.sizeX = shape.sizeX
            it.sizeY = shape.sizeY
            it.originX = shape.originX
            it.originY = shape.originY
            it.diameter = shape.diameter
            it.texture = shape.texture
            it.model = shape.model
            it.points = shape.points.flatMap { point -> listOf(point.x, point.y) }.toDoubleArray()
        }
    }
}

internal fun BedShapeParcel.toBedShapeOutcome(): BedShapeOutcome {
    error?.let { return BedShapeOutcome.Failure(it) }
    return BedShapeOutcome.Success(
        BedShape(
            kind = BedShapeKind.valueOf(kind),
            sizeX = sizeX,
            sizeY = sizeY,
            originX = originX,
            originY = originY,
            diameter = diameter,
            texture = texture.orEmpty(),
            model = model.orEmpty(),
            points = (points ?: DoubleArray(0)).toList().chunked(2).mapNotNull { if (it.size == 2) Point2(it[0], it[1]) else null },
        ),
    )
}

/** EditGCodeDialog: the G-code it opens with, and the placeholders it lists. */
internal fun GcodePlaceholdersOutcome.toParcel() = GcodePlaceholdersParcel().also { parcel ->
    parcel.value = ""
    when (this) {
        is GcodePlaceholdersOutcome.Failure -> parcel.error = message
        is GcodePlaceholdersOutcome.Success -> {
            parcel.value = placeholders.value
            parcel.placeholders = placeholders.placeholders.map { node ->
                GcodePlaceholderParcel().also {
                    it.parent = node.parent
                    it.type = node.type.name
                    it.label = node.label.toParcels()
                    it.key = node.key
                    it.text = node.text
                    it.icon = node.icon
                    it.expanded = node.expanded
                }
            }.toTypedArray()
        }
    }
}

internal fun GcodePlaceholdersParcel.toGcodePlaceholdersOutcome(): GcodePlaceholdersOutcome {
    error?.let { return GcodePlaceholdersOutcome.Failure(it) }
    return GcodePlaceholdersOutcome.Success(
        GcodePlaceholders(
            value = value.orEmpty(),
            placeholders = placeholders.orEmpty().map { node ->
                GcodePlaceholder(
                    parent = node.parent,
                    type = GcodePlaceholderType.valueOf(node.type),
                    label = node.label.toTexts(),
                    key = node.key,
                    text = node.text,
                    icon = node.icon,
                    expanded = node.expanded,
                )
            },
        ),
    )
}

internal fun GcodePlaceholderInfo.toParcel() = GcodePlaceholderInfoParcel().also {
    it.label = label.toParcels()
    it.type = type
    it.description = description.toParcels()
    it.undefined = undefined
}

internal fun GcodePlaceholderInfoParcel.toGcodePlaceholderInfo() = GcodePlaceholderInfo(
    label = label.toTexts(),
    type = type.orEmpty(),
    description = description.toTexts(),
    undefined = undefined,
)

/** Search::OptionsSearcher: every setting the preset tabs show. */
internal fun SearchCatalogOutcome.toParcel() = SearchCatalogParcel().also { parcel ->
    when (this) {
        is SearchCatalogOutcome.Failure -> parcel.error = message
        is SearchCatalogOutcome.Success -> parcel.options = options.map { option ->
            SearchOptionParcel().also {
                it.kind = option.kind.name
                it.key = option.key
                it.id = option.id
                it.page = option.page.toParcels()
                it.group = option.group.toParcels()
                it.label = option.label.toParcels()
                it.mode = option.mode.name
            }
        }.toTypedArray()
    }
}

internal fun SearchCatalogParcel.toSearchCatalogOutcome(): SearchCatalogOutcome {
    error?.let { return SearchCatalogOutcome.Failure(it) }
    return SearchCatalogOutcome.Success(
        options.orEmpty().map { option ->
            SearchOption(
                kind = PresetKind.valueOf(option.kind),
                key = option.key,
                id = option.id,
                page = option.page.toTexts(),
                group = option.group.toTexts(),
                label = option.label.toTexts(),
                mode = SettingsMode.valueOf(option.mode),
            )
        },
    )
}

/** CreateFilamentPresetDialog: what it offers. */
internal fun CreateFilamentOptionsOutcome.toParcel() = CreateFilamentOptionsParcel().also { parcel ->
    when (this) {
        is CreateFilamentOptionsOutcome.Failure -> parcel.error = message
        is CreateFilamentOptionsOutcome.Success -> {
            parcel.vendors = vendors.toTypedArray()
            parcel.types = types.toTypedArray()
            parcel.baseFilaments = baseFilaments.toTypedArray()
            parcel.presetPrinters = presets.map(FilamentPresetChoice::printer).toTypedArray()
            parcel.presetNames = presets.map(FilamentPresetChoice::preset).toTypedArray()
            parcel.copyPrinters = copyPresets.map(FilamentPresetChoice::printer).toTypedArray()
            parcel.copyNames = copyPresets.map(FilamentPresetChoice::preset).toTypedArray()
        }
    }
}

internal fun CreateFilamentOptionsParcel.toCreateFilamentOptionsOutcome(): CreateFilamentOptionsOutcome {
    error?.let { return CreateFilamentOptionsOutcome.Failure(it) }
    return CreateFilamentOptionsOutcome.Success(
        vendors = vendors.orEmpty().toList(),
        types = types.orEmpty().toList(),
        baseFilaments = baseFilaments.orEmpty().toList(),
        presets = filamentChoices(presetPrinters, presetNames),
        copyPresets = filamentChoices(copyPrinters, copyNames),
    )
}

/** The printers and presets the check boxes of a filament dialog stand for. */
private fun filamentChoices(printers: Array<String>?, presets: Array<String>?): List<FilamentPresetChoice> =
    printers.orEmpty().mapIndexedNotNull { index, printer ->
        presets?.getOrNull(index)?.let { FilamentPresetChoice(printer, it) }
    }

/** CreatePrinterPresetDialog: what its two pages offer. */
internal fun CreatePrinterOptionsOutcome.toParcel() = CreatePrinterOptionsParcel().also { parcel ->
    when (this) {
        is CreatePrinterOptionsOutcome.Failure -> parcel.error = message
        is CreatePrinterOptionsOutcome.Success -> {
            parcel.vendors = vendors.toTypedArray()
            parcel.models = models.toTypedArray()
            parcel.nozzleDiameters = nozzleDiameters.toTypedArray()
            parcel.existingPrinters = existingPrinters.toTypedArray()
            parcel.presetVendors = presetVendors.toTypedArray()
            parcel.printerPresets = printerPresets.toTypedArray()
            parcel.filamentPresets = filamentPresets.toTypedArray()
            parcel.processPresets = processPresets.toTypedArray()
            parcel.templateAllowed = templateAllowed
            parcel.message = message
        }
    }
}

internal fun CreatePrinterOptionsParcel.toCreatePrinterOptionsOutcome(): CreatePrinterOptionsOutcome {
    error?.let { return CreatePrinterOptionsOutcome.Failure(it) }
    return CreatePrinterOptionsOutcome.Success(
        vendors = vendors.orEmpty().toList(),
        models = models.orEmpty().toList(),
        nozzleDiameters = nozzleDiameters.orEmpty().toList(),
        existingPrinters = existingPrinters.orEmpty().toList(),
        presetVendors = presetVendors.orEmpty().toList(),
        printerPresets = printerPresets.orEmpty().toList(),
        filamentPresets = filamentPresets.orEmpty().toList(),
        processPresets = processPresets.orEmpty().toList(),
        templateAllowed = templateAllowed,
        message = message.orEmpty(),
    )
}

/** What CreatePrinterPresetDialog's pages are filled in with. */
internal fun CreatePrinterRequest.toParcel() = CreatePrinterRequestParcel().also { parcel ->
    parcel.createNozzle = createNozzle
    parcel.customPrinter = customPrinter
    parcel.vendor = vendor
    parcel.model = model
    parcel.existingPrinter = existingPrinter
    parcel.nozzle = nozzle
    parcel.customNozzle = customNozzle
    parcel.customNozzleDiameter = customNozzleDiameter
    parcel.sizeX = sizeX
    parcel.sizeY = sizeY
    parcel.originX = originX
    parcel.originY = originY
    parcel.maxPrintHeight = maxPrintHeight
    parcel.customTexture = customTexture
    parcel.customModel = customModel
    parcel.presetVendor = presetVendor
    parcel.printerPreset = printerPreset
    parcel.fromTemplate = fromTemplate
    parcel.filamentPresets = filamentPresets.toTypedArray()
    parcel.processPresets = processPresets.toTypedArray()
}

internal fun CreatePrinterRequestParcel.toCreatePrinterRequest() = CreatePrinterRequest(
    createNozzle = createNozzle,
    customPrinter = customPrinter,
    vendor = vendor.orEmpty(),
    model = model.orEmpty(),
    existingPrinter = existingPrinter.orEmpty(),
    nozzle = nozzle.orEmpty(),
    customNozzle = customNozzle,
    customNozzleDiameter = customNozzleDiameter.orEmpty(),
    sizeX = sizeX,
    sizeY = sizeY,
    originX = originX,
    originY = originY,
    maxPrintHeight = maxPrintHeight,
    customTexture = customTexture.orEmpty(),
    customModel = customModel.orEmpty(),
    presetVendor = presetVendor.orEmpty(),
    printerPreset = printerPreset.orEmpty(),
    fromTemplate = fromTemplate,
    filamentPresets = filamentPresets.orEmpty().toList(),
    processPresets = processPresets.orEmpty().toList(),
)

/** What came of creating a filament, or of deleting one of its presets. */
internal fun PresetCreationOutcome.toParcel() = PresetCreationParcel().also { parcel ->
    when (this) {
        is PresetCreationOutcome.Failure -> parcel.error = message
        is PresetCreationOutcome.Question -> parcel.question = question.toParcel()
        is PresetCreationOutcome.Success -> parcel.name = name
    }
}

internal fun PresetCreationParcel.toPresetCreationOutcome(): PresetCreationOutcome {
    error?.let { return PresetCreationOutcome.Failure(it) }
    question?.let { return PresetCreationOutcome.Question(it.toDialog()) }
    return PresetCreationOutcome.Success(name)
}

/** The filaments of the user's own. */
internal fun CustomFilamentsOutcome.toParcel() = CustomFilamentsParcel().also { parcel ->
    when (this) {
        is CustomFilamentsOutcome.Failure -> parcel.error = message
        is CustomFilamentsOutcome.Success -> {
            parcel.ids = filaments.map(CustomFilament::id).toTypedArray()
            parcel.names = filaments.map(CustomFilament::name).toTypedArray()
        }
    }
}

internal fun CustomFilamentsParcel.toCustomFilamentsOutcome(): CustomFilamentsOutcome {
    error?.let { return CustomFilamentsOutcome.Failure(it) }
    val listed = ids.orEmpty()
    return CustomFilamentsOutcome.Success(
        listed.mapIndexedNotNull { index, id -> names?.getOrNull(index)?.let { CustomFilament(id, it) } },
    )
}

/** EditFilamentPresetDialog: what it shows for one of them. */
internal fun FilamentPresetsOutcome.toParcel() = FilamentPresetsParcel().also { parcel ->
    when (this) {
        is FilamentPresetsOutcome.Failure -> parcel.error = message
        is FilamentPresetsOutcome.Success -> {
            parcel.name = filament.name
            parcel.vendor = filament.vendor
            parcel.type = filament.type
            parcel.serial = filament.serial
            parcel.printers = filament.presets.map(FilamentPresetChoice::printer).toTypedArray()
            parcel.presets = filament.presets.map(FilamentPresetChoice::preset).toTypedArray()
        }
    }
}

internal fun FilamentPresetsParcel.toFilamentPresetsOutcome(): FilamentPresetsOutcome {
    error?.let { return FilamentPresetsOutcome.Failure(it) }
    return FilamentPresetsOutcome.Success(
        FilamentPresetList(
            name = name,
            vendor = vendor,
            type = type,
            serial = serial,
            presets = filamentChoices(printers, presets),
        ),
    )
}

/** ExportConfigsDialog: what it offers for an export kind. */
internal fun ConfigExportOptionsOutcome.toParcel() = ConfigExportOptionsParcel().also { parcel ->
    when (this) {
        is ConfigExportOptionsOutcome.Failure -> parcel.error = message
        is ConfigExportOptionsOutcome.Success -> {
            parcel.names = entries.map(ConfigExportEntry::name).toTypedArray()
            parcel.counts = entries.map { it.count.toLong() }.toLongArray()
            parcel.note = note
        }
    }
}

internal fun ConfigExportOptionsParcel.toConfigExportOptionsOutcome(): ConfigExportOptionsOutcome {
    error?.let { return ConfigExportOptionsOutcome.Failure(it) }
    val listed = names.orEmpty()
    val carried = counts ?: LongArray(0)
    return ConfigExportOptionsOutcome.Success(
        entries = listed.mapIndexed { index, name -> ConfigExportEntry(name, carried.getOrElse(index) { 0 }.toInt()) },
        note = note,
    )
}

/** DiffPresetDialog: what the presets of either side differ in. */
internal fun PresetComparisonOutcome.toParcel() = PresetComparisonParcel().also { parcel ->
    when (this) {
        is PresetComparisonOutcome.Failure -> parcel.error = message
        is PresetComparisonOutcome.Success -> parcel.kinds = kinds.map { compared ->
            PresetKindComparisonParcel().also {
                it.kind = compared.kind.name
                it.leftPresets = compared.leftPresets.toParcels()
                it.rightPresets = compared.rightPresets.toParcels()
                it.left = compared.left
                it.right = compared.right
                it.problem = compared.problem
                it.changes = compared.changes.toParcels()
                it.edited = compared.edited
                it.editedDirty = compared.editedDirty
            }
        }.toTypedArray()
    }
}

internal fun PresetComparisonParcel.toPresetComparisonOutcome(): PresetComparisonOutcome {
    error?.let { return PresetComparisonOutcome.Failure(it) }
    return PresetComparisonOutcome.Success(
        kinds.orEmpty().map { compared ->
            PresetKindComparison(
                kind = PresetKind.valueOf(compared.kind),
                leftPresets = compared.leftPresets.toItems(),
                rightPresets = compared.rightPresets.toItems(),
                left = compared.left,
                right = compared.right,
                problem = compared.problem,
                changes = compared.changes.toChanges(),
                edited = compared.edited,
                editedDirty = compared.editedDirty,
            )
        },
    )
}

/** The values a preset changed, as both dialogs list them. */
internal fun List<PresetChange>.toParcels(): Array<PresetChangeParcel> = map { change ->
    PresetChangeParcel().also {
        it.id = change.id
        it.category = change.category.toParcels()
        it.group = change.group.toParcels()
        it.label = change.label.toParcels()
        it.oldValue = change.oldValue.toParcels()
        it.newValue = change.newValue.toParcels()
    }
}.toTypedArray()

internal fun Array<PresetChangeParcel>?.toChanges(): List<PresetChange> = orEmpty().map { change ->
    PresetChange(
        id = change.id,
        category = change.category.toTexts(),
        group = change.group.toTexts(),
        label = change.label.toTexts(),
        oldValue = change.oldValue.toTexts(),
        newValue = change.newValue.toTexts(),
    )
}

/** Import Configs and Export Preset Bundle. */
internal fun ConfigTransferOutcome.toParcel() = ConfigTransferParcel().also { parcel ->
    when (this) {
        is ConfigTransferOutcome.Failure -> parcel.error = message
        is ConfigTransferOutcome.Success -> parcel.names = names.toTypedArray()
        is ConfigTransferOutcome.Overwrite -> {
            parcel.names = names.toTypedArray()
            parcel.overwritePreset = preset
        }
    }
}

internal fun ConfigTransferParcel.toConfigTransferOutcome(): ConfigTransferOutcome {
    error?.let { return ConfigTransferOutcome.Failure(it) }
    if (overwritePreset.isNotEmpty()) {
        return ConfigTransferOutcome.Overwrite(overwritePreset, names.orEmpty().toList())
    }
    return ConfigTransferOutcome.Success(names.orEmpty().toList())
}

/** The printer's host on the edited printer preset (PhysicalPrinterDialog). */
internal fun PrinterConnectionOutcome.toParcel() = PrinterConnectionParcel().also { parcel ->
    parcel.keys = emptyArray()
    parcel.values = emptyArray()
    parcel.saveName = ""
    parcel.webUi = ""
    parcel.apiKey = ""
    parcel.printerType = ""
    when (this) {
        is PrinterConnectionOutcome.Failure -> parcel.error = message
        is PrinterConnectionOutcome.Success -> {
            parcel.keys = connection.settings.values.keys.toTypedArray()
            parcel.values = connection.settings.values.values.toTypedArray()
            parcel.saveName = connection.saveName
            parcel.saveNameCopySuffix = connection.saveNameCopySuffix
            parcel.webUi = connection.webUi
            parcel.apiKey = connection.apiKey
            parcel.bambuDeviceTab = connection.bambuDeviceTab
            parcel.printerType = connection.printerType
        }
    }
}

internal fun PrinterConnectionParcel.toPrinterConnectionOutcome(): PrinterConnectionOutcome {
    error?.let { return PrinterConnectionOutcome.Failure(it) }
    val keys = keys.orEmpty()
    val values = values.orEmpty()
    return PrinterConnectionOutcome.Success(
        PrinterConnection(
            settings = ModelSettings(keys.indices.filter { it < values.size }.associate { keys[it] to values[it] }),
            saveName = saveName.orEmpty(),
            saveNameCopySuffix = saveNameCopySuffix,
            webUi = webUi.orEmpty(),
            apiKey = apiKey.orEmpty(),
            bambuDeviceTab = bambuDeviceTab,
            printerType = printerType.orEmpty(),
        ),
    )
}
