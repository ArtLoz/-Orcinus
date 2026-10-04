package app.orcinus.shadow.core.ui.orca

import android.content.Context
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A hint of OrcaSlicer's resources/data/hints.ini (HintData), as DailyTipsPanel
 * shows it: [text] is the catalogue's msgid, a headline and a body divided by
 * a new line, with <b> marks; the panel translates it as it shows it.
 */
data class OrcaHint(
    val text: String,
    val documentationLink: String = "",
    val image: String = "",
)

/**
 * HintDatabase::load_hints_from_file(): the hints of hints.ini in the file's
 * order, each section "[hint:<name>]" with its keys; the text unescaped as
 * unescape_string_cstyle() does. A hint whose hypertext marks are broken is
 * left out, as Orca leaves it.
 */
object OrcaHints {
    fun parse(ini: String): List<OrcaHint> {
        val hints = mutableListOf<OrcaHint>()
        var section: String? = null
        var keys = mutableMapOf<String, String>()
        fun close() {
            if (section?.startsWith("hint:") == true) hintOf(keys)?.let(hints::add)
        }
        for (raw in ini.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith('#') || line.startsWith(';')) continue
            if (line.startsWith('[') && line.endsWith(']')) {
                close()
                section = line.substring(1, line.length - 1)
                keys = mutableMapOf()
                continue
            }
            val equals = line.indexOf('=')
            if (equals > 0) keys[line.substring(0, equals).trim()] = line.substring(equals + 1).trim()
        }
        close()
        return hints
    }

    private fun hintOf(keys: Map<String, String>): OrcaHint? {
        val text = unescape(keys["text"].orEmpty())
        // Hypertext must be one per hint and closed by </a>.
        val start = text.indexOf(HYPERTEXT_START)
        if (start >= 0) {
            val rest = text.substring(start + HYPERTEXT_START.length)
            if (HYPERTEXT_START in rest) return null
            val end = rest.indexOf(HYPERTEXT_END)
            if (end < 0 || HYPERTEXT_END in rest.substring(end + HYPERTEXT_END.length)) return null
        }
        return OrcaHint(text, keys["documentation_link"].orEmpty(), keys["image"].orEmpty())
    }

    /** unescape_string_cstyle() */
    private fun unescape(text: String): String = buildString {
        var index = 0
        while (index < text.length) {
            val c = text[index]
            if (c == '\\' && index + 1 < text.length) {
                index++
                append(
                    when (val next = text[index]) {
                        'n' -> '\n'
                        't' -> '\t'
                        'r' -> '\r'
                        else -> next
                    },
                )
            } else {
                append(c)
            }
            index++
        }
    }

    /**
     * DailyTipsData's main text: the part before a hint's hypertext, which the
     * panel does not show (its hyper_text and follow_text are "currently not
     * used").
     */
    fun mainText(translated: String): String = translated.substringBefore(HYPERTEXT_START).replace(HYPERTEXT_END, "")

    private const val HYPERTEXT_START = "<a>"
    private const val HYPERTEXT_END = "</a>"
}

/** Reads hints.ini once per process. */
object OrcaHintsFile {
    private val lock = Mutex()

    @Volatile
    var loaded: List<OrcaHint>? = null
        private set

    suspend fun load(context: Context): List<OrcaHint> = lock.withLock {
        loaded ?: withContext(Dispatchers.IO) {
            try {
                OrcaHints.parse(context.assets.open("orca/data/hints.ini").use { it.readBytes().decodeToString() })
            } catch (_: FileNotFoundException) {
                emptyList()
            }
        }.also { loaded = it }
    }
}
