package app.orcinus.shadow.network.printhost

import app.orcinus.shadow.core.model.CrealityHost
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * CrealityHostDiscovery: the Creality K-series printers of the local network.
 * Their firmware announces every printer under a service type of its own,
 * _Creality-<hex of its MAC>._udp, which a lookup of a fixed type cannot find,
 * so the scan asks for every service type there is (DNS-SD's
 * _services._dns-sd._udp.local) from a port of its own, which the printers
 * answer directly (Creality Print's cxmdns), keeps the types that name
 * Creality, and asks each printer its model at http://<ip>/info.
 *
 * Unlike cxmdns, which stops reading a reply at its first answer (it never
 * steps over the answer's data) and drops a reply whose first type is another
 * device's, the scan reads every answer of a reply; and it asks over IPv4
 * only, since cxmdns takes the address of an IPv6 reply up to its first colon
 * ("[fe80"), which no request can reach.
 */
class CrealityHostDiscovery(private val http: HttpClient = UrlConnectionHttpClient(PROBE_CONNECT_MILLIS, PROBE_MAX_MILLIS)) {
    /** CrealityHostDiscovery::scan(): the printers found, once no reply came for five seconds. */
    suspend fun scan(probe: Boolean = true): List<CrealityHost> {
        val raw = discoverServices(PREFIXES)
        // One printer may answer twice, from several interfaces.
        val hosts = raw.distinctBy { it.first }.map { (ip, answer) ->
            CrealityHost(ip = ip, serviceName = answer, hostname = hostnameFromService(answer))
        }
        return if (probe) hosts.map { probeInfo(it) } else hosts
    }

    /** probe_info(): the model and the MAC the printer reports, with a short timeout. */
    private suspend fun probeInfo(host: CrealityHost): CrealityHost {
        val body = http.get("http://${host.ip}/info", emptyMap()).getOrNull() ?: return host
        val info = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return host
        val modelCode = (info["model"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: host.modelCode
        val mac = (info["mac"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: host.mac
        val modelName = K2_MODELS[modelCode]
        return host.copy(
            modelCode = modelCode,
            mac = mac,
            cfsCapable = modelName != null,
            modelName = modelName ?: host.modelName,
        )
    }

    /**
     * cxnet::syncDiscoveryService(): the address and the service type of every
     * answer that names one of [prefixes], from a socket of every interface.
     */
    private suspend fun discoverServices(prefixes: List<String>): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val found = mutableListOf<Pair<String, String>>()
        val buffer = ByteBuffer.allocate(CAPACITY)
        Selector.open().use { selector ->
            val sockets = openClientSockets(selector)
            try {
                for (channel in sockets) send(channel, BonjourRequest.MCAST_IP4, SERVICES_QUERY)
                // It reads for as long as replies come, and five seconds more.
                var quietSince = System.nanoTime()
                while (System.nanoTime() - quietSince < QUIET_NANOS) {
                    if (selector.select(POLL_MILLIS) > 0) {
                        for (key in selector.selectedKeys()) {
                            receive(key.channel() as DatagramChannel, buffer) { datagram, from ->
                                machineInfo(datagram, from, prefixes, found)
                            }
                        }
                        selector.selectedKeys().clear()
                        quietSince = System.nanoTime()
                    }
                    ensureActive()
                }
            } finally {
                sockets.forEach { runCatching { it.close() } }
            }
        }
        found
    }

    /** open_client_sockets(): a socket on a port of its own for every interface with an IPv4 address. */
    private fun openClientSockets(selector: Selector): List<DatagramChannel> {
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces()?.toList().orEmpty() }.getOrDefault(emptyList())
            .filter { runCatching { it.isUp && it.supportsMulticast() && !it.isLoopback && !it.isPointToPoint }.getOrDefault(false) }
        return interfaces.filter { it.hasAddress(ipv4 = true) }.mapNotNull { nif ->
            openMulticast(ipv4 = true, port = 0, group = BonjourRequest.MCAST_IP4, nif = nif)
                ?.also { it.register(selector, SelectionKey.OP_READ) }
        }
    }

    internal companion object {
        /** The K2 family, which prints from CFS boxes, by the model code /info reports. */
        val K2_MODELS = mapOf("F008" to "K2 Plus", "F012" to "K2 Pro", "F021" to "K2")

        val PREFIXES = listOf("Creality", "creality")

        /** mdns_services_query: _services._dns-sd._udp.local., PTR, the unicast-response bit and class IN. */
        val SERVICES_QUERY: ByteArray = (
            listOf(0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0) +
                labels("_services", "_dns-sd", "_udp", "local") +
                listOf(0, PTR, 0x80, CLASS_IN)
            ).map { it.toByte() }.toByteArray()

        private const val PTR = 12
        private const val CLASS_IN = 1
        private const val CAPACITY = 2048
        private const val MAX_SUBSTRINGS = 64
        private const val QUIET_NANOS = 5_000_000_000L
        private const val POLL_MILLIS = 250L
        private const val PROBE_CONNECT_MILLIS = 2_000
        private const val PROBE_MAX_MILLIS = 4_000
        private val SERVICES_LABELS = listOf("_services", "_dns-sd", "_udp", "local")

        private fun labels(vararg names: String): List<Int> =
            names.flatMap { name -> listOf(name.length) + name.map { it.code } } + 0

        /**
         * hostname_from_service(): "K2-" and the last four characters of the
         * type's suffix, "_Creality-543324280CDB19._udp.local." being "K2-DB19".
         */
        fun hostnameFromService(serviceName: String): String {
            val dash = serviceName.lastIndexOf('-')
            if (dash < 0) return ""
            val dot = serviceName.indexOf('.', dash)
            if (dot < 0) return ""
            val suffix = serviceName.substring(dash + 1, dot)
            return when {
                suffix.length >= 4 -> "K2-" + suffix.takeLast(4)
                suffix.isEmpty() -> ""
                else -> "K2-$suffix"
            }
        }

        /**
         * recvMachineInfoFromSocket(): a reply to the query (its ID 0 and the
         * flags of an authoritative answer, 0x8400) whose PTR answers to
         * _services._dns-sd._udp.local name a type containing one of [prefixes].
         */
        fun machineInfo(buffer: ByteArray, from: InetAddress, prefixes: List<String>, found: MutableList<Pair<String, String>>) {
            val size = buffer.size
            if (size < HEADER_SIZE || from !is Inet4Address) return
            val queryId = u16(buffer, 0)
            val flags = u16(buffer, 2)
            val questions = u16(buffer, 4)
            val answers = u16(buffer, 6)
            // RFC 6762: the ID of a reply must be the query's, which is 0.
            if (queryId != 0 || flags != 0x8400) return
            var offset = HEADER_SIZE
            repeat(questions) {
                val (question, end) = readName(buffer, offset) ?: return
                if (!sameName(question, SERVICES_LABELS) || end + 4 > size) return
                val rtype = u16(buffer, end)
                val rclass = u16(buffer, end + 2)
                // A reply to the PTR question of class IN.
                if (rtype != PTR || (rclass and 0x7fff) != CLASS_IN) return
                offset = end + 4
            }
            repeat(answers) {
                val (name, end) = readName(buffer, offset) ?: return
                if (end + 10 > size) return
                val rtype = u16(buffer, end)
                val length = u16(buffer, end + 8)
                val data = end + 10
                if (length > size - data) return
                if (sameName(name, SERVICES_LABELS) && rtype == PTR && length >= 2) {
                    val (type, _) = readName(buffer, data) ?: return
                    val answer = type.joinToString("") { "$it." }
                    if (prefixes.any { answer.contains(it) }) found += from.hostAddress.orEmpty() to answer
                }
                offset = data + length
            }
        }

        private fun sameName(labels: List<String>, expected: List<String>): Boolean =
            labels.size == expected.size && labels.zip(expected).all { (a, b) -> a.equals(b, ignoreCase = true) }

        /**
         * mdns_string_extract() and mdns_string_skip(): the labels of the name
         * at [start], following compression pointers, and where the name ends.
         */
        fun readName(buffer: ByteArray, start: Int): Pair<List<String>, Int>? {
            val size = buffer.size
            val labels = mutableListOf<String>()
            var cur = start
            var end = -1
            var counter = 0
            while (true) {
                if (cur >= size || counter++ > MAX_SUBSTRINGS) return null
                var at = cur
                var ref = false
                var recursion = 0
                while ((buffer[at].toInt() and 0xc0) == 0xc0) {
                    if (size < at + 2) return null
                    at = u16(buffer, at) and 0x3fff
                    if (at >= size) return null
                    ref = true
                    if (++recursion > 16) return null
                }
                val length = buffer[at].toInt() and 0xff
                at++
                if (size < at + length) return null
                if (ref && end < 0) end = cur + 2
                if (length == 0) break
                labels += String(buffer, at, length, Charsets.UTF_8)
                cur = at + length
            }
            return labels to if (end < 0) cur + 1 else end
        }

        private fun u16(buffer: ByteArray, at: Int): Int = ((buffer[at].toInt() and 0xff) shl 8) or (buffer[at + 1].toInt() and 0xff)

        private const val HEADER_SIZE = 12
    }
}
