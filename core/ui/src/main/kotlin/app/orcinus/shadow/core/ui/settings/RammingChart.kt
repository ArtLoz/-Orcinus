package app.orcinus.shadow.core.ui.settings

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * OrcaSlicer's RammingPanel and its Chart (RammingChart.cpp, WipeTowerDialog.cpp):
 * the ramming speeds of a filament as a spline through points the user drags up
 * and down, and the parameter string the dialog writes back
 * ("<width %> <spacing %> <speeds...>| <time> <speed> ...").
 *
 * The desktop chart works in its own pixels — the spline is sampled once per
 * pixel column and the written speeds are read back from those samples — so
 * the numbers depend on the size of the chart. This port keeps that pixel grid
 * as the desktop app lays it out at 100% scaling (a 480 x 360 chart with a
 * 50 px legend) and keeps upstream's int, float and double arithmetic, so the
 * dialog writes what the desktop app writes; the screen only scales the grid.
 */
class RammingChart private constructor(
    /** The ramming line's width and spacing, in percent (m_ramming_*_multiplicator). */
    var lineWidth: Int,
    var lineSpacing: Int,
    buttons: List<Pair<Float, Float>>,
    rammingSpeedSize: Int,
) {
    private val buttonX = DoubleArray(buttons.size) { buttons[it].first.toDouble() }
    private val buttonY = DoubleArray(buttons.size) { buttons[it].second.toDouble() }

    /** visible_area: from 0 s to the ramming time, from 0 to 60 mm³/s. */
    var visibleWidth: Double = (SAMPLING * rammingSpeedSize).toDouble()
        private set
    val visibleHeight: Double = 60.0

    /** m_line_to_draw: the spline's height in chart pixels, a column per pixel of the chart. */
    var line: IntArray = IntArray(0)
        private set

    /** m_total_volume: the filament the ramming pushes out, mm³. */
    var volume: Float = 0f
        private set

    init {
        recalculateLine()
    }

    val buttonCount: Int get() = buttonX.size

    /** A draggable point in chart units: seconds and mm³/s. */
    fun button(index: Int): Pair<Double, Double> = buttonX[index] to buttonY[index]

    /** get_time(): the ramming time, in seconds. */
    val time: Float get() = visibleWidth.toFloat()

    /**
     * The time spin control (m_widget_time, milliseconds, 0 to 5000 in steps of
     * 250): set_xy_range() keeps whole quarters of a second.
     */
    fun setTime(milliseconds: Int) {
        var x: Float = (milliseconds * 0.001).toFloat()
        x = ((x / 0.25).toInt() * 0.25).toFloat()
        if (x >= 0) visibleWidth = (x - 0.0)
        recalculateLine()
    }

    /**
     * mouse_moved(): the point [index] dragged by [deltaY] chart pixels, down
     * being positive; the time of a point stays (fixed_x). With [uniform] every
     * point takes the dragged point's new speed, as the desktop app's Ctrl drag
     * gives a constant flow rate.
     */
    fun drag(index: Int, deltaY: Double, uniform: Boolean) {
        val newY = buttonY[index] - deltaY / RECT_HEIGHT * visibleHeight
        if (uniform) {
            for (b in buttonY.indices) buttonY[b] += newY - buttonY[b]
        } else {
            buttonY[index] += newY - buttonY[index]
        }
        recalculateLine()
    }

    /** RammingPanel::get_parameters(): the string the dialog writes into filament_ramming_parameters. */
    fun parameters(): String = buildString {
        append(lineWidth).append(' ').append(lineSpacing)
        for (speed in rammingSpeed(SAMPLING)) append(' ').append(cppFloat(speed))
        append('|')
        for (b in buttonX.indices) {
            append(' ').append(cppFloat(buttonX[b].toFloat())).append(' ').append(cppFloat(buttonY[b].toFloat()))
        }
    }

    /**
     * get_ramming_speed(): the speed every [sampling] seconds, averaged from the
     * spline's pixels at both ends of the step. Upstream reads outside the line
     * when fewer than two points lie in the time shown (a line of none); the
     * port writes no speeds then, which is no ramming.
     */
    fun rammingSpeed(sampling: Float): List<Float> {
        val speeds = mutableListOf<Float>()
        val numberOfSamples = (visibleWidth / sampling).roundToInt()
        if (numberOfSamples > 0 && line.isNotEmpty()) {
            val dx = (line.size - 1) / numberOfSamples
            for (j in 0 until numberOfSamples) {
                val left = screenToMathY(line[j * dx]).toFloat()
                val right = screenToMathY(line[(j + 1) * dx]).toFloat()
                speeds += (left + right) / 2f
            }
        }
        return speeds
    }

    /** math_to_screen(): chart pixels of a point, the y axis pointing down. */
    fun mathToScreen(x: Double, y: Double): Pair<Int, Int> {
        val screenX = ((x - 0.0) * (RECT_WIDTH / visibleWidth)).toInt() + RECT_LEFT
        var screenY = ((y - 0.0) * (RECT_HEIGHT / visibleHeight)).toInt()
        screenY *= -1
        return screenX to screenY + RECT_BOTTOM
    }

    /** screen_to_math() for a height in chart pixels. */
    fun screenToMathY(screenY: Int): Double {
        var y = (screenY - RECT_BOTTOM).toDouble()
        y *= -1
        y *= visibleHeight / RECT_HEIGHT
        return y + 0.0
    }

    /** recalculate_line(): the cubic spline through the points, with a flat start and end. */
    private fun recalculateLine() {
        volume = 0f
        val xs = mutableListOf<Int>()
        val ys = mutableListOf<Int>()
        for (b in buttonX.indices) {
            val (px, py) = mathToScreen(buttonX[b], buttonY[b])
            xs += px
            ys += py
            if (xs.size > 1 && xs.last() == xs[xs.size - 2]) {
                xs.removeAt(xs.lastIndex)
                ys.removeAt(ys.lastIndex)
            }
            if (xs.size > 1 && xs.last() > RECT_RIGHT) {
                xs.removeAt(xs.lastIndex)
                ys.removeAt(ys.lastIndex)
                break
            }
        }
        if (xs.size <= 1) {
            line = IntArray(0)
            return
        }
        val order = xs.indices.sortedBy { xs[it] }
        val px = IntArray(order.size) { xs[order[it]] }
        val py = IntArray(order.size) { ys[order[it]] }

        val n = px.size - 1
        val diag = FloatArray(n + 1) { 2f }
        val mu = FloatArray(n + 1)
        val lambda = FloatArray(n + 1)
        val h = FloatArray(n + 1)
        val rhs = FloatArray(n + 1)
        for (i in 1..n) h[i] = (px[i] - px[i - 1]).toFloat()
        for (i in 1..n - 1) {
            mu[i] = h[i] / (h[i] + h[i + 1])
            lambda[i] = 1f - mu[i]
            rhs[i] = 6 * (
                (py[i + 1] - py[i]).toFloat() / (h[i + 1] * (px[i + 1] - px[i - 1])) -
                    (py[i] - py[i - 1]).toFloat() / (h[i] * (px[i + 1] - px[i - 1]))
                )
        }
        // boundary_first_derivative: the first derivative is 0 at both ends.
        val endpointsDerivative = 0f
        lambda[0] = 1f
        mu[n] = 1f
        rhs[0] = (6f / h[1]) * ((py[0] - py[1]).toFloat() / (px[0] - px[1]) - endpointsDerivative)
        rhs[n] = (6f / h[n]) * (endpointsDerivative - (py[n - 1] - py[n]).toFloat() / (px[n - 1] - px[n]))
        for (i in 1..n) {
            val multiple = mu[i] / diag[i - 1]
            diag[i] -= multiple * lambda[i - 1]
            rhs[i] -= multiple * rhs[i - 1]
        }
        rhs[n] = rhs[n] / diag[n]
        for (i in n - 1 downTo 0) rhs[i] = (rhs[i] - lambda[i] * rhs[i + 1]) / diag[i]

        val drawn = IntArray(RECT_RIGHT - RECT_LEFT + 1)
        var i = 1
        var y: Float
        for (x in RECT_LEFT..RECT_RIGHT) {
            if (i < px.size - 1 && px[i] < x) ++i
            y = when {
                px[0] > x -> py[0].toFloat()
                px[n] < x -> py[n].toFloat()
                else -> {
                    val cubic = (rhs[i - 1].toDouble() * (px[i] - x).toDouble().pow(3) + rhs[i].toDouble() * (x - px[i - 1]).toDouble().pow(3)) /
                        (6 * h[i]).toDouble()
                    val left = (py[i - 1] - rhs[i - 1] * h[i] * h[i] / 6f) * (px[i] - x) / h[i]
                    val right = (py[i] - rhs[i] * h[i] * h[i] / 6f) * (x - px[i - 1]) / h[i]
                    (cubic + left + right).toFloat()
                }
            }
            var sample = y.toInt()
            sample = max(sample, RECT_TOP - 1)
            sample = min(sample, RECT_BOTTOM - 1)
            drawn[x - RECT_LEFT] = sample
            volume = (volume + (RECT_BOTTOM - sample) * (visibleWidth / RECT_WIDTH) * (visibleHeight / RECT_HEIGHT)).toFloat()
        }
        line = drawn
    }

    companion object {
        /** The seconds a written speed stands for (RammingPanel samples by 0.25 s). */
        const val SAMPLING = 0.25f

        // Chart's m_rect at em_unit 10: the 480 x 360 chart less its 50 px legend.
        const val RECT_LEFT = 50
        const val RECT_TOP = 0
        const val RECT_WIDTH = 430
        const val RECT_HEIGHT = 310
        const val RECT_RIGHT = RECT_LEFT + RECT_WIDTH - 1
        const val RECT_BOTTOM = RECT_TOP + RECT_HEIGHT - 1

        /** The whole chart, legend included, in the same pixels. */
        const val CHART_WIDTH = 480
        const val CHART_HEIGHT = 360
        const val LEGEND_SIDE = 50

        /**
         * RammingPanel's constructor: the multiplicators, the speeds (only
         * counted: their number is the time), and the points after the "|".
         */
        fun parse(parameters: String): RammingChart {
            val bar = parameters.indexOf('|')
            val head = (if (bar < 0) parameters else parameters.substring(0, bar)).trim().split(WHITESPACE).filter(String::isNotEmpty)
            val lineWidth = head.getOrNull(0)?.toIntOrNull() ?: 0
            val lineSpacing = head.getOrNull(1)?.toIntOrNull() ?: 0
            // while (stream >> dummy) ++ramming_speed_size;
            val speeds = head.drop(2).takeWhile { it.toFloatOrNull() != null }.size
            val tail = if (bar < 0) emptyList() else parameters.substring(bar + 1).trim().split(WHITESPACE).filter(String::isNotEmpty)
            val numbers = tail.map { it.toFloatOrNull() }.takeWhile { it != null }.map { it!! }
            val buttons = numbers.chunked(2).filter { it.size == 2 }.map { it[0] to it[1] }
            return RammingChart(lineWidth, lineSpacing, buttons, speeds)
        }

        private val WHITESPACE = Regex("\\s+")

        /** std::ostream << float: %g with six significant digits. */
        fun cppFloat(value: Float): String {
            if (value.isNaN()) return "nan"
            if (value.isInfinite()) return if (value > 0) "inf" else "-inf"
            if (value == 0f) return if (1f / value < 0) "-0" else "0"
            val rounded = BigDecimal(value.toDouble()).round(MathContext(6, RoundingMode.HALF_EVEN))
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
    }
}
