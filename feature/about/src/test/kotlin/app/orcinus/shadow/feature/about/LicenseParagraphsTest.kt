package app.orcinus.shadow.feature.about

import kotlin.test.Test
import kotlin.test.assertEquals

class LicenseParagraphsTest {
    @Test
    fun joinsHardWrappedLines() {
        val text = """
            Permission is hereby granted, free of charge, to any person obtaining a copy
            of this software and associated documentation files.

            THE SOFTWARE IS PROVIDED "AS IS".
        """.trimIndent()

        assertEquals(
            listOf(
                "Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files.",
                "THE SOFTWARE IS PROVIDED \"AS IS\".",
            ),
            licenseParagraphs(text),
        )
    }

    @Test
    fun keepsListItemsOnTheirOwnLines() {
        val text = "  a) The work must carry prominent notices stating that you modified\n  it, and giving a relevant date.\n  b) The work must carry prominent notices."

        assertEquals(
            listOf("a) The work must carry prominent notices stating that you modified it, and giving a relevant date.\nb) The work must carry prominent notices."),
            licenseParagraphs(text),
        )
    }

    @Test
    fun keepsCentredTitleLines() {
        val text = "                    GNU GENERAL PUBLIC LICENSE\n                       Version 2, June 1991"

        assertEquals(listOf("GNU GENERAL PUBLIC LICENSE\nVersion 2, June 1991"), licenseParagraphs(text))
    }

    @Test
    fun keepsColumnLayout() {
        val text = "Qhull, Copyright (c) 1993-2020\nC.B. Barber          Arlington, MA"

        assertEquals(listOf(text), licenseParagraphs("\r\n\r\n$text\r\n"))
    }
}
