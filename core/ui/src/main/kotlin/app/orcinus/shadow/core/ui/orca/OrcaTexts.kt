package app.orcinus.shadow.core.ui.orca

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.ui.R
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
 * The catalogue of the app's language, orca/i18n/<language>.po in the assets
 * (see core/ui/build.gradle.kts); empty until it is read, and for a language
 * OrcaSlicer does not translate.
 */
@Composable
fun rememberOrcaCatalog(): OrcaCatalog {
    val context = LocalContext.current.applicationContext
    val catalog by produceState(OrcaCatalogs.loaded ?: OrcaCatalog.EMPTY, context) { value = OrcaCatalogs.load(context) }
    return catalog
}

/** Reads the catalogue once per process. */
object OrcaCatalogs {
    private val lock = Mutex()

    @Volatile
    internal var loaded: OrcaCatalog? = null
        private set

    suspend fun load(context: Context): OrcaCatalog = lock.withLock {
        loaded ?: withContext(Dispatchers.IO) {
            val language = context.getString(R.string.orca_catalog_language)
            if (language.isEmpty()) {
                OrcaCatalog.EMPTY
            } else {
                try {
                    OrcaCatalog.parse(context.assets.open("orca/i18n/$language.po").use { it.readBytes().decodeToString() })
                } catch (_: FileNotFoundException) {
                    OrcaCatalog.EMPTY
                }
            }
        }.also { loaded = it }
    }
}
