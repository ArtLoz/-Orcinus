package app.orcinus.shadow.core.model

import java.util.Locale

/**
 * FlashforgePrintHostSendDialog's rules for feeding the filaments of a print
 * from the slots of a Flashforge material station (IFS).
 */
object FlashforgeSend {
    /**
     * normalize_material(): the kind of a material as the station compares it —
     * upper case, letters and digits only, and the families the printer tells
     * apart (silk, carbon PLA and PETG, PLA, ABS with ASA, PETG, flexible).
     */
    fun normalizeMaterial(material: String): String {
        val normalized = material.uppercase(Locale.ROOT).filter { it in 'A'..'Z' || it in '0'..'9' }
        return when {
            normalized.isEmpty() -> ""
            "SILK" in normalized -> "SILK"
            "PLA" in normalized && "CF" in normalized -> "PLACF"
            "PETG" in normalized && "CF" in normalized -> "PETGCF"
            "PLA" in normalized -> "PLA"
            "ABS" in normalized || "ASA" in normalized -> "ABS"
            "PETG" in normalized -> "PETG"
            "TPU" in normalized || "TPE" in normalized || "FLEX" in normalized -> "TPU"
            else -> normalized
        }
    }

    /** slot_matches_filament(): a loaded slot of the filament's own kind. */
    fun slotMatches(slot: FlashforgeMaterialSlot, filamentType: String): Boolean {
        if (!slot.hasFilament) return false
        val project = normalizeMaterial(filamentType)
        val loaded = normalizeMaterial(slot.materialName)
        return project.isNotEmpty() && loaded.isNotEmpty() && project == loaded
    }

    /**
     * auto_assign_mappings(): every filament gets the loaded slot of its kind
     * whose colour is nearest to its own, or none.
     */
    fun autoAssign(filaments: List<SentFilament>, slots: List<FlashforgeMaterialSlot>): List<Int?> = filaments.map { filament ->
        val color = rgb(filament.color)
        slots.filter { it.hasFilament && slotMatches(it, filament.type) }
            .minByOrNull { distance(color, rgb(it.materialColor)) }
            ?.slotId
    }

    /**
     * validate_before_close(): why the print cannot go as chosen, as
     * OrcaSlicer's msgid, or null when it can.
     */
    fun validate(
        useMaterialStation: Boolean,
        filaments: List<SentFilament>,
        assigned: List<Int?>,
        slots: List<FlashforgeMaterialSlot>,
    ): String? {
        if (!useMaterialStation && filaments.size > 1) {
            return "This plate uses multiple materials. Enable IFS and assign each tool to a printer slot."
        }
        if (!useMaterialStation) return null
        filaments.forEachIndexed { index, filament ->
            val slotId = assigned.getOrNull(index) ?: return "Each project material must be assigned to an IFS slot before printing."
            val slot = slots.firstOrNull { it.slotId == slotId }
            if (slot == null || !slot.hasFilament) return "Each project material must be assigned to a loaded IFS slot before printing."
            if (!slotMatches(slot, filament.type)) return "Each project material must match the material loaded in the selected IFS slot."
        }
        return null
    }

    /** extendedInfo(): the mapping of every filament that has a slot. */
    fun mappings(filaments: List<SentFilament>, assigned: List<Int?>, slots: List<FlashforgeMaterialSlot>): List<FlashforgeMapping> =
        filaments.mapIndexedNotNull { index, filament ->
            val slot = assigned.getOrNull(index)?.let { id -> slots.firstOrNull { it.slotId == id } } ?: return@mapIndexedNotNull null
            FlashforgeMapping(filament.tool, slot.slotId, slot.materialName, filament.color, slot.materialColor)
        }

    /**
     * to_wx_colour(): "#RRGGBB", "RRGGBB", "0xRRGGBB" or with an alpha after
     * it; grey #999999 for anything else.
     */
    fun rgb(color: String): Int {
        var text = color.trim()
        if (text.startsWith("0x", ignoreCase = true)) text = text.substring(2)
        text = text.removePrefix("#")
        if (text.length == 8 || text.length == 6) text.take(6).toIntOrNull(16)?.let { return it }
        return 0x999999
    }

    /** color_distance_sq() */
    private fun distance(first: Int, second: Int): Long {
        val dr = ((first shr 16) and 0xff) - ((second shr 16) and 0xff)
        val dg = ((first shr 8) and 0xff) - ((second shr 8) and 0xff)
        val db = (first and 0xff) - (second and 0xff)
        return dr.toLong() * dr + dg.toLong() * dg + db.toLong() * db
    }
}

/**
 * A filament of the plate as the send dialogs take it: its colour and type,
 * its index (the G-code's tool), and whether the slice prints with it
 * (FilamentInfo of the slice).
 */
data class SentFilament(val color: String, val type: String, val tool: Int = 0, val used: Boolean = true)
