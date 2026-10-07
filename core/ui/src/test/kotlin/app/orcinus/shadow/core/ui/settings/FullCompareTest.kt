package app.orcinus.shadow.core.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FullCompareTest {
    @Test
    fun `a value of one line under 30 characters stays whole`() {
        assertEquals("0.2", FullCompare.shortValue("0.2"))
        assertEquals("", FullCompare.shortValue(""))
        assertEquals("a".repeat(29), FullCompare.shortValue("a".repeat(29)))
    }

    @Test
    fun `a value of 30 characters or more is cut at the 30th`() {
        assertEquals("x".repeat(30) + "...", FullCompare.shortValue("x".repeat(30)))
        assertEquals("y".repeat(30) + "...", FullCompare.shortValue("y".repeat(45)))
    }

    @Test
    fun `a value of several lines is cut at its first line, or at the 30th character before it`() {
        assertEquals("G28...", FullCompare.shortValue("G28\nG1 Z5 F3000"))
        assertEquals("M".repeat(30) + "...", FullCompare.shortValue("M".repeat(40) + "\nG28"))
    }

    @Test
    fun `a colour stays whole however long it is`() {
        val colours = "#FF0000;#00FF00;#0000FF;#FFFFFF;#000000"

        assertEquals(colours, FullCompare.shortValue(colours))
    }

    @Test
    fun `a row is long when either of its values is cut short`() {
        assertTrue(FullCompare.isLong("G28", "G28\nG29"))
        assertFalse(FullCompare.isLong("0.2", "0.3"))
    }

    @Test
    fun `the lines one value has and the other has not are marked`() {
        val old = "G28\nG1 Z5\nM104 S200"
        val new = "G28\nG1 Z10\nM104 S200"

        assertEquals(listOf(4..8), FullCompare.marks(old, new))
        assertEquals(listOf(4..9), FullCompare.marks(new, old))
    }

    @Test
    fun `a value of one line is compared by its words, each marked where it first stands`() {
        assertEquals(listOf(12..15), FullCompare.marks("rectilinear grid", "rectilinear honeycomb"))
        assertEquals(listOf(12..20), FullCompare.marks("rectilinear honeycomb", "rectilinear grid"))
        // wxString::First() finds the word "1" inside "a1" first.
        assertEquals(listOf(1..1), FullCompare.marks("a1 1", "a1"))
    }
}
