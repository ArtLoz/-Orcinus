package app.orcinus.shadow.network.printhost

import app.orcinus.shadow.core.model.BonjourReply
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals

class PrintHostDiscoveryTest {
    @Test
    fun `Bonjour finds an OctoPrint service with its address, port, host and version`() {
        val reply = header(answers = 1, additional = 3) +
            record(name("_octoprint", "_tcp", "local"), type = 12, data = name("octopi", "_octoprint", "_tcp", "local")) +
            record(
                name("octopi", "_octoprint", "_tcp", "local"),
                type = 0x21,
                data = bytes(0, 0, 0, 0, 0x13, 0x88) + name("octopi", "local"),
            ) +
            record(name("octopi", "_octoprint", "_tcp", "local"), type = 0x10, data = txt("path=/octo", "version=1.9.3", "api=0.1")) +
            record(name("octopi", "local"), type = 1, data = bytes(192, 168, 8, 50))

        val replies = lookupReplies(reply, InetAddress.getByName("192.168.8.2"), "_octoprint._tcp.local", setOf("version", "model"))

        assertEquals(
            listOf(BonjourReply("192.168.8.50", 5000, "octopi", "octopi.local", mapOf("path" to "/octo", "version" to "1.9.3"))),
            replies,
        )
        assertEquals("192.168.8.50:5000/octo", replies.single().fullAddress)
        // A service of another kind is not one of OctoPrint's.
        assertEquals(emptyList(), lookupReplies(reply, InetAddress.getByName("192.168.8.2"), "_http._tcp.local", emptySet()))
    }

    @Test
    fun `The Creality scan asks what cxmdns asks`() {
        // mdns_services_query: _services._dns-sd._udp.local., PTR, the unicast-response bit, class IN.
        assertEquals(
            "000000000001000000000000095f7365727669636573075f646e732d7364045f756470056c6f63616c00000c8001",
            CrealityHostDiscovery.SERVICES_QUERY.joinToString("") { "%02x".format(it) },
        )
    }

    @Test
    fun `The Creality scan reads the service type a K2 Plus answers with`() {
        // What the K2 Plus at 192.168.8.186 answered the query of the scan.
        val reply = hex(
            "000084000001000100000000095f7365727669636573075f646e732d7364045f756470056c6f63616c00000c8001" +
                "c00c000c00010000000a001b185f437265616c6974792d3333373231373238303644463844c01e",
        )
        val found = mutableListOf<Pair<String, String>>()

        CrealityHostDiscovery.machineInfo(reply, InetAddress.getByName("192.168.8.186"), CrealityHostDiscovery.PREFIXES, found)

        assertEquals(listOf("192.168.8.186" to "_Creality-3372172806DF8D._udp.local."), found)
        assertEquals("K2-DF8D", CrealityHostDiscovery.hostnameFromService(found.single().second))
        // Another device's reply lists its own types, which are not Creality's.
        val other = hex(
            "000084000001000200000000095f7365727669636573075f646e732d7364045f756470056c6f63616c00000c8001" +
                "c00c000c00010000000a00160e5f6170706c652d6d6f6264657632045f746370c023c00c000c00010000000a00110e5f72656d6f746570616972696e67c049",
        )
        CrealityHostDiscovery.machineInfo(other, InetAddress.getByName("192.168.8.5"), CrealityHostDiscovery.PREFIXES, found)
        assertEquals(1, found.size)
    }

    private fun bytes(vararg values: Int) = values.map { it.toByte() }.toByteArray()

    private fun hex(text: String) = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun header(answers: Int, additional: Int) = bytes(0, 0, 0x84, 0, 0, 0, 0, answers, 0, 0, 0, additional)

    private fun name(vararg labels: String) =
        labels.fold(ByteArray(0)) { out, label -> out + label.length.toByte() + label.toByteArray() } + 0.toByte()

    private fun txt(vararg entries: String) = entries.fold(ByteArray(0)) { out, entry -> out + entry.length.toByte() + entry.toByteArray() }

    private fun record(name: ByteArray, type: Int, data: ByteArray) =
        name + bytes(0, type, 0x80, 1, 0, 0, 0, 120, data.size shr 8, data.size and 0xff) + data
}
