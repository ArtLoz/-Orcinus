package app.orcinus.shadow.core.ui.orca

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.max

/**
 * std::ostream << double, as OrcaSlicer's streams and boost::format's "%1%"
 * write a number: %g with six significant digits.
 */
fun cppNumber(value: Double): String {
    if (value.isNaN()) return "nan"
    if (value.isInfinite()) return if (value > 0) "inf" else "-inf"
    if (value == 0.0) return if (1.0 / value < 0) "-0" else "0"
    val rounded = BigDecimal(value).round(MathContext(6, RoundingMode.HALF_EVEN))
    val exponent = rounded.precision() - rounded.scale() - 1
    if (exponent < -4 || exponent >= 6) {
        val digits = rounded.unscaledValue().abs().toString().trimEnd('0')
        val mantissa = if (digits.length > 1) digits[0] + "." + digits.substring(1) else digits
        val sign = if (rounded.signum() < 0) "-" else ""
        return sign + mantissa + "e" + (if (exponent < 0) "-" else "+") + abs(exponent).toString().padStart(2, '0')
    }
    val plain = rounded.setScale(max(0, 5 - exponent), RoundingMode.HALF_EVEN).toPlainString()
    return if (plain.contains('.')) plain.trimEnd('0').trimEnd('.') else plain
}
