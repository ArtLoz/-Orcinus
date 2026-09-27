package app.orcinus.shadow.feature.preview

import app.orcinus.shadow.core.model.ImperialUnits.IN_TO_MM
import app.orcinus.shadow.core.model.ImperialUnits.OZ_TO_G
import java.util.Locale
import kotlin.math.abs

/**
 * The legend's number formats, as OrcaSlicer writes them with sprintf in the
 * C locale: libslic3r's get_time_dhms() and short_time(), and the helpers of
 * GCodeViewer::render_legend(). OrcaSlicer leaves their units untranslated.
 * With [imperial] units (use_inches) lengths are in inches and weights in
 * ounces, as render_legend() writes them.
 */
internal object LegendFormat {
    /** short_time(get_time_dhms(seconds)): "1d2h3m", "2h5m", "12m9s", "45s", "<1s". */
    fun shortTime(seconds: Float): String {
        var rest = seconds
        var days = (rest / 86_400f).toInt()
        rest -= days * 86_400f
        var hours = (rest / 3_600f).toInt()
        rest -= hours * 3_600f
        var minutes = (rest / 60f).toInt()
        rest -= minutes * 60f
        val wholeSeconds = rest.toInt()
        // get_time_dhms() keeps a fraction only for a time of at most one second.
        val fractionalSeconds = if (days == 0 && hours == 0 && minutes == 0 && rest <= 1f) rest else wholeSeconds.toFloat()
        if (days + hours > 0 && wholeSeconds >= 30) {
            if (++minutes == 60) {
                minutes = 0
                if (++hours == 24) {
                    hours = 0
                    ++days
                }
            }
        }
        return when {
            days > 0 -> "${days}d${hours}h${minutes}m"
            hours > 0 -> "${hours}h${minutes}m"
            minutes > 0 -> "${minutes}m${wholeSeconds}s"
            wholeSeconds >= 1 -> "${wholeSeconds}s"
            fractionalSeconds > 0f && fractionalSeconds < 1f -> "<1s"
            else -> "0s"
        }
    }

    /** The share columns: "0", "12.3", or "<0.1", without the percent sign. */
    fun percent(fraction: Float): String = when {
        fraction == 0f -> "0"
        fraction > 0.001f -> String.format(Locale.ROOT, "%.1f", fraction * 100f)
        else -> "<0.1"
    }

    /** format_distance(): inches, or millimetres below a metre and metres above. */
    fun distance(millimeters: Float, imperial: Boolean): String = when {
        imperial -> String.format(Locale.ROOT, "%.2fin", millimeters / IN_TO_MM.toFloat())
        abs(millimeters) < 1_000f -> String.format(Locale.ROOT, "%.0fmm", millimeters)
        else -> String.format(Locale.ROOT, "%.2fm", millimeters / 1_000f)
    }

    /** format_compact_count(): 999, 1.2K, 3M. */
    fun compactCount(value: Long): String {
        if (value < 1_000) return value.toString()
        val suffixes = listOf("", "K", "M", "B", "T", "P", "E")
        var index = 0
        var divisor = 1L
        while (index + 1 < suffixes.size && value / divisor >= 1_000) {
            divisor *= 1_000
            ++index
        }
        val whole = value / divisor
        val tenths = (value % divisor) * 10 / divisor
        return buildString {
            append(whole)
            if (tenths != 0L) append('.').append(tenths)
            append(suffixes[index])
        }
    }

    /** format_compact_weight(): ounces, or grams, kilograms, or tonnes with two decimals. */
    fun compactWeight(grams: Double, imperial: Boolean): String {
        if (imperial) return String.format(Locale.ROOT, "%.2f oz", grams / OZ_TO_G)
        var scaled = abs(grams)
        var unit = "g"
        if (scaled >= 1_000_000.0) {
            scaled /= 1_000_000.0
            unit = "t"
        } else if (scaled >= 1_000.0) {
            scaled /= 1_000.0
            unit = "kg"
        }
        return (if (grams < 0.0) "-" else "") + String.format(Locale.ROOT, "%.2f", scaled) + unit
    }

    /** Filament length in metres or inches, as the Usage column writes it. */
    fun meters(meters: Double, imperial: Boolean): String =
        if (imperial) String.format(Locale.ROOT, "%.2fin", inches(meters)) else String.format(Locale.ROOT, "%.2fm", meters)

    /** Filament length in metres or inches with a spaced unit, as the totals write it. */
    fun spacedMeters(meters: Double, imperial: Boolean): String =
        if (imperial) String.format(Locale.ROOT, "%.2f in", inches(meters)) else String.format(Locale.ROOT, "%.2f m", meters)

    private fun inches(meters: Double) = meters * 1_000.0 / IN_TO_MM

    /** A cost with two decimals. */
    fun cost(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

    /** A colour range value with the view type's decimals. */
    fun decimal(value: Float, decimals: Int): String = String.format(Locale.ROOT, "%.${decimals}f", value)
}
