package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.CloudLoginOutcome
import app.orcinus.shadow.core.model.FlashforgeSlotsOutcome
import app.orcinus.shadow.core.model.HostPrintersOutcome
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.OutputPath
import app.orcinus.shadow.core.model.PhysicalPrinter
import app.orcinus.shadow.core.model.PrintHostJobState
import app.orcinus.shadow.core.model.PrintHostTestOutcome
import app.orcinus.shadow.core.model.PrintHostUploadOutcome
import app.orcinus.shadow.core.model.PrintOptions
import app.orcinus.shadow.core.model.Printer3dOsListsOutcome
import app.orcinus.shadow.core.model.PrinterSlotsOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class PrintHostJobQueueTest {
    @Test
    fun `uploads go one after another, and a cancelled one ends cancelled without its file`() = runBlocking {
        val sender = HeldSender()
        val files = Files()
        val queue = PrintHostJobQueue(sender, files, CoroutineScope(SupervisorJob() + coroutineContext))

        queue.enqueue(printer, startPrint = false, options("one.gcode")) { Result.success(it.newUpload(".gcode")) }
        queue.enqueue(printer, startPrint = false, options("two.gcode")) { Result.success(it.newUpload(".gcode")) }
        queue.enqueue(printer, startPrint = false, options("three.gcode")) { Result.success(it.newUpload(".gcode")) }
        withTimeout(5_000) { sender.started.await() }

        // The first is going out, the others wait; a waiting one is dropped with its file.
        assertEquals(listOf(PrintHostJobState.UPLOADING, PrintHostJobState.QUEUED, PrintHostJobState.QUEUED), queue.jobs.value.map { it.state })
        queue.cancel(2)
        // The one going out is aborted.
        queue.cancel(1)
        val ended = withTimeout(5_000) { queue.jobs.first { jobs -> jobs.none { it.state.cancellable } } }

        assertEquals(listOf(PrintHostJobState.CANCELLED, PrintHostJobState.CANCELLED, PrintHostJobState.COMPLETED), ended.map { it.state })
        assertEquals(listOf("/uploads/2.gcode", "/uploads/1.gcode", "/uploads/3.gcode"), files.deleted)
        assertEquals(listOf("/uploads/1.gcode", "/uploads/3.gcode"), sender.sent)
        assertEquals(100, ended[2].progress)
    }

    @Test
    fun `an upload that fails keeps the host's message after Orca's own, and a file that could not be written its message alone`() = runBlocking {
        val sender = HeldSender(hold = false, outcome = PrintHostUploadOutcome.Failure("HTTP 500: busy", listOf(OrcaText("HTTP 500: busy"))))
        val queue = PrintHostJobQueue(sender, Files(), CoroutineScope(SupervisorJob() + coroutineContext))

        queue.enqueue(printer, startPrint = true, options("one.gcode")) { Result.success(it.newUpload(".gcode")) }
        queue.enqueue(printer, startPrint = true, options("two.gcode")) {
            Result.failure(IllegalStateException("Abnormal print file data. Please slice again"))
        }
        val ended = withTimeout(5_000) { queue.jobs.first { jobs -> jobs.size == 2 && jobs.all { it.state == PrintHostJobState.ERROR } } }

        assertEquals(listOf(OrcaText("Error uploading to print host"), OrcaText(":\n"), OrcaText("HTTP 500: busy")), ended[0].error)
        assertEquals(listOf(OrcaText("Abnormal print file data. Please slice again")), ended[1].error)
        assertEquals(-1, ended[1].size)
        queue.acknowledge(1)
        assertEquals(listOf(true, false), queue.jobs.value.map { it.reported })
    }

    private val printer = PhysicalPrinter("K2", ModelSettings(mapOf("host_type" to "octoprint", "print_host" to "192.168.1.5")))

    private fun options(path: String) = PrintOptions(uploadPath = path)

    /** Sends every file it is given; the first one waits until it is cancelled when [hold]. */
    private class HeldSender(
        private val hold: Boolean = true,
        private val outcome: PrintHostUploadOutcome = PrintHostUploadOutcome.Success(""),
    ) : GcodeSender {
        val started = CompletableDeferred<Unit>()
        val sent = mutableListOf<String>()

        override suspend fun send(
            printer: PhysicalPrinter,
            gcode: OutputPath,
            startPrint: Boolean,
            options: PrintOptions,
            onProgress: (Float) -> Unit,
        ): PrintHostUploadOutcome {
            sent += gcode.value
            onProgress(0.5f)
            if (hold && started.complete(Unit)) awaitCancellation()
            return outcome
        }

        override suspend fun slots(printer: PhysicalPrinter) = PrinterSlotsOutcome.Success(emptyList())

        override suspend fun flashforgeSlots(printer: PhysicalPrinter) = FlashforgeSlotsOutcome.Failure("")

        override suspend fun printer3dOsLists(printer: PhysicalPrinter) = Printer3dOsListsOutcome.Failure("")

        override suspend fun test(printer: PhysicalPrinter) = PrintHostTestOutcome.Success("")

        override suspend fun printers(printer: PhysicalPrinter) = HostPrintersOutcome.Success(emptyList())

        override suspend fun serialNumber(printer: PhysicalPrinter, lookUp: Boolean) = ""

        override suspend fun cloudLogin(printer: PhysicalPrinter, openPage: (String) -> Unit) = CloudLoginOutcome.Failure("")

        override suspend fun isLoggedIn(printer: PhysicalPrinter) = false

        override suspend fun logOut(printer: PhysicalPrinter) = Unit
    }

    private class Files : UploadFiles {
        private var next = 0
        val deleted = mutableListOf<String>()

        override fun newUpload(suffix: String) = OutputPath("/uploads/${++next}$suffix")

        override fun copy(source: OutputPath, target: OutputPath) = true

        override fun sizeOf(file: OutputPath) = 1_000L

        override fun delete(file: OutputPath) {
            deleted += file.value
        }
    }
}
