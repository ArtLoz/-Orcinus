package app.orcinus.shadow.slicing.nativebridge

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The JNI bridge finds the classes it constructs by name, which R8 renames
 * unless a keep rule names them; a release build then aborts the :slicer
 * process the first time the bridge needs such a class. This test reads the
 * bridge and the rules, so a class the bridge starts constructing cannot be
 * forgotten.
 */
class ConsumerRulesTest {
    private val bridge = File(System.getProperty("orcinus.adapterSources")!!).resolve("native_bridge.cpp").readText()
    private val rules = File(System.getProperty("orcinus.consumerRules")!!).readText()

    @Test
    fun `every class the bridge constructs is kept`() {
        val constructed = BRIDGE_CLASS.findAll(bridge).map { it.groupValues[1] }.toSortedSet()
        val kept = KEPT_CLASS.findAll(rules).map { it.groupValues[1] }.toSet()
        assertTrue(constructed.isNotEmpty(), "The bridge constructs no class")
        val missing = constructed - kept
        assertTrue(missing.isEmpty(), "consumer-rules.pro keeps none of $missing")
    }

    private companion object {
        val BRIDGE_CLASS = Regex("""app/orcinus/shadow/slicing/nativebridge/(Native\w+)""")
        val KEPT_CLASS = Regex("""-keep\s+(?:class|interface)\s+app\.orcinus\.shadow\.slicing\.nativebridge\.(Native\w+)""")
    }
}
