package app.orcinus.shadow.core.ui.orca

import app.orcinus.shadow.core.model.OrcaText

/**
 * OrcaSlicer's translations for one language, read from its gettext catalogue
 * (localization/i18n/<language>/OrcaSlicer_<language>.po) as msgfmt compiles it
 * for the desktop app: fuzzy and untranslated entries are left out, so their
 * msgids show, as they do in OrcaSlicer.
 */
class OrcaCatalog private constructor(
    private val messages: Map<String, List<String>>,
    private val pluralIndex: (Long) -> Int,
) {
    /** _(): the translation of [msgid], with [context] when it has one. */
    fun translate(msgid: String, context: String = ""): String =
        if (msgid.isEmpty()) msgid else messages[key(context, msgid)]?.firstOrNull() ?: msgid

    /** _L_PLURAL(): the form of [msgid] for [count]; [msgidPlural] when untranslated and count is not 1. */
    fun translatePlural(msgid: String, msgidPlural: String, count: Long, context: String = ""): String {
        val forms = messages[key(context, msgid)]
        return forms?.getOrNull(pluralIndex(count))?.takeIf(String::isNotEmpty)
            ?: if (count == 1L) msgid else msgidPlural
    }

    /** The text as OrcaSlicer shows it: translated, with its placeholders filled. */
    fun format(text: OrcaText): String {
        val translated = if (text.msgidPlural.isEmpty()) {
            translate(text.msgid, text.context)
        } else {
            translatePlural(text.msgid, text.msgidPlural, text.count, text.context)
        }
        if (text.args.isEmpty()) return translated
        return fill(translated, if (text.translateArgs) text.args.map { translate(it) } else text.args)
    }

    /** Texts shown one after another, as OrcaSlicer concatenates the parts of a message. */
    fun format(texts: List<OrcaText>): String = texts.joinToString("") { format(it) }

    companion object {
        /** No translations: every text shows its msgid. */
        val EMPTY = OrcaCatalog(emptyMap()) { if (it == 1L) 0 else 1 }

        /** Reads the entries of a .po file. */
        fun parse(po: String): OrcaCatalog {
            val messages = HashMap<String, List<String>>()
            var pluralIndex: (Long) -> Int = { if (it == 1L) 0 else 1 }
            for (entry in entries(po)) {
                if (entry.msgid.isEmpty() && entry.context.isEmpty()) {
                    // The header: Plural-Forms tells the form for a count.
                    Regex("""Plural-Forms:.*?plural\s*=\s*([^;\n]+)""").find(entry.msgstr.firstOrNull().orEmpty())?.let {
                        pluralIndex = PluralExpression.parse(it.groupValues[1])
                    }
                    continue
                }
                if (entry.fuzzy || entry.msgstr.all(String::isEmpty)) continue
                messages[key(entry.context, entry.msgid)] = entry.msgstr
            }
            return OrcaCatalog(messages, pluralIndex)
        }

        private fun key(context: String, msgid: String) = if (context.isEmpty()) msgid else "$context$msgid"

        private class Entry {
            var fuzzy = false
            var context = ""
            var msgid = ""
            val msgstr = mutableListOf<String>()
        }

        private fun entries(po: String): List<Entry> {
            val entries = mutableListOf<Entry>()
            var entry = Entry()
            // Where the continuation lines of a string go.
            var append: ((String) -> Unit)? = null
            fun finish() {
                if (append != null) entries += entry
                entry = Entry()
                append = null
            }
            for (rawLine in po.lineSequence()) {
                val line = rawLine.trim()
                when {
                    line.isEmpty() -> finish()
                    line.startsWith("#~") -> Unit
                    line.startsWith("#,") -> {
                        if (append != null) finish()
                        if (line.substring(2).split(',').any { it.trim() == "fuzzy" }) entry.fuzzy = true
                    }
                    line.startsWith("#") -> if (append != null) finish()
                    line.startsWith("msgctxt ") -> {
                        if (append != null) finish()
                        entry.context = unquote(line.removePrefix("msgctxt "))
                        append = { entry.context += it }
                        // An entry starts with its context; the msgid follows.
                    }
                    line.startsWith("msgid ") -> {
                        if (append != null && entry.msgid.isNotEmpty() || entry.msgstr.isNotEmpty()) finish()
                        entry.msgid = unquote(line.removePrefix("msgid "))
                        append = { entry.msgid += it }
                    }
                    line.startsWith("msgid_plural ") -> append = {}
                    line.startsWith("msgstr[") -> {
                        val current = entry.msgstr.size
                        entry.msgstr += unquote(line.substringAfter("] "))
                        append = { entry.msgstr[current] = entry.msgstr[current] + it }
                    }
                    line.startsWith("msgstr ") -> {
                        val current = entry.msgstr.size
                        entry.msgstr += unquote(line.removePrefix("msgstr "))
                        append = { entry.msgstr[current] = entry.msgstr[current] + it }
                    }
                    line.startsWith("\"") -> append?.invoke(unquote(line))
                }
            }
            finish()
            return entries
        }

        /** A C string literal's content. */
        private fun unquote(literal: String): String {
            val text = literal.trim().removeSurrounding("\"")
            if ('\\' !in text) return text
            return buildString {
                var index = 0
                while (index < text.length) {
                    val char = text[index++]
                    if (char != '\\' || index == text.length) {
                        append(char)
                        continue
                    }
                    when (val escaped = text[index++]) {
                        'n' -> append('\n')
                        't' -> append('\t')
                        'r' -> append('\r')
                        else -> append(escaped)
                    }
                }
            }
        }

        private val printfSpecifier = Regex("""(\d+\$)?[-+ 0#']*\d*(?:\.\d+)?(?:hh|h|ll|l|L|z|j|t)?[diouxXeEfFgGaAcsp]""")
        private val boostPositional = Regex("""(\d+)%""")

        /**
         * wxString::Format() and boost::format: every printf specifier (%s,
         * %d, %0.3f, %1$s) and every %N% takes an argument, which the engine
         * already formatted; %% is a percent sign.
         */
        internal fun fill(format: String, args: List<String>): String = buildString {
            var next = 0
            var index = 0
            while (index < format.length) {
                val char = format[index]
                if (char != '%' || index + 1 == format.length) {
                    append(char)
                    index++
                    continue
                }
                if (format[index + 1] == '%') {
                    append('%')
                    index += 2
                    continue
                }
                val boost = boostPositional.matchAt(format, index + 1)
                if (boost != null) {
                    append(args.getOrElse(boost.groupValues[1].toInt() - 1) { "" })
                    index = boost.range.last + 1
                    continue
                }
                val printf = printfSpecifier.matchAt(format, index + 1)
                if (printf != null) {
                    val position = printf.groupValues[1].removeSuffix("$").toIntOrNull()
                    append(args.getOrElse(if (position != null) position - 1 else next++) { "" })
                    index = printf.range.last + 1
                    continue
                }
                append(char)
                index++
            }
        }
    }
}

/** A gettext Plural-Forms expression: C operators over n. */
internal object PluralExpression {
    fun parse(expression: String): (Long) -> Int {
        val parser = Parser(expression)
        val node = parser.ternary()
        check(parser.atEnd()) { "Unexpected text in the plural expression: $expression" }
        return { count -> node(count).toInt().coerceAtLeast(0) }
    }

    private class Parser(private val text: String) {
        private var index = 0

        fun atEnd(): Boolean {
            skipSpaces()
            return index == text.length
        }

        fun ternary(): (Long) -> Long {
            val condition = binary(0)
            if (!take("?")) return condition
            val yes = ternary()
            check(take(":")) { "Expected ':' in the plural expression: $text" }
            val no = ternary()
            return { n -> if (condition(n) != 0L) yes(n) else no(n) }
        }

        // From the loosest binding: ||, &&, equality, comparison, additive, multiplicative.
        private val levels = listOf(
            listOf("||"),
            listOf("&&"),
            listOf("==", "!="),
            listOf("<=", ">=", "<", ">"),
            listOf("+", "-"),
            listOf("*", "/", "%"),
        )

        private fun binary(level: Int): (Long) -> Long {
            if (level == levels.size) return unary()
            var left = binary(level + 1)
            while (true) {
                val operator = levels[level].firstOrNull { take(it) } ?: return left
                val right = binary(level + 1)
                val first = left
                left = when (operator) {
                    "||" -> { n -> if (first(n) != 0L || right(n) != 0L) 1L else 0L }
                    "&&" -> { n -> if (first(n) != 0L && right(n) != 0L) 1L else 0L }
                    "==" -> { n -> if (first(n) == right(n)) 1L else 0L }
                    "!=" -> { n -> if (first(n) != right(n)) 1L else 0L }
                    "<=" -> { n -> if (first(n) <= right(n)) 1L else 0L }
                    ">=" -> { n -> if (first(n) >= right(n)) 1L else 0L }
                    "<" -> { n -> if (first(n) < right(n)) 1L else 0L }
                    ">" -> { n -> if (first(n) > right(n)) 1L else 0L }
                    "+" -> { n -> first(n) + right(n) }
                    "-" -> { n -> first(n) - right(n) }
                    "*" -> { n -> first(n) * right(n) }
                    "/" -> { n -> right(n).let { divisor -> if (divisor == 0L) 0L else first(n) / divisor } }
                    else -> { n -> right(n).let { divisor -> if (divisor == 0L) 0L else first(n) % divisor } }
                }
            }
        }

        private fun unary(): (Long) -> Long {
            if (take("!")) {
                val operand = unary()
                return { n -> if (operand(n) == 0L) 1L else 0L }
            }
            if (take("(")) {
                val inner = ternary()
                check(take(")")) { "Expected ')' in the plural expression: $text" }
                return inner
            }
            skipSpaces()
            if (index < text.length && text[index] == 'n') {
                index++
                return { n -> n }
            }
            val start = index
            while (index < text.length && text[index].isDigit()) index++
            check(index > start) { "Unexpected text in the plural expression: $text" }
            val value = text.substring(start, index).toLong()
            return { value }
        }

        private fun take(token: String): Boolean {
            skipSpaces()
            // "<" must not take the first character of "<=", nor "!" of "!=".
            if (!text.startsWith(token, index)) return false
            val after = text.getOrNull(index + token.length)
            if ((token == "<" || token == ">" || token == "!") && after == '=') return false
            index += token.length
            return true
        }

        private fun skipSpaces() {
            while (index < text.length && text[index].isWhitespace()) index++
        }
    }
}
