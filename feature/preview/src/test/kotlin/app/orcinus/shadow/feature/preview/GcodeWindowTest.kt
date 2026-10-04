package app.orcinus.shadow.feature.preview

import kotlin.test.Test
import kotlin.test.assertEquals

class GcodeWindowTest {
    @Test
    fun `a line splits as update_lines() splits it`() {
        assertEquals(GcodeWindowLine(7, "G1", " X10.5 Y20 E0.3", ""), GcodeWindowLine.parse(7, "G1 X10.5 Y20 E0.3\n"))
        // The comment is what follows the last ';', and spaces side by side make one.
        assertEquals(GcodeWindowLine(8, "G1", " X1 ", "; perimeter"), GcodeWindowLine.parse(8, "G1  X1 ;; perimeter\r\n"))
        assertEquals(GcodeWindowLine(9, "", "", ";LAYER_CHANGE"), GcodeWindowLine.parse(9, ";LAYER_CHANGE\n"))
        // Longer than 55 characters with its break: 52 of them and "...".
        val long = "M117 " + "a".repeat(60) + "\n"
        assertEquals(GcodeWindowLine(10, "M117", " " + "a".repeat(47) + "...", ""), GcodeWindowLine.parse(10, long))
    }

    @Test
    fun `the window holds the lines around the current one, inside the file`() {
        assertEquals(98..102, gcodeWindowRange(current = 100, lines = 5, count = 1_000))
        // At the start, the lines from the first; at the end, GCodeWindow::render() stops a line short of the count.
        assertEquals(1..4, gcodeWindowRange(current = 1, lines = 5, count = 1_000))
        assertEquals(995..999, gcodeWindowRange(current = 999, lines = 5, count = 1_000))
    }
}
