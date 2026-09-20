package app.orcinus.shadow.core.ui.settings

/**
 * fts_fuzzy_match.h of the desktop app, which OrcaSlicer's settings search
 * matches a query with: the characters of the pattern must appear in the text
 * in their order, and the score rewards matches that run together, that start a
 * word, and that leave little unmatched text behind.
 *
 * The desktop app matches over wide characters, so this matches over the text's
 * code points; a match is the index of the code point in the text.
 */
internal object FuzzyMatch {
    /** Its max_matches, which also caps how long a pattern may be. */
    private const val MAX_MATCHES = 255

    private const val RECURSION_LIMIT = 10

    // The bonuses and penalties of fuzzy_match_recursive().
    private const val SEQUENTIAL_BONUS = 75
    private const val SEPARATOR_BONUS = 10
    private const val FIRST_LETTER_BONUS = -30
    private const val LEADING_LETTER_PENALTY = -1
    private const val MAX_LEADING_LETTER_PENALTY = -15
    private const val UNMATCHED_LETTER_PENALTY = -1

    /** A match of a pattern in a text: its score and the places it matched. */
    data class Match(val score: Int, val matches: List<Int>)

    /** Null when the pattern does not appear in [text] at all. */
    fun match(pattern: String, text: String): Match? {
        val patternPoints = pattern.lowercase().codePoints().toArray()
        val textPoints = text.codePoints().toArray()
        if (patternPoints.isEmpty() || textPoints.isEmpty()) return null
        val lowered = IntArray(textPoints.size) { Character.toLowerCase(textPoints[it]) }
        val matches = IntArray(MAX_MATCHES + 1)
        val score = matchRecursive(
            pattern = patternPoints,
            patternIndex = 0,
            text = lowered,
            textIndex = 0,
            source = null,
            matches = matches,
            nextMatch = 0,
            recursionCount = 0,
        ) ?: return null
        return Match(score, matches.take(countMatches(matches, pattern = patternPoints.size, found = score)).toList())
    }

    /**
     * The recursive matcher: it takes the better of matching the character here
     * and skipping it, as the desktop app does.
     */
    private fun matchRecursive(
        pattern: IntArray,
        patternIndex: Int,
        text: IntArray,
        textIndex: Int,
        source: IntArray?,
        matches: IntArray,
        nextMatch: Int,
        recursionCount: Int,
    ): Int? {
        if (recursionCount + 1 >= RECURSION_LIMIT) return null
        if (patternIndex >= pattern.size || textIndex >= text.size) return null

        var bestRecursiveMatches: IntArray? = null
        var bestRecursiveScore = 0
        var firstMatch = true
        var patternAt = patternIndex
        var textAt = textIndex
        var matched = nextMatch

        while (patternAt < pattern.size && textAt < text.size) {
            if (pattern[patternAt] == text[textAt]) {
                if (matched + 1 > MAX_MATCHES) return null
                // "Copy-on-write" of the matches this call started with.
                if (firstMatch && source != null) {
                    source.copyInto(matches, endIndex = matched + 1)
                    firstMatch = false
                }
                // The recursive call that skips this match.
                val skipped = IntArray(MAX_MATCHES + 1)
                val skippedScore = matchRecursive(
                    pattern = pattern,
                    patternIndex = patternAt,
                    text = text,
                    textIndex = textAt + 1,
                    source = matches,
                    matches = skipped,
                    nextMatch = matched,
                    recursionCount = recursionCount + 1,
                )
                if (skippedScore != null && (bestRecursiveMatches == null || skippedScore > bestRecursiveScore)) {
                    bestRecursiveMatches = skipped
                    bestRecursiveScore = skippedScore
                }
                matches[matched++] = textAt
                patternAt++
            }
            textAt++
        }

        val complete = patternAt >= pattern.size
        val score = if (complete) score(text, matches, matched) else Int.MIN_VALUE
        return when {
            bestRecursiveMatches != null && (!complete || bestRecursiveScore > score) -> {
                bestRecursiveMatches.copyInto(matches)
                bestRecursiveScore
            }
            complete -> score
            else -> null
        }
    }

    /** The scoring of fuzzy_match_recursive() once the whole pattern matched. */
    private fun score(text: IntArray, matches: IntArray, count: Int): Int {
        var score = 100
        // The group a match belongs to starts after the previous ":" separator.
        var groupStart = matches[0]
        var index = matches[0]
        while (index >= 0 && text[index] != ':'.code) {
            if (text[index] != ' '.code && text[index] != '\t'.code) groupStart = index
            index--
        }
        score += if (matches[0] == groupStart) {
            FIRST_LETTER_BONUS
        } else {
            maxOf((matches[0] - groupStart) * LEADING_LETTER_PENALTY, MAX_LEADING_LETTER_PENALTY)
        }
        score += (text.size - groupStart - count) * UNMATCHED_LETTER_PENALTY

        var sequential = SEQUENTIAL_BONUS
        for (at in 0 until count) {
            val current = matches[at]
            if (current <= 0) continue
            if (at > 0 && current == matches[at - 1] + 1) {
                score += sequential
                // The bonus of a run grows the longer the run is.
                sequential = minOf(5 * SEQUENTIAL_BONUS, sequential + sequential / 3)
            } else {
                sequential = SEQUENTIAL_BONUS
            }
            val previous = text[current - 1]
            if (previous == '_'.code || previous == ' '.code) score += SEPARATOR_BONUS
        }
        return score
    }

    /** How many places matched: one per character of the pattern. */
    private fun countMatches(matches: IntArray, pattern: Int, found: Int): Int {
        if (found == Int.MIN_VALUE) return 0
        return minOf(pattern, matches.size)
    }
}
