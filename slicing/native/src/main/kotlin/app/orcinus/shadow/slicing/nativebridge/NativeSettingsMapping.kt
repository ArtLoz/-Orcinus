package app.orcinus.shadow.slicing.nativebridge

import app.orcinus.shadow.core.model.AppConfigOutcome
import app.orcinus.shadow.core.model.BedShape
import app.orcinus.shadow.core.model.BedShapeKind
import app.orcinus.shadow.core.model.BedShapeOutcome
import app.orcinus.shadow.core.model.ConfigTransferOutcome
import app.orcinus.shadow.core.model.DialogIcon
import app.orcinus.shadow.core.model.GcodePlaceholder
import app.orcinus.shadow.core.model.GcodePlaceholderInfo
import app.orcinus.shadow.core.model.GcodePlaceholderType
import app.orcinus.shadow.core.model.GcodePlaceholders
import app.orcinus.shadow.core.model.GcodePlaceholdersOutcome
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.core.model.PresetChange
import app.orcinus.shadow.core.model.PresetCreationOutcome
import app.orcinus.shadow.core.model.PresetKind
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

// The settings tabs' values as the bridge passes them, and back.

/** PresetKind in orca_engine_adapter.hpp. */
internal val PresetKind.native: Long
    get() = ordinal.toLong()

/** SettingsMode in orca_engine_adapter.hpp. */
internal val SettingsMode.native: Long
    get() = ordinal.toLong()

internal fun Map<String, Boolean>.answerIds(): Array<String> = keys.toTypedArray()

internal fun Map<String, Boolean>.answerFlags(): BooleanArray = values.toBooleanArray()

internal fun NativeSettingDefinitions.toTabOutcome(kind: PresetKind): SettingsTabOutcome {
    if (status != NativeSceneStatus.SUCCESS) {
        return SettingsTabOutcome.Failure(message.ifBlank { "OrcaSlicer could not describe the settings" })
    }
    return SettingsTabOutcome.Success(SettingsTab(kind, settings.associate { it.key to it.toDefinition() }))
}

private fun NativeSettingDefinition.toDefinition() = SettingDefinition(
    key = key,
    type = when (type) {
        0L -> SettingType.NONE
        1L -> SettingType.FLOAT
        2L -> SettingType.INT
        3L -> SettingType.STRING
        4L -> SettingType.PERCENT
        5L -> SettingType.FLOAT_OR_PERCENT
        6L -> SettingType.POINT
        7L -> SettingType.POINT3
        8L -> SettingType.BOOL
        9L -> SettingType.ENUM
        VECTOR_TYPE + 1L -> SettingType.FLOATS
        VECTOR_TYPE + 2L -> SettingType.INTS
        VECTOR_TYPE + 3L -> SettingType.STRINGS
        VECTOR_TYPE + 4L -> SettingType.PERCENTS
        VECTOR_TYPE + 5L -> SettingType.FLOATS_OR_PERCENTS
        VECTOR_TYPE + 6L -> SettingType.POINTS
        VECTOR_TYPE + 8L -> SettingType.BOOLS
        VECTOR_TYPE + 9L -> SettingType.ENUMS
        else -> SettingType.OTHER
    },
    label = label,
    sidetext = sidetext,
    category = category,
    mode = mode.toMode(),
    control = SettingControl.entries.getOrElse(guiType.toInt()) { SettingControl.DEFAULT },
    enumValues = enumValues.toList(),
    enumLabels = enumLabels.toList(),
    multiline = multiline,
    fullWidth = fullWidth,
    isCode = isCode,
    height = height,
)

internal fun NativePresetSettings.toOutcome(): PresetSettingsOutcome {
    if (status != NativeSceneStatus.SUCCESS) {
        return PresetSettingsOutcome.Failure(message.ifBlank { "OrcaSlicer could not apply the settings" })
    }
    if (hasQuestion) {
        return PresetSettingsOutcome.Question(question.toDialog(), questionLoadsSelection)
    }
    return PresetSettingsOutcome.Success(
        PresetSettings(
            kind = PresetKind.entries[kind.toInt()],
            preset = preset,
            label = label,
            dirty = dirty,
            savedDirty = savedDirty,
            isDefault = isDefault,
            isSystem = isSystem,
            hasParent = hasParent,
            canDelete = canDelete,
            mode = mode.toMode(),
            pages = pages.map { it.toPage() },
            activePage = activePage,
            variants = variants.toList(),
            variant = variant.toInt(),
            settings = settings.map { it.toState() },
            saveName = saveName,
            saveNameCopySuffix = saveNameCopySuffix,
            modelSettings = if (hasModelSettings) {
                modelSettingKeys.indices.filter { it < modelSettingValues.size }.map { object_ ->
                    val keys = modelSettingKeys[object_]
                    val values = modelSettingValues[object_]
                    ModelSettings(keys.indices.filter { it < values.size }.associate { keys[it] to values[it] })
                }
            } else {
                null
            },
        ),
        notices = notices.map { it.toDialog() },
    )
}

private fun NativeSettingsPage.toPage() = SettingsPage(
    title = title,
    label = label.map { it.toText() },
    icon = icon,
    groups = groups.map { group -> SettingsGroup(group.title, group.icon, group.lines.map { it.toLine() }) },
)

private fun NativeSettingsLine.toLine() = SettingsLine(
    label = label,
    tooltip = tooltip,
    separator = separator,
    widget = SettingWidget.entries.getOrElse(widget.toInt()) { SettingWidget.NONE },
    hasOverride = hasOverride,
    options = options.map { option ->
        SettingsLineOption(
            id = option.id,
            key = option.key,
            index = option.index,
            label = option.label,
            fullWidth = option.fullWidth,
            isCode = option.isCode,
            multiline = option.multiline,
            height = option.height,
            editCustomGcode = option.editCustomGcode,
        )
    },
)

private fun NativeSettingState.toState() = SettingState(
    id = id,
    key = key,
    value = value,
    modified = modified,
    system = system,
    enabled = enabled,
    visible = visible,
    choices = if (hasChoices) choiceValues.zip(choiceLabels, ::SettingChoice) else null,
    nullable = nullable,
    isNil = isNil,
    mixed = mixed,
    overrideEnabled = overrideEnabled,
    listValues = listValues.toList(),
)

/** PresetCreation in orca_engine_adapter.hpp. */
internal fun NativePresetCreation.toOutcome(): PresetCreationOutcome = when {
    status != NativeSceneStatus.SUCCESS ->
        PresetCreationOutcome.Failure(message.ifBlank { "OrcaSlicer could not create the filament" })
    hasQuestion -> PresetCreationOutcome.Question(question.toDialog())
    else -> PresetCreationOutcome.Success(name)
}

internal fun NativeSettingsDialog.toDialog() = SettingsDialog(
    id = id,
    icon = DialogIcon.entries.getOrElse(icon.toInt()) { DialogIcon.WARNING },
    title = title.map { it.toText() },
    text = text.map { it.toText() },
    question = question,
    yes = yes.takeIf { it.msgid.isNotEmpty() }?.toText(),
    no = no.takeIf { it.msgid.isNotEmpty() }?.toText(),
    checkbox = checkbox.takeIf { it.msgid.isNotEmpty() }?.toText(),
    checked = checked,
)

internal fun NativeUiText.toText() = OrcaText(
    msgid = msgid,
    args = args.toList(),
    translateArgs = translateArgs,
    context = context,
    msgidPlural = msgidPlural,
    count = count,
)

internal fun NativePresetNameValidation.toOutcome(): PresetNameOutcome {
    if (status != NativeSceneStatus.SUCCESS) {
        return PresetNameOutcome.Failure(message.ifBlank { "OrcaSlicer could not check the name" })
    }
    return PresetNameOutcome.Success(
        PresetNameValidation(
            check = PresetNameCheck.entries.getOrElse(check.toInt()) { PresetNameCheck.INVALID },
            info = info.map { it.toText() },
            existing = existing,
            existingInProject = existingInProject,
            editedInProject = editedInProject,
        ),
    )
}

/** The values of [keys], in their order. */
internal fun NativeAppConfigValues.toOutcome(keys: List<String>): AppConfigOutcome {
    if (status != NativeSceneStatus.SUCCESS) {
        return AppConfigOutcome.Failure(message.ifBlank { "OrcaSlicer could not read its app configuration" })
    }
    return AppConfigOutcome.Success(keys.zip(values).toMap())
}

internal fun NativePresetNames.toOutcome(): PresetNamesOutcome {
    if (status != NativeSceneStatus.SUCCESS) {
        return PresetNamesOutcome.Failure(message.ifBlank { "OrcaSlicer could not list the presets" })
    }
    return PresetNamesOutcome.Success(names.toList())
}

private fun Long.toMode(): SettingsMode = SettingsMode.entries.getOrElse(toInt()) { SettingsMode.DEVELOP }

/** coVectorType in libslic3r/Config.hpp. */
private const val VECTOR_TYPE = 0x4000L

/** BedShapeState in orca_engine_adapter.hpp. */
internal fun NativeBedShape.toOutcome(): BedShapeOutcome {
    if (status != NativeSceneStatus.SUCCESS) {
        return BedShapeOutcome.Failure(message.ifBlank { "OrcaSlicer could not describe the shape of the plate" })
    }
    return BedShapeOutcome.Success(
        BedShape(
            kind = BedShapeKind.entries.getOrElse(kind.toInt()) { BedShapeKind.RECTANGLE },
            sizeX = sizeX,
            sizeY = sizeY,
            originX = originX,
            originY = originY,
            diameter = diameter,
            texture = texture,
            model = model,
            points = points.toList().chunked(2).mapNotNull { if (it.size == 2) Point2(it[0], it[1]) else null },
        ),
    )
}

/** GcodePlaceholders in orca_engine_adapter.hpp. */
internal fun NativeGcodePlaceholders.toOutcome(): GcodePlaceholdersOutcome {
    if (status != NativeSceneStatus.SUCCESS) {
        return GcodePlaceholdersOutcome.Failure(message.ifBlank { "OrcaSlicer could not list the G-code placeholders" })
    }
    return GcodePlaceholdersOutcome.Success(
        GcodePlaceholders(
            value = value,
            placeholders = placeholders.map { node ->
                GcodePlaceholder(
                    parent = node.parent,
                    type = GcodePlaceholderType.entries.getOrElse(node.type.toInt()) { GcodePlaceholderType.GROUP },
                    label = node.label.map { it.toText() },
                    key = node.key,
                    text = node.text,
                    icon = node.icon,
                    expanded = node.expanded,
                )
            },
        ),
    )
}

/** GcodePlaceholderInfo in orca_engine_adapter.hpp. */
internal fun NativeGcodePlaceholderInfo.toInfo() = GcodePlaceholderInfo(
    label = label.map { it.toText() },
    type = type,
    description = description.map { it.toText() },
    undefined = undefined,
)

/** SearchCatalog in orca_engine_adapter.hpp. */
internal fun NativeSearchCatalog.toOutcome(): SearchCatalogOutcome {
    if (status != NativeSceneStatus.SUCCESS) {
        return SearchCatalogOutcome.Failure(message.ifBlank { "OrcaSlicer could not list its settings" })
    }
    return SearchCatalogOutcome.Success(
        options.map { option ->
            SearchOption(
                kind = PresetKind.entries.getOrElse(option.kind.toInt()) { PresetKind.PRINT },
                key = option.key,
                id = option.id,
                page = option.page.map { it.toText() },
                group = option.group.map { it.toText() },
                label = option.label.map { it.toText() },
                mode = SettingsMode.entries.getOrElse(option.mode.toInt()) { SettingsMode.SIMPLE },
            )
        },
    )
}

/** PresetChange in orca_engine_adapter.hpp. */
internal fun NativePresetChange.toChange() = PresetChange(
    id = id,
    category = category.map { it.toText() },
    group = group.map { it.toText() },
    label = label.map { it.toText() },
    oldValue = oldValue.map { it.toText() },
    newValue = newValue.map { it.toText() },
)

/** ConfigTransfer in orca_engine_adapter.hpp. */
internal fun NativeConfigTransfer.toOutcome(): ConfigTransferOutcome {
    if (status != NativeSceneStatus.SUCCESS) {
        return ConfigTransferOutcome.Failure(message.ifBlank { "OrcaSlicer could not transfer the configuration" })
    }
    if (overwritePreset.isNotEmpty()) {
        return ConfigTransferOutcome.Overwrite(overwritePreset, names.toList())
    }
    return ConfigTransferOutcome.Success(names.toList())
}

/** PrinterConnection in orca_engine_adapter.hpp. */
internal fun NativePrinterConnection.toOutcome(): PrinterConnectionOutcome {
    if (status != NativeSceneStatus.SUCCESS) {
        return PrinterConnectionOutcome.Failure(message.ifBlank { "OrcaSlicer could not read the printer's host" })
    }
    return PrinterConnectionOutcome.Success(
        PrinterConnection(
            settings = ModelSettings(keys.indices.filter { it < values.size }.associate { keys[it] to values[it] }),
            saveName = saveName,
            saveNameCopySuffix = saveNameCopySuffix,
            webUi = webUi,
            apiKey = apiKey,
            bambuDeviceTab = bambuDeviceTab,
            printerType = printerType,
        ),
    )
}
