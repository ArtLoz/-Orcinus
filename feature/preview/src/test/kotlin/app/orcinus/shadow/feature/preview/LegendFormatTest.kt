package app.orcinus.shadow.feature.preview

import kotlin.test.Test
import kotlin.test.assertEquals

class LegendFormatTest {
    @Test
    fun `times read as short_time(get_time_dhms()) writes them`() {
        assertEquals("0s", LegendFormat.shortTime(0f))
        assertEquals("<1s", LegendFormat.shortTime(0.4f))
        assertEquals("1s", LegendFormat.shortTime(1f))
        assertEquals("45s", LegendFormat.shortTime(45.7f))
        assertEquals("12m9s", LegendFormat.shortTime(729.2f))
        assertEquals("2h5m", LegendFormat.shortTime(2 * 3_600f + 5 * 60f + 29f))
        // With hours, 30 seconds and more round the minutes up, carrying into hours and days.
        assertEquals("2h6m", LegendFormat.shortTime(2 * 3_600f + 5 * 60f + 30f))
        assertEquals("1d0h0m", LegendFormat.shortTime(23 * 3_600f + 59 * 60f + 45f))
        assertEquals("1d2h3m", LegendFormat.shortTime(86_400f + 2 * 3_600f + 3 * 60f))
    }

    @Test
    fun `shares, distances, counts and weights read as the legend writes them`() {
        assertEquals("0", LegendFormat.percent(0f))
        assertEquals("<0.1", LegendFormat.percent(0.0005f))
        assertEquals("19.3", LegendFormat.percent(0.193f))
        assertEquals("845mm", LegendFormat.distance(845.4f, imperial = false))
        assertEquals("8.45m", LegendFormat.distance(8_450f, imperial = false))
        assertEquals("362", LegendFormat.compactCount(362))
        assertEquals("1.2K", LegendFormat.compactCount(1_234))
        assertEquals("3M", LegendFormat.compactCount(3_000_000))
        assertEquals("3.61g", LegendFormat.compactWeight(3.611, imperial = false))
        assertEquals("1.50kg", LegendFormat.compactWeight(1_500.0, imperial = false))
        assertEquals("0.35m", LegendFormat.meters(0.354, imperial = false))
        assertEquals("0.20", LegendFormat.decimal(0.2f, 2))
    }

    @Test
    fun `imperial units write inches and ounces`() {
        assertEquals("33.28in", LegendFormat.distance(845.4f, imperial = true))
        assertEquals("1.00 oz", LegendFormat.compactWeight(28.34952, imperial = true))
        assertEquals("39.37in", LegendFormat.meters(1.0, imperial = true))
        assertEquals("39.37 in", LegendFormat.spacedMeters(1.0, imperial = true))
    }
}
