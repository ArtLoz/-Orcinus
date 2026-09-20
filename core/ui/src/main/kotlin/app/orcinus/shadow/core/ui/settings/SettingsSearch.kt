package app.orcinus.shadow.core.ui.settings

import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.SearchOption
import app.orcinus.shadow.core.model.SearchResult
import app.orcinus.shadow.core.model.SettingsMode

/**
 * Search::OptionsSearcher of the desktop app: the settings whose text the query
 * matches, best first. A setting reads "Group : Label" the way OptionsSearcher
 * builds it, and the page it sits in is what the desktop app puts in the
 * tooltip of the line.
 *
 * The texts are translated here, since the app holds OrcaSlicer's catalogue and
 * the user searches in their own language.
 */
object SettingsSearch {
    /** The score OptionsSearcher::search() accepts a match from. */
    private const val MINIMUM_SCORE = 90

    /** The " : " OptionsSearcher joins the parts of a line with. */
    const val SEPARATOR = " : "

    /**
     * [translate] turns OrcaSlicer's texts into the user's language, and [mode]
     * keeps the settings the tabs show in that mode, as the desktop app does.
     */
    fun search(
        query: String,
        options: List<SearchOption>,
        mode: SettingsMode,
        translate: (List<OrcaText>) -> String,
    ): List<SearchResult> {
        val shown = options.filter { it.mode.ordinal <= mode.ordinal }
        val results = shown.mapNotNull { option ->
            val text = lineOf(option, translate)
            if (query.isBlank()) {
                SearchResult(option = option, text = text, matches = emptyList(), score = 0)
            } else {
                FuzzyMatch.match(query.trimStart(), text)
                    ?.takeIf { it.score > MINIMUM_SCORE }
                    ?.let { match -> SearchResult(option = option, text = text, matches = match.matches, score = match.score) }
            }
        }
        // An empty query lists the settings in the tabs' order; a query ranks them.
        if (query.isBlank()) return results
        return results.sortedWith(compareByDescending<SearchResult> { it.score }.thenBy { it.text })
    }

    /** get_label() of OptionsSearcher::search(): the parts, without repeating one. */
    fun lineOf(option: SearchOption, translate: (List<OrcaText>) -> String): String {
        val parts = listOf(option.group, option.label).map(translate).filter(String::isNotEmpty)
        return parts.filterIndexed { at, part -> at == 0 || part != parts[at - 1] }.joinToString(SEPARATOR)
    }
}
