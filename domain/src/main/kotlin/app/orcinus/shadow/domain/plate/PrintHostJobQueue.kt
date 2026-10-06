package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.OutputPath
import app.orcinus.shadow.core.model.PhysicalPrinter
import app.orcinus.shadow.core.model.PrintHostJob
import app.orcinus.shadow.core.model.PrintHostJobState
import app.orcinus.shadow.core.model.PrintHostUploadOutcome
import app.orcinus.shadow.core.model.PrintOptions
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Where an upload's file waits for its turn: BackgroundSlicingProcess::
 * prepare_upload() copies the G-code to a temporary file of its own, which
 * the queue removes once the job is done (PrintHostJobQueue::remove_source()).
 */
interface UploadFiles {
    /** A new file for an upload, ending in [suffix] (".gcode", ".gcode.3mf"). */
    fun newUpload(suffix: String): OutputPath

    /** copy_file(): false when the copy could not be made. */
    fun copy(source: OutputPath, target: OutputPath): Boolean

    /** The file's size in bytes; -1 when it cannot be read. */
    fun sizeOf(file: OutputPath): Long

    fun delete(file: OutputPath)
}

/**
 * PrintHostJobQueue: the uploads the user sent go out one after another, on
 * [scope], which outlives the screen, while [keep] holds the network for
 * them; [jobs] is what PrintHostQueueDialog lists, and [cancel] its "Cancel
 * selected" and the notification's cancel button.
 */
class PrintHostJobQueue(
    private val sender: GcodeSender,
    private val files: UploadFiles,
    private val scope: CoroutineScope,
    /** What keeps the network while jobs run (the app's foreground service). */
    private val keep: suspend (suspend () -> Unit) -> Unit = { it() },
) {
    private val state = MutableStateFlow<List<PrintHostJob>>(emptyList())

    /** The jobs since the app started, by their ID. */
    val jobs: StateFlow<List<PrintHostJob>> = state.asStateFlow()

    /** The job's printhost and upload_data: what goes where, and the file that goes. */
    private class Upload(val printer: PhysicalPrinter, val source: OutputPath, val startPrint: Boolean, val options: PrintOptions)

    private val lock = Any()
    private var lastId = 0
    private val waiting = LinkedHashMap<Int, Upload>()
    private var draining = false
    private var current: Int? = null
    private var currentJob: Job? = null
    private var currentCancelled = false

    /**
     * BackgroundSlicingProcess::prepare_upload() and enqueue(): [prepare]
     * writes the file that goes at once, so a later slice does not change
     * it; the job then waits as "Queued" for the ones before it. A file that
     * could not be written ends the job with its error.
     */
    fun enqueue(printer: PhysicalPrinter, startPrint: Boolean, options: PrintOptions, prepare: suspend (UploadFiles) -> Result<OutputPath>) {
        scope.launch {
            val prepared = try {
                prepare(files)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }
            val source = prepared.getOrNull()
            val job = PrintHostJob(
                id = 0,
                host = printer.host,
                uploadPath = options.uploadPath,
                size = source?.let(files::sizeOf) ?: -1,
                state = if (source == null) PrintHostJobState.ERROR else PrintHostJobState.QUEUED,
                // Plater shows a file it could not write as it is, not as an upload's error.
                error = if (source == null) listOf(OrcaText(prepared.exceptionOrNull()?.message.orEmpty())) else emptyList(),
                switchToDeviceTab = options.switchToDeviceTab,
            )
            synchronized(lock) {
                val id = ++lastId
                state.update { it + job.copy(id = id) }
                if (source != null) waiting[id] = Upload(printer, source, startPrint, options)
            }
            if (source != null) drainLater()
        }
    }

    /**
     * PrintHostJobQueue::cancel(): a job that waits is dropped with its file,
     * the one going out is aborted; either ends "Canceled". A job that has
     * ended stays as it ended.
     */
    fun cancel(id: Int) {
        val running = synchronized(lock) {
            val upload = waiting.remove(id)
            if (upload != null) {
                files.delete(upload.source)
                update(id) { it.copy(state = PrintHostJobState.CANCELLED, progress = 0) }
                return
            }
            if (current != id) return
            currentCancelled = true
            currentJob
        }
        running?.cancel()
    }

    /** The screen has shown how the job ended. */
    fun acknowledge(id: Int) = update(id) { it.copy(reported = true) }

    /** bg_thread_main(): one job after another, while any waits. */
    private fun drainLater() {
        synchronized(lock) {
            if (draining) return
            draining = true
        }
        scope.launch {
            keep {
                while (true) {
                    val (id, upload) = next() ?: break
                    perform(id, upload)
                }
            }
        }
    }

    private fun next(): Pair<Int, Upload>? = synchronized(lock) {
        val first = waiting.entries.firstOrNull()
        if (first == null) {
            draining = false
            return null
        }
        waiting.remove(first.key)
        current = first.key
        currentJob = null
        currentCancelled = false
        first.key to first.value
    }

    /**
     * perform_job(): the job is "Uploading" from 0 % (emit_progress(0)), its
     * progress follows the file, and it ends completed, with the host's error
     * or cancelled; its file is removed either way.
     */
    private suspend fun perform(id: Int, upload: Upload) {
        update(id) { it.copy(state = PrintHostJobState.UPLOADING, progress = 0) }
        var outcome: PrintHostUploadOutcome? = null
        val running = scope.launch(start = CoroutineStart.LAZY) {
            outcome = try {
                sender.send(upload.printer, upload.source, upload.startPrint, upload.options) { part -> progress(id, part) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // bg_thread_main(): what a host throws is the job's error.
                PrintHostUploadOutcome.Failure(error.message.orEmpty())
            }
        }
        val cancelled = synchronized(lock) {
            currentJob = running
            currentCancelled
        }
        if (cancelled) running.cancel() else running.start()
        running.join()
        synchronized(lock) {
            current = null
            currentJob = null
        }
        files.delete(upload.source)
        when (val ended = outcome) {
            is PrintHostUploadOutcome.Success -> update(id) {
                it.copy(state = PrintHostJobState.COMPLETED, progress = 100, openUrl = ended.openUrl)
            }
            // on_error(): "Error uploading to print host:" and the host's message.
            is PrintHostUploadOutcome.Failure -> update(id) {
                it.copy(
                    state = PrintHostJobState.ERROR,
                    progress = 0,
                    error = listOf(OrcaText("Error uploading to print host"), OrcaText(":\n")) +
                        ended.text.ifEmpty { listOf(OrcaText(ended.message)) },
                )
            }
            null -> update(id) { it.copy(state = PrintHostJobState.CANCELLED, progress = 0) }
        }
    }

    /** progress_fn(): the percentage, sent on when it changes. */
    private fun progress(id: Int, part: Float) {
        val percent = (part * 100).toInt().coerceIn(0, 100)
        update(id) { job -> if (job.state == PrintHostJobState.UPLOADING && job.progress != percent) job.copy(progress = percent) else job }
    }

    private fun update(id: Int, change: (PrintHostJob) -> PrintHostJob) =
        state.update { jobs -> jobs.map { if (it.id == id) change(it) else it } }
}
