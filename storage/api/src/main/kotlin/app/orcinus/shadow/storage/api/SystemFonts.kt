package app.orcinus.shadow.storage.api

/**
 * The fonts the phone has, which OrcaSlicer's text tool offers, as the
 * desktop app offers the fonts the computer has (wxFontEnumerator).
 */
fun interface SystemFonts {
    /** The font files, each once. */
    suspend fun fontFiles(): List<String>
}
