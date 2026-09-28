package app.orcinus.shadow.core.ui.orca

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import app.orcinus.shadow.core.model.OrcaText
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The catalogue OrcaSlicer's texts are translated with; the app root provides it. */
val LocalOrcaCatalog = staticCompositionLocalOf { OrcaCatalog.EMPTY }

/** An OrcaSlicer text as the app shows it. */
@Composable
@ReadOnlyComposable
fun orcaText(text: OrcaText): String = LocalOrcaCatalog.current.format(text)

/** OrcaSlicer's message parts, one after another. */
@Composable
@ReadOnlyComposable
fun orcaText(texts: List<OrcaText>): String = LocalOrcaCatalog.current.format(texts)

/** _(): an OrcaSlicer msgid translated. */
@Composable
@ReadOnlyComposable
fun orcaString(msgid: String, context: String = ""): String = LocalOrcaCatalog.current.translate(msgid, context)

/**
 * The catalogue of the app's language, orca/i18n/<catalog>.po in the assets
 * (see core/ui/build.gradle.kts): the language Android gives the app, English
 * for one OrcaSlicer does not translate; empty until it is read.
 */
@Composable
fun rememberOrcaCatalog(): OrcaCatalog {
    val context = LocalContext.current.applicationContext
    val language = orcaLanguageOf(LocalConfiguration.current.locales[0]).catalog
    val catalog by produceState(OrcaCatalogs.loaded(language) ?: OrcaCatalog.EMPTY, context, language) {
        value = OrcaCatalogs.load(context, language)
    }
    return catalog
}

/** Reads each catalogue once per process. */
object OrcaCatalogs {
    private val lock = Mutex()
    private val catalogs = HashMap<String, OrcaCatalog>()

    internal fun loaded(language: String): OrcaCatalog? = synchronized(catalogs) { catalogs[language] }

    suspend fun load(context: Context, language: String): OrcaCatalog = lock.withLock {
        loaded(language) ?: withContext(Dispatchers.IO) {
            try {
                OrcaCatalog.parse(context.assets.open("orca/i18n/$language.po").use { it.readBytes().decodeToString() })
            } catch (_: FileNotFoundException) {
                OrcaCatalog.EMPTY
            }
        }.also { catalog -> synchronized(catalogs) { catalogs[language] = catalog } }
    }
}
