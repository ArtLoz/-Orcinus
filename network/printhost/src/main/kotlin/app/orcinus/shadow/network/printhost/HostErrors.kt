package app.orcinus.shadow.network.printhost

import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.PrintHostTestOutcome
import app.orcinus.shadow.core.model.PrintHostUploadOutcome
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * PrintHost::format_error(): what a request that failed says. An answer with
 * an HTTP status is "HTTP <status>: <body>", untranslated; a request that got
 * no answer is curl's error, of which a timeout, a host that cannot be
 * resolved and a reset connection are worded for the user.
 */
internal fun formatError(error: Throwable): List<OrcaText> = when {
    error is HttpStatusException -> listOf(OrcaText("HTTP ${error.status}: ${error.body}"))
    // curl:Timeout was reached
    error is SocketTimeoutException -> listOf(OrcaText(TIMED_OUT))
    // curl:Couldn't resolve host name
    error is UnknownHostException -> listOf(OrcaText(UNRESOLVED))
    // Connection was reset
    error is SocketException && error.message.orEmpty().contains("reset", ignoreCase = true) -> listOf(OrcaText(INTERRUPTED))
    else -> listOf(OrcaText(error.message ?: NO_ANSWER))
}

/** format_error(body, error, 0) of a request that did not fail: [msgid] as it is. */
internal fun orcaError(msgid: String, vararg args: String): List<OrcaText> = listOf(OrcaText(msgid, args.toList()))

internal fun uploadFailure(text: List<OrcaText>): PrintHostUploadOutcome.Failure = PrintHostUploadOutcome.Failure(english(text), text)

internal fun uploadFailure(error: Throwable): PrintHostUploadOutcome.Failure = uploadFailure(formatError(error))

internal fun testFailure(text: List<OrcaText>): PrintHostTestOutcome.Failure = PrintHostTestOutcome.Failure(english(text), text)

internal fun testFailure(error: Throwable): PrintHostTestOutcome.Failure = testFailure(formatError(error))

internal fun PrintHostTestOutcome.Failure.asUpload(): PrintHostUploadOutcome.Failure = PrintHostUploadOutcome.Failure(message, text)

/** Orca's texts as the desktop shows them untranslated: each msgid with its %s and %1% filled. */
internal fun english(text: List<OrcaText>): String = text.joinToString("") { part ->
    var next = 0
    PLACEHOLDER.replace(part.msgid) { match ->
        when {
            match.value == "%%" -> "%"
            match.groupValues[1].isNotEmpty() -> part.args.getOrElse(match.groupValues[1].toInt() - 1) { "" }
            else -> part.args.getOrElse(next++) { "" }
        }
    }.takeIf { part.args.isNotEmpty() } ?: part.msgid
}

private val PLACEHOLDER = Regex("""%%|%(\d+)%|%[sd]""")

/** format_error()'s three worded curl errors. */
internal const val TIMED_OUT =
    "Connection timed out. Please check if the printer and computer network are functioning properly, and confirm that they are on the same network."
internal const val UNRESOLVED = "The Hostname/IP/URL could not be parsed, please check it and try again."
internal const val INTERRUPTED = "File/data transfer interrupted. Please check the printer and network, then try it again."

/** The app's own words for a request that failed without saying why. */
internal const val NO_ANSWER = "The printer did not answer"
