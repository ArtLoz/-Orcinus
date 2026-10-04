package app.orcinus.shadow.core.ui.orca

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The texts of OrcaSlicer's web pages (resources/web/data/text.js, LangText):
 * each language's texts by their ids (the pages' "tid"), read from the asset
 * orca/web/text.js.
 */
class OrcaWebTexts private constructor(private val languages: Map<String, Map<String, String>>) {
    /**
     * TranslatePage(): the text [tid] in the page's [language], whose texts
     * are the English ones when the file has none of it; [default], the
     * page's own text, where those lack it.
     */
    fun text(language: String, tid: String, default: String): String =
        (languages[language] ?: languages[ENGLISH])?.get(tid) ?: default

    companion object {
        val EMPTY = OrcaWebTexts(emptyMap())
        private const val ENGLISH = "en"
        private val LANGUAGE = Regex("""^\s*([A-Za-z_]+):\s*\{\s*$""")
        private val TEXT = Regex("""^\s*(\w+):\s*"((?:[^"\\]|\\.)*)",?\s*$""")

        /** LangText of text.js: a language opens its block, and each of its texts is a line of its own. */
        fun parse(source: String): OrcaWebTexts {
            val languages = LinkedHashMap<String, MutableMap<String, String>>()
            var current: MutableMap<String, String>? = null
            for (line in source.lineSequence().dropWhile { !it.startsWith("var LangText") }.drop(1)) {
                // The object ends with the first line back at the start.
                if (line.startsWith("}")) break
                LANGUAGE.matchEntire(line)?.let { language ->
                    current = languages.getOrPut(language.groupValues[1]) { LinkedHashMap() }
                }
                TEXT.matchEntire(line)?.let { text -> current?.put(text.groupValues[1], unescape(text.groupValues[2])) }
            }
            return OrcaWebTexts(languages)
        }

        /** A JavaScript string's escapes. */
        private fun unescape(text: String): String {
            if ('\\' !in text) return text
            val result = StringBuilder(text.length)
            var index = 0
            while (index < text.length) {
                val char = text[index++]
                if (char != '\\' || index == text.length) {
                    result.append(char)
                    continue
                }
                when (val escaped = text[index++]) {
                    'n' -> result.append('\n')
                    't' -> result.append('\t')
                    'r' -> result.append('\r')
                    'u' -> if (index + 4 <= text.length) {
                        result.append(text.substring(index, index + 4).toInt(16).toChar())
                        index += 4
                    }
                    else -> result.append(escaped)
                }
            }
            return result.toString()
        }
    }
}

/**
 * GUI_App::current_language_code_safe(): the code of the language the home
 * page is opened in ("lang"), which keeps a language's own region only for
 * the languages Prusa Research translated, and is English otherwise.
 */
fun webLanguageCode(language: OrcaLanguage): String =
    WEB_LANGUAGE_CODES[language.canonicalName.substringBefore('_')] ?: "en_US"

private val WEB_LANGUAGE_CODES = mapOf(
    "cs" to "cs_CZ",
    "sk" to "cs_CZ",
    "de" to "de_DE",
    "nl" to "nl_NL",
    "sv" to "sv_SE",
    "es" to "es_ES",
    "fr" to "fr_FR",
    "it" to "it_IT",
    "ja" to "ja_JP",
    "ko" to "ko_KR",
    "pl" to "pl_PL",
    "uk" to "uk_UA",
    "zh" to "zh_CN",
    "ru" to "ru_RU",
    "tr" to "tr_TR",
    "pt" to "pt_BR",
    "lt" to "lt_LT",
    "vi" to "vi_VN",
    "th" to "th_TH",
)

/** A web page's text [tid] in the app's language; [default] is the page's own. */
class OrcaWebText(private val texts: OrcaWebTexts, private val language: String) {
    operator fun invoke(tid: String, default: String): String = texts.text(language, tid, default)
}

/** The texts of OrcaSlicer's web pages in the app's language; the page's own until the file is read. */
@Composable
fun rememberOrcaWebText(): OrcaWebText {
    val context = LocalContext.current.applicationContext
    val language = webLanguageCode(orcaLanguageOf(LocalConfiguration.current.locales[0]))
    val texts by produceState(OrcaWebTextsFile.loaded ?: OrcaWebTexts.EMPTY, context) {
        value = OrcaWebTextsFile.load(context)
    }
    return OrcaWebText(texts, language)
}

/** Reads text.js once per process. */
private object OrcaWebTextsFile {
    private val lock = Mutex()

    @Volatile
    var loaded: OrcaWebTexts? = null
        private set

    suspend fun load(context: Context): OrcaWebTexts = lock.withLock {
        loaded ?: withContext(Dispatchers.IO) {
            try {
                OrcaWebTexts.parse(context.assets.open("orca/web/text.js").use { it.readBytes().decodeToString() })
            } catch (_: FileNotFoundException) {
                OrcaWebTexts.EMPTY
            }
        }.also { loaded = it }
    }
}
