package app.orcinus.shadow.network.printhost

import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * SerialMessage: a line of G-code the console sends and waits for "ok" to
 * ([COMMAND], which gets the line ending), or raw data it only writes
 * ([DATA]), such as the pieces of a file.
 */
data class SerialMessage(val message: String, val type: Type = Type.COMMAND) {
    enum class Type { COMMAND, DATA }
}

/** What talks to the console of a printer's board, which keeps the hosts that use one testable. */
interface ConsoleClient {
    /**
     * run_queue(): the messages in order, to [host]:[port], with a pause of
     * [queueDelayMillis] before every one (set_tcp_queue_delay()); the error
     * when the exchange failed.
     */
    suspend fun run(host: String, port: Int, messages: List<SerialMessage>, queueDelayMillis: Long = 0): Result<Unit>
}

/**
 * OrcaSlicer's TCPConsole: a telnet-like console of a printer's board, which
 * the MKS host sends its G-code to. Every command is written with the line
 * ending and answered with lines until one reads "ok"; data is only written.
 * The run fails at the first error, and on a command that goes unanswered.
 */
class TcpConsole(
    private val newline: String = "\n",
    private val doneString: String = "ok",
    private val connectTimeoutMillis: Int = 5_000,
    private val readTimeoutMillis: Int = 10_000,
) : ConsoleClient {
    override suspend fun run(host: String, port: Int, messages: List<SerialMessage>, queueDelayMillis: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), connectTimeoutMillis)
                socket.soTimeout = readTimeoutMillis
                val output = socket.getOutputStream()
                val input = socket.getInputStream()
                for (message in messages) {
                    if (queueDelayMillis > 0) delay(queueDelayMillis)
                    val sent = if (message.type == SerialMessage.Type.COMMAND) message.message + newline else message.message
                    output.write(sent.toByteArray(Charsets.ISO_8859_1))
                    output.flush()
                    if (message.type == SerialMessage.Type.COMMAND) waitDone(input)
                }
            }
            Result.success(Unit)
        } catch (_: SocketTimeoutException) {
            Result.failure(IOException(TIMED_OUT))
        } catch (error: IOException) {
            Result.failure(error)
        } catch (error: IllegalArgumentException) {
            Result.failure(IOException(error.message ?: "Invalid address", error))
        }
    }

    /** handle_read(): the lines the board answers, until the one that says it is done. */
    private fun waitDone(input: InputStream) {
        while (true) {
            val line = readLine(input) ?: throw IOException("End of file")
            if (line.trim().lowercase(Locale.ROOT) == doneString) return
        }
    }

    /** async_read_until(newline): one line of the answer, without its ending. */
    private fun readLine(input: InputStream): String? {
        val line = StringBuilder()
        while (true) {
            val byte = input.read()
            if (byte < 0) return if (line.isEmpty()) null else line.toString()
            line.append(byte.toChar())
            if (line.endsWith(newline)) return line.substring(0, line.length - newline.length)
        }
    }

    private companion object {
        /** boost::asio::error::timed_out's message. */
        const val TIMED_OUT = "Connection timed out"
    }
}
