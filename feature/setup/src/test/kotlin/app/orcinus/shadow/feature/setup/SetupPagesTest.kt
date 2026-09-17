package app.orcinus.shadow.feature.setup

import app.orcinus.shadow.core.model.SetupFilament
import app.orcinus.shadow.core.model.SetupPrinterModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SetupPagesTest {
    @Test
    fun `the printer page lists Custom printers first, then vendors in alphabetical order`() {
        val sorted = PrinterPage.sorted(listOf(model("Voron", "Voron 2.4"), model("Custom", "MyKlipper"), model("BBL", "Bambu Lab X1"), model("anycubic", "Kobra")))

        assertEquals(listOf("Custom", "anycubic", "BBL", "Voron"), sorted.map(SetupPrinterModel::vendor))
    }

    @Test
    fun `a search keeps the models that contain every word in their name or vendor`() {
        val models = listOf(model("Creality", "Creality K2 Plus"), model("Creality", "Creality K1C"), model("Voron", "Voron 2.4 350"))

        val vendors = PrinterPage.vendors(models, "  creality  PLUS ")

        assertEquals(listOf("Creality"), vendors.map(PrinterVendor::vendor))
        assertEquals(listOf("Creality K2 Plus"), vendors.single().models.map(SetupPrinterModel::id))
        assertEquals(2, PrinterPage.vendors(models, "creality").single().models.size)
    }

    @Test
    fun `model titles drop the vendor as the printer page shows them`() {
        assertEquals("K2 Plus", PrinterPage.modelTitle(model("Creality", "Creality K2 Plus")))
        assertEquals("X1 Carbon", PrinterPage.modelTitle(model("BBL", "Bambu Lab X1 Carbon")))
        assertEquals("Klipper", PrinterPage.modelTitle(model("Custom", "Generic Klipper Printer")))
        assertEquals("Custom Printer", PrinterPage.vendorTitle("Custom"))
    }

    @Test
    fun `a vendor's check box chooses its listed models unless all are chosen`() {
        val listed = listOf(model("Creality", "A"), model("Creality", "B"))

        val some = PrinterPage.toggleVendor(setOf("A", "Other"), listed)
        val none = PrinterPage.toggleVendor(some, listed)

        assertEquals(setOf("A", "B", "Other"), some)
        assertEquals(setOf("Other"), none)
    }

    @Test
    fun `presets of a vendor and type with the same name before the at sign share a line, generic ones first`() {
        val filaments = listOf(
            filament("Creality PLA @K2-all", "Creality", "PLA", models = listOf(0)),
            filament("Generic PLA @K2 Plus-all", "Generic", "PLA", models = listOf(0)),
            filament("Generic PLA @System", "Generic", "PLA"),
            filament("Generic PETG @K2 Plus-all", "Generic", "PETG", models = listOf(1)),
        )

        val lines = FilamentPage.lines(filaments)

        assertEquals(listOf("Generic PLA", "Generic PETG", "Creality PLA"), lines.map(FilamentLine::name))
        assertEquals(listOf("Generic PLA @K2 Plus-all", "Generic PLA @System"), lines[0].presets)
        // One preset for every printer makes the line for every printer.
        assertNull(lines[0].models)
        assertEquals(setOf(1), lines[1].models)
    }

    @Test
    fun `filters show lines of a checked type and vendor for a checked printer`() {
        val line = FilamentLine("Generic PETG", "Generic", "PETG", listOf("Generic PETG @K2 Plus-all"), setOf(1))

        assertTrue(FilamentPage.shown(line, machines = setOf(1), types = setOf("PETG"), vendors = setOf("Generic")))
        assertFalse(FilamentPage.shown(line, machines = setOf(0), types = setOf("PETG"), vendors = setOf("Generic")))
        assertFalse(FilamentPage.shown(line, machines = setOf(1), types = setOf("PLA"), vendors = setOf("Generic")))
        assertTrue(FilamentPage.shown(line.copy(models = null), machines = emptySet(), types = setOf("petg"), vendors = setOf("generic")))
    }

    @Test
    fun `types come in the page's priority order and Generic leads the vendors`() {
        val lines = listOf(
            FilamentLine("A", "Polymaker", "TPU", emptyList(), null),
            FilamentLine("B", "Generic", "PETG", emptyList(), null),
            FilamentLine("C", "Elegoo", "PLA", emptyList(), null),
            FilamentLine("D", "Generic", "ASA", emptyList(), null),
        )

        assertEquals(listOf("PLA", "TPU", "PETG", "ASA"), FilamentPage.types(lines))
        assertEquals(listOf("Generic", "Polymaker", "Elegoo"), FilamentPage.vendors(lines))
    }

    @Test
    fun `a line starts checked when one of its presets is selected, and finishing installs all its presets`() {
        val filaments = listOf(
            filament("Generic PLA @K2 Plus-all", "Generic", "PLA", selected = false),
            filament("Generic PLA @System", "Generic", "PLA", selected = true),
            filament("Generic ABS @System", "Generic", "ABS", selected = false),
        )
        val lines = FilamentPage.lines(filaments)

        val checked = FilamentPage.checked(filaments, lines)

        assertEquals(setOf(0), checked)
        assertEquals(listOf("Generic PLA @K2 Plus-all", "Generic PLA @System"), FilamentPage.presets(lines, checked))
    }

    @Test
    fun `default filaments are the lines whose names before the first at sign are default materials`() {
        val lines = listOf(
            FilamentLine("Anker Generic PLA", "Anker", "PLA", listOf("Anker Generic PLA"), null),
            FilamentLine("Creality Generic PLA", "Creality", "PLA", listOf("Creality Generic PLA @K2-all"), null),
            FilamentLine("Generic ABS", "Generic", "ABS", listOf("Generic ABS"), null),
        )
        val models = listOf(
            model("Anker", "Anker M5", materials = listOf("Anker Generic PLA", "Anker Generic PETG")),
            model("Creality", "Creality K2 Plus", materials = listOf("Creality Generic PLA @K2-all")),
        )

        // A default material named with its printer never matches a name before "@", as on the desktop page.
        assertEquals(setOf(0), FilamentPage.defaults(lines, models))
    }

    @Test
    fun `the filter bar keeps names with the search text, or the checked or unchecked lines`() {
        val line = FilamentLine("Generic PLA Silk", "Generic", "PLA", emptyList(), null)

        assertTrue(FilamentPage.kept(line, checked = false, search = " silk", filter = FilamentFilter.ALL))
        assertFalse(FilamentPage.kept(line, checked = false, search = "petg", filter = FilamentFilter.ALL))
        assertTrue(FilamentPage.kept(line, checked = true, search = "", filter = FilamentFilter.CHECKED))
        assertFalse(FilamentPage.kept(line, checked = true, search = "", filter = FilamentFilter.UNCHECKED))
    }

    private fun model(vendor: String, id: String, materials: List<String> = emptyList()) = SetupPrinterModel(
        vendor = vendor,
        id = id,
        name = id,
        nozzleDiameters = listOf("0.4"),
        defaultMaterials = materials,
        cover = "/covers/$id.png",
        installedNozzles = emptyList(),
    )

    private fun filament(name: String, vendor: String, type: String, models: List<Int> = emptyList(), selected: Boolean = false) =
        SetupFilament(name, vendor, type, models, selected)
}
