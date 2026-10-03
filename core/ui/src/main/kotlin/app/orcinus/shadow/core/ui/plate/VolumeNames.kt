package app.orcinus.shadow.core.ui.plate

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.volumeAt
import app.orcinus.shadow.core.ui.R
import app.orcinus.shadow.core.ui.displayName

/**
 * ObjectDataViewModel names a volume of the object by its own name, the one
 * at [at] here; the object's own mesh is named after the object unless the
 * file named it.
 */
@Composable
fun PlateObject.volumeName(at: Int): String {
    val part = volumeAt(at) ?: return displayName()
    return when {
        at == 0 -> part.name.ifEmpty { displayName() }
        else -> part.name.ifEmpty { stringResource(partName(part.type), stringResource(shapeName(part.shape))) }
    }
}

/** What a row of the list calls a part of an object (ObjectDataViewModel). */
private fun partName(type: VolumeType): Int = when (type) {
    VolumeType.PART -> R.string.object_part_name
    VolumeType.NEGATIVE -> R.string.object_part_negative
    VolumeType.MODIFIER -> R.string.object_part_modifier
    VolumeType.SUPPORT_BLOCKER -> R.string.object_part_support_blocker
    VolumeType.SUPPORT_ENFORCER -> R.string.object_part_support_enforcer
}
