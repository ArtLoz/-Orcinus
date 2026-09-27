package app.orcinus.shadow.network.printhost

import app.orcinus.shadow.core.model.BonjourReply
import java.net.InetAddress

/*
 * The DNS decoding of OrcaSlicer's Bonjour.cpp, which only lets fully correct
 * replies through: the decoder gives up the moment it meets anything odd
 * (RFC 6762, RFC 6763).
 */

/** Where the decoder is in the datagram, which the decoding functions move on. */
internal class Cursor(var offset: Int)

private fun ByteArray.u8(at: Int): Int = this[at].toInt() and 0xff

private fun ByteArray.u16(at: Int): Int = (u8(at) shl 8) or u8(at + 1)

private fun ByteArray.u32(at: Int): Long = (u16(at).toLong() shl 16) or u16(at + 2).toLong()

/** DnsName::decode(): the labels of a name joined with dots, following compression pointers. */
internal fun decodeDnsName(buffer: ByteArray, cursor: Cursor, depth: Int = 0): String? {
    // Check offset sanity, and the depth, which keeps names from nesting too deep or going round.
    if (cursor.offset + 1 >= buffer.size || depth >= DNS_MAX_RECURSION) return null
    val res = StringBuilder()
    val bsize = buffer.size
    while (true) {
        val ptr = cursor.offset
        var len = buffer.u8(ptr)
        if ((len and 0xc0) != 0) {
            // A pointer to a name elsewhere in the datagram.
            if (ptr + 1 >= bsize) return null
            val pointer = Cursor(((len and 0x3f) shl 8) or buffer.u8(ptr + 1))
            val nested = decodeDnsName(buffer, pointer, depth + 1) ?: return null
            if (res.isNotEmpty()) res.append('.')
            res.append(nested)
            cursor.offset += 2
            return res.toString()
        } else if (len == 0) {
            // The end of the name.
            cursor.offset++
            break
        } else {
            len = len and 0x3f
            if (len + cursor.offset + 1 >= bsize) return null
            if (res.isNotEmpty()) res.append('.')
            for (at in ptr + 1..ptr + len) {
                val c = buffer.u8(at)
                if (c in 0x20..0x7f) res.append(c.toChar()) else return null
            }
            cursor.offset += len + 1
        }
    }
    return res.takeIf { it.isNotEmpty() }?.toString()
}

/** DnsResource: a record of the datagram, with where its data starts. */
private class DnsResource(val name: String, val type: Int, val data: ByteArray, val dataOffset: Int)

private fun decodeResource(buffer: ByteArray, cursor: Cursor): DnsResource? {
    val bsize = buffer.size
    if (cursor.offset + 1 >= bsize) return null
    val name = decodeDnsName(buffer, cursor) ?: return null
    if (cursor.offset + 10 >= bsize) return null
    val at = cursor.offset
    val type = buffer.u16(at)
    val rdlength = buffer.u16(at + 8)
    cursor.offset += 10
    if (cursor.offset + rdlength > bsize) return null
    val dataOffset = cursor.offset
    cursor.offset += rdlength
    return DnsResource(name, type, buffer.copyOfRange(dataOffset, dataOffset + rdlength), dataOffset)
}

/** DnsRR_SRV: the port of a service and the host it runs on. */
internal class DnsSrv(val port: Int, val hostname: String)

private fun decodeSrv(buffer: ByteArray, rr: DnsResource): DnsSrv? {
    if (rr.data.size < SRV_MIN_SIZE) return null
    val hostname = decodeDnsName(buffer, Cursor(rr.dataOffset + 6)) ?: return null
    return DnsSrv(rr.data.u16(4), hostname)
}

/** DnsRR_TXT: the key=value strings asked for, and "path" always. */
private fun decodeTxt(rr: DnsResource, txtKeys: Set<String>): Map<String, String>? {
    val data = rr.data
    if (data.size < 2) return null
    val res = mutableMapOf<String, String>()
    var it = 0
    while (it < data.size) {
        val valSize = data.u8(it)
        if (valSize == 0 || it + valSize >= data.size) return null
        it++
        val itEnd = it + valSize
        val itEq = (it until itEnd).firstOrNull { data[it] == '='.code.toByte() } ?: itEnd
        if (itEq > it && itEq < itEnd - 1) {
            val key = String(data, it, itEq - it, Charsets.UTF_8)
            if (key in txtKeys || key == "path") {
                res[key] = String(data, itEq + 1, itEnd - itEq - 1, Charsets.UTF_8)
            }
        }
        it = itEnd
    }
    return res
}

/** DnsSDPair: what a service's SRV and TXT records say. */
internal class DnsSdPair(var srv: DnsSrv? = null, var txt: Map<String, String>? = null)

/** DnsMessage: the addresses and the services a datagram names. */
internal class DnsMessage private constructor() {
    var a: InetAddress? = null
    var aaaa: InetAddress? = null

    /** DnsSDMap, a std::map, in the order of the names. */
    val sdmap = sortedMapOf<String, DnsSdPair>()

    companion object {
        const val MAX_SIZE = 4096
        private const val MAX_ANS = 30

        fun decode(buffer: ByteArray, txtKeys: Set<String>): DnsMessage? {
            val size = buffer.size
            if (size < DNS_HEADER_SIZE + DNS_QUESTION_MIN_SIZE || size > MAX_SIZE) return null
            val qdcount = buffer.u16(4)
            val ancount = buffer.u16(6)
            val rrcount = ancount + buffer.u16(8) + buffer.u16(10)
            if (qdcount > 1 || ancount > MAX_ANS) return null
            val res = DnsMessage()
            val cursor = Cursor(DNS_HEADER_SIZE)
            if (qdcount == 1) {
                // DnsQuestion: its name, its type and its class, which the lookup does not look at.
                if (decodeDnsName(buffer, cursor) != null) cursor.offset += 4
            }
            repeat(rrcount) {
                val rr = decodeResource(buffer, cursor) ?: return null
                res.parse(buffer, rr, txtKeys)
            }
            return res
        }
    }

    private fun parse(buffer: ByteArray, rr: DnsResource, txtKeys: Set<String>) {
        when (rr.type) {
            TYPE_A -> if (rr.data.size == IPV4_BYTES) a = InetAddress.getByAddress(rr.data)
            TYPE_AAAA -> if (rr.data.size == IPV6_BYTES) aaaa = InetAddress.getByAddress(rr.data)
            TYPE_SRV -> decodeSrv(buffer, rr)?.let { srv -> sdmap.getOrPut(rr.name) { DnsSdPair() }.srv = srv }
            TYPE_TXT -> decodeTxt(rr, txtKeys)?.let { txt -> sdmap.getOrPut(rr.name) { DnsSdPair() }.txt = txt }
        }
    }
}

/**
 * LookupSession::handle_receive(): the services of [serviceDn] a datagram
 * from [from] names with an SRV record, at the address its A record gives, or
 * its AAAA record, or the one it came from.
 */
internal fun lookupReplies(datagram: ByteArray, from: InetAddress, serviceDn: String, txtKeys: Set<String>): List<BonjourReply> {
    val message = DnsMessage.decode(datagram, txtKeys) ?: return emptyList()
    val ip = message.a ?: message.aaaa ?: from
    return message.sdmap.mapNotNull { (name, pair) ->
        val srv = pair.srv ?: return@mapNotNull null
        val serviceName = stripServiceDn(name, serviceDn).ifEmpty { return@mapNotNull null }
        BonjourReply(ip.hostAddress.orEmpty(), srv.port, serviceName, srv.hostname, pair.txt.orEmpty())
    }
}

/** strip_service_dn(): the name of the service without the kind's domain, or nothing when it is not of that kind. */
internal fun stripServiceDn(serviceName: String, serviceDn: String): String {
    if (serviceName.length <= serviceDn.length) return ""
    val needle = serviceName.lastIndexOf(serviceDn)
    return if (needle == serviceName.length - serviceDn.length) serviceName.substring(0, needle - 1) else ""
}

/** BonjourRequest: the queries the lookup sends to the mDNS group. */
internal object BonjourRequest {
    val MCAST_IP4: InetAddress = InetAddress.getByAddress(byteArrayOf(224.toByte(), 0, 0, 251.toByte()))
    val MCAST_IP6: InetAddress = InetAddress.getByAddress(ByteArray(16).also { it[0] = 0xff.toByte(); it[1] = 0x02; it[15] = 0xfb.toByte() })
    const val MCAST_PORT = 5353

    /** make_PTR(): the services of _<service>._<protocol>.local, of any class. */
    fun makePtr(service: String, protocol: String): ByteArray? {
        if (service.length > 15 || protocol.length > 15) return null
        val data = mutableListOf<Byte>()
        // Query ID (zero for mDNS), flags, one question, no answer, authority or additional records.
        data += listOf(0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0).map { it.toByte() }
        data += (service.length + 1).toByte()
        data += '_'.code.toByte()
        data += service.toByteArray(Charsets.US_ASCII).toList()
        data += (protocol.length + 1).toByte()
        data += '_'.code.toByte()
        data += protocol.toByteArray(Charsets.US_ASCII).toList()
        // "local", its terminator, type PTR, class ANY.
        data += listOf(5, 'l'.code, 'o'.code, 'c'.code, 'a'.code, 'l'.code, 0, 0, 0x0c, 0, 0xff).map { it.toByte() }
        return data.toByteArray()
    }
}

private const val TYPE_A = 0x1
private const val TYPE_AAAA = 0x1c
private const val TYPE_SRV = 0x21
private const val TYPE_TXT = 0x10
private const val DNS_MAX_RECURSION = 10
private const val DNS_HEADER_SIZE = 12
private const val DNS_QUESTION_MIN_SIZE = 5
private const val SRV_MIN_SIZE = 8
private const val IPV4_BYTES = 4
private const val IPV6_BYTES = 16
