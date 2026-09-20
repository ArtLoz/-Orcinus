package app.orcinus.shadow.core.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The expected strings are what OrcaSlicer's own RammingChart and RammingPanel
 * code writes for the same steps: their logic compiled on its own with
 * stand-ins for the wx point and rectangle types, at 100% scaling.
 */
class RammingChartTest {
    private val default = "120 100 6.6 6.8 7.2 7.6 7.9 8.2 8.7 9.4 9.9 10.0| " +
        "0.05 6.6 0.45 6.8 0.95 7.8 1.45 8.3 1.95 9.7 2.45 10 2.95 7.6 3.45 7.6 3.95 7.6 4.45 7.6 4.95 7.6"
    private val points = "0.05 6.6 0.45 6.8 0.95 7.8 1.45 8.3 1.95 9.7 2.45 10 2.95 7.6 3.45 7.6 3.95 7.6 4.45 7.6 4.95 7.6"

    @Test
    fun `the dialog closed at once writes the speeds read back from the spline`() {
        val chart = RammingChart.parse(default)

        assertEquals(2500, (chart.time * 1000).toInt())
        assertEquals(20, chart.volume.toInt())
        assertEquals(
            "120 100 6.67742 6.87097 7.16129 7.64516 7.93548 8.12903 8.70968 9.48387 9.96774 10.0645| $points",
            chart.parameters(),
        )
    }

    @Test
    fun `a longer ramming time samples more of the spline`() {
        val chart = RammingChart.parse(default)

        chart.setTime(3500)

        assertEquals(28, chart.volume.toInt())
        assertEquals(
            "120 100 6.67742 6.87097 7.16129 7.64516 7.93548 8.12903 8.6129 9.3871 10.0645 10.1613 9.48387 8.32258 7.64516 7.54839| $points",
            chart.parameters(),
        )
    }

    @Test
    fun `a dragged point changes its speed, and a uniform drag gives every point the same`() {
        val chart = RammingChart.parse(default)
        chart.setTime(3500)

        chart.drag(2, -40.0, uniform = false)

        assertEquals(32, chart.volume.toInt())
        assertEquals(
            "120 100 6.3871 6.77419 9.96774 14.0323 13.8387 10.1613 8.03226 8.90323 10.1613 10.2581 9.3871 8.22581 7.64516 7.54839| " +
                "0.05 6.6 0.45 6.8 0.95 15.5419 1.45 8.3 1.95 9.7 2.45 10 2.95 7.6 3.45 7.6 3.95 7.6 4.45 7.6 4.95 7.6",
            chart.parameters(),
        )

        chart.drag(4, 25.0, uniform = true)

        assertEquals(16, chart.volume.toInt())
        assertEquals(
            "120 100" + " 4.83871".repeat(14) + "| 0.05 4.86129 0.45 4.86129 0.95 4.86129 1.45 4.86129 1.95 4.86129 " +
                "2.45 4.86129 2.95 4.86129 3.45 4.86129 3.95 4.86129 4.45 4.86129 4.95 4.86129",
            chart.parameters(),
        )
    }

    @Test
    fun `a ramming too short to hold two points writes no speeds`() {
        val chart = RammingChart.parse(default)

        chart.setTime(250)

        assertEquals(0, chart.volume.toInt())
        assertEquals("120 100| $points", chart.parameters())
    }

    @Test
    fun `the line width and spacing are written as the spin controls hold them`() {
        val chart = RammingChart.parse(default)

        chart.lineWidth = 150
        chart.lineSpacing = 80

        assertEquals(true, chart.parameters().startsWith("150 80 6.67742 "))
    }

    @Test
    fun `floats are written as the C++ stream writes them`() {
        assertEquals("0.05", RammingChart.cppFloat(0.05f))
        assertEquals("10", RammingChart.cppFloat(10f))
        assertEquals("7.6", RammingChart.cppFloat(7.6f))
        assertEquals("10.0645", RammingChart.cppFloat(10.064516f))
        assertEquals("1e+06", RammingChart.cppFloat(1_000_000f))
        assertEquals("1.5e-05", RammingChart.cppFloat(0.000015f))
    }
}
