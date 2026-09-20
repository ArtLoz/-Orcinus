package app.orcinus.shadow.core.ui.orca

import app.orcinus.shadow.core.model.OrcaText
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class OrcaCatalogTest {
    private val catalog = OrcaCatalog.parse(
        """
        # Translation header
        msgid ""
        msgstr ""
        "Language: ru_RU\n"
        "Plural-Forms: nplurals=3; plural=(n%10==1 && n%100!=11 ? 0 : n%10>=2 && "
        "n%10<=4 && (n%100<12 || n%100>14) ? 1 : 2);\n"

        msgid "Layer height"
        msgstr "Высота слоя"

        msgctxt "PresetName"
        msgid "Copy"
        msgstr "Копировать"

        msgid "Copy"
        msgstr "Копия"

        #, c-format, boost-format
        msgid "%1${'$'}d error repaired"
        msgid_plural "%1${'$'}d errors repaired"
        msgstr[0] "Исправлена %1${'$'}d ошибка"
        msgstr[1] "Исправлено %1${'$'}d ошибки"
        msgstr[2] "Исправлено %1${'$'}d ошибок"

        #, fuzzy
        #| msgid "Old text"
        msgid "Fuzzy text"
        msgstr "Неточный перевод"

        msgid "Untranslated"
        msgstr ""

        msgid ""
        "Too small layer height.\n"
        "Reset to 0.2."
        msgstr ""
        "Слишком маленькая высота слоя.\n"
        "Сброс до 0.2."

        #, c-format, boost-format
        msgid "%s can't be a percentage"
        msgstr "%s не может быть в процентах"

        #, boost-format
        msgid "Preset \"%1%\" already exists."
        msgstr "Профиль «%1%» уже существует."

        msgid "Quote \"inside\" and a backslash \\"
        msgstr "Кавычки «внутри» и обратная косая черта \\"
        """.trimIndent(),
    )

    @Test
    fun `msgids are translated, with their context when they have one`() {
        assertEquals("Высота слоя", catalog.translate("Layer height"))
        assertEquals("Копировать", catalog.translate("Copy", "PresetName"))
        assertEquals("Копия", catalog.translate("Copy"))
        assertEquals("Слишком маленькая высота слоя.\nСброс до 0.2.", catalog.translate("Too small layer height.\nReset to 0.2."))
        assertEquals("Кавычки «внутри» и обратная косая черта \\", catalog.translate("Quote \"inside\" and a backslash \\"))
    }

    @Test
    fun `fuzzy, untranslated, and unknown msgids stay as they are, as msgfmt compiles the catalogue`() {
        assertEquals("Fuzzy text", catalog.translate("Fuzzy text"))
        assertEquals("Untranslated", catalog.translate("Untranslated"))
        assertEquals("Unknown", catalog.translate("Unknown"))
        assertEquals("", catalog.translate(""))
    }

    @Test
    fun `plural forms follow the catalogue's Plural-Forms`() {
        val forms = listOf(1L, 2L, 5L, 11L, 21L, 22L, 112L).map {
            catalog.format(OrcaText("%1\$d error repaired", listOf(it.toString()), msgidPlural = "%1\$d errors repaired", count = it))
        }

        assertEquals(
            listOf(
                "Исправлена 1 ошибка",
                "Исправлено 2 ошибки",
                "Исправлено 5 ошибок",
                "Исправлено 11 ошибок",
                "Исправлена 21 ошибка",
                "Исправлено 22 ошибки",
                "Исправлено 112 ошибок",
            ),
            forms,
        )
        assertEquals("errors", OrcaCatalog.EMPTY.translatePlural("error", "errors", 3))
    }

    @Test
    fun `placeholders take the arguments, translated when the text asks`() {
        assertEquals("Высота слоя не может быть в процентах", catalog.format(OrcaText("%s can't be a percentage", listOf("Layer height"), translateArgs = true)))
        assertEquals("Профиль «Layer height» уже существует.", catalog.format(OrcaText("Preset \"%1%\" already exists.", listOf("Layer height"))))
        assertEquals(
            "Is it 150% or 150 mm/s?\nYES for 150%, \nNO for 150 mm/s.",
            catalog.format(OrcaText("Is it %s%% or %s %s?\nYES for %s%%, \nNO for %s %s.", listOf("150", "150", "mm/s", "150", "150", "mm/s"))),
        )
        assertEquals("Reset to 0.300.", OrcaCatalog.fill("Reset to %0.3f.", listOf("0.300")))
        assertEquals("b a", OrcaCatalog.fill("%2\$s %1\$s", listOf("a", "b")))
        // Without arguments a text is shown as it is.
        assertEquals("Reset to 50% of skin depth.", catalog.format(OrcaText("Reset to 50% of skin depth.")))
    }

    @Test
    fun `message parts are joined`() {
        assertEquals(
            "Высота слоя\n\nКопировать",
            catalog.format(listOf(OrcaText("Layer height"), OrcaText("\n\n"), OrcaText("Copy", context = "PresetName"))),
        )
    }

    @Test
    fun `OrcaSlicer's Russian catalogue translates the process settings`() {
        val russian = OrcaCatalog.parse(File("../../upstream/OrcaSlicer/localization/i18n/ru/OrcaSlicer_ru.po").readText())

        assertEquals("Высота слоя", russian.translate("Layer height"))
        assertEquals("Копировать", russian.translate("Copy", "PresetName"))
        assertEquals("мм", russian.translate("mm"))
    }
}
