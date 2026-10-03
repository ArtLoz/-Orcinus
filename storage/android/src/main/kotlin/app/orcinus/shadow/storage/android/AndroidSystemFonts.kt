package app.orcinus.shadow.storage.android

import app.orcinus.shadow.storage.api.SystemFonts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** android.graphics.fonts.SystemFonts: the files of the fonts the system has. */
class AndroidSystemFonts : SystemFonts {
    override suspend fun fontFiles(): List<String> = withContext(Dispatchers.IO) {
        android.graphics.fonts.SystemFonts.getAvailableFonts()
            .mapNotNull { font -> font.file?.absolutePath }
            .distinct()
            .sorted()
    }
}
