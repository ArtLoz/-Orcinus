package app.orcinus.shadow.network.printhost

import app.orcinus.shadow.core.model.BonjourReply
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.StandardProtocolFamily
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn

/**
 * OrcaSlicer's Bonjour (Bonjour.cpp): a minimal mDNS / DNS-SD client that asks
 * the local network for the services of one kind — a PTR query of
 * _<service>._<protocol>.local sent to the mDNS group from port 5353 — and
 * answers with every service that replies with an SRV record, with the TXT
 * values asked for ("path" always).
 *
 * The query goes out [retries] times, [timeoutSeconds] apart, and the lookup
 * ends [timeoutSeconds] after the last one. There is a socket for every
 * network interface that takes multicast, for IPv4 and for IPv6, as the
 * desktop app opens one for every address of the machine.
 */
class Bonjour(
    private val service: String,
    private val protocol: String = "tcp",
    private val txtKeys: Set<String> = emptySet(),
    private val timeoutSeconds: Int = 10,
    private val retries: Int = 1,
) {
    /** Bonjour::lookup(): every reply as it comes; the flow ends when the lookup does. */
    fun lookup(): Flow<BonjourReply> = channelFlow {
        val request = BonjourRequest.makePtr(service, protocol) ?: return@channelFlow
        val serviceDn = "_$service._$protocol.local"
        val buffer = ByteBuffer.allocate(DnsMessage.MAX_SIZE)
        Selector.open().use { selector ->
            val sockets = openSockets(selector)
            try {
                var rounds = retries.coerceAtLeast(1)
                var deadline = 0L
                while (true) {
                    val now = System.nanoTime()
                    if (deadline == 0L || now >= deadline) {
                        // The timer: another round of queries, or the end.
                        if (rounds == 0) break
                        rounds--
                        deadline = now + timeoutSeconds * NANOS_PER_SECOND
                        for ((channel, group) in sockets) send(channel, group, request)
                        continue
                    }
                    // Short waits, so a closed dialog stops the lookup soon.
                    val wait = ((deadline - now) / NANOS_PER_MILLI).coerceIn(1, POLL_MILLIS)
                    if (selector.select(wait) > 0) {
                        for (key in selector.selectedKeys()) {
                            receive(key.channel() as DatagramChannel, buffer) { datagram, from ->
                                lookupReplies(datagram, from, serviceDn, txtKeys).forEach { trySend(it) }
                            }
                        }
                        selector.selectedKeys().clear()
                    }
                    ensureActive()
                }
            } finally {
                sockets.forEach { (channel, _) -> runCatching { channel.close() } }
            }
        }
    }.flowOn(Dispatchers.IO)

    /** The sockets of the interfaces, with the group each sends to. */
    private fun openSockets(selector: Selector): List<Pair<DatagramChannel, InetAddress>> {
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces()?.toList().orEmpty() }.getOrDefault(emptyList())
            .filter { runCatching { it.isUp && !it.isLoopback && it.supportsMulticast() }.getOrDefault(false) }
        val sockets = mutableListOf<Pair<DatagramChannel, InetAddress>>()
        for (ipv4 in listOf(true, false)) {
            val group = if (ipv4) BonjourRequest.MCAST_IP4 else BonjourRequest.MCAST_IP6
            for (nif in interfaces.filter { it.hasAddress(ipv4) }) {
                val channel = openMulticast(ipv4, BonjourRequest.MCAST_PORT, group, nif) ?: continue
                channel.register(selector, SelectionKey.OP_READ)
                sockets += channel to group
            }
        }
        return sockets
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000L
        const val NANOS_PER_MILLI = 1_000_000L
        const val POLL_MILLIS = 250L
    }
}

/** Whether the interface has an address of IPv4, or of IPv6. */
internal fun NetworkInterface.hasAddress(ipv4: Boolean): Boolean =
    inetAddresses.toList().any { it.address.size == if (ipv4) IPV4_SIZE else IPV6_SIZE }

/**
 * UdpSocket's setup: a socket bound to [port] of every address (0 for one of
 * its own), which joins the mDNS [group] on the interface and sends there.
 * Null when the interface does not let it.
 */
internal fun openMulticast(ipv4: Boolean, port: Int, group: InetAddress, nif: NetworkInterface): DatagramChannel? {
    val channel = DatagramChannel.open(if (ipv4) StandardProtocolFamily.INET else StandardProtocolFamily.INET6)
    return try {
        channel.setOption(StandardSocketOptions.SO_REUSEADDR, true)
        channel.bind(InetSocketAddress(InetAddress.getByName(if (ipv4) "0.0.0.0" else "::"), port))
        channel.setOption(StandardSocketOptions.IP_MULTICAST_IF, nif)
        channel.setOption(StandardSocketOptions.IP_MULTICAST_TTL, 1)
        channel.join(group, nif)
        channel.configureBlocking(false)
        channel
    } catch (_: IOException) {
        runCatching { channel.close() }
        null
    } catch (_: UnsupportedOperationException) {
        runCatching { channel.close() }
        null
    }
}

/** Sends [request] to the mDNS group; an interface that cannot send stays quiet, as the desktop app logs and goes on. */
internal fun send(channel: DatagramChannel, group: InetAddress, request: ByteArray) {
    try {
        channel.send(ByteBuffer.wrap(request), InetSocketAddress(group, BonjourRequest.MCAST_PORT))
    } catch (_: IOException) {
    }
}

/** Every datagram waiting on [channel], with the address it came from. */
internal inline fun receive(channel: DatagramChannel, buffer: ByteBuffer, onDatagram: (ByteArray, InetAddress) -> Unit) {
    while (true) {
        buffer.clear()
        val from = try {
            channel.receive(buffer) as? InetSocketAddress
        } catch (_: IOException) {
            null
        } ?: return
        buffer.flip()
        onDatagram(ByteArray(buffer.remaining()).also { buffer.get(it) }, from.address)
    }
}

private const val IPV4_SIZE = 4
private const val IPV6_SIZE = 16
