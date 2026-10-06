package app.orcinus.shadow.core.model

/**
 * PrintHostQueueDialog::JobState: where an upload of the queue is. Cancel
 * applies to the states before [ERROR] (`state < ST_ERROR`).
 */
enum class PrintHostJobState {
    QUEUED,
    UPLOADING,
    ERROR,
    CANCELLED,
    COMPLETED,
    ;

    /** PrintHostQueueDialog::on_list_select(): "Cancel selected" is enabled. */
    val cancellable: Boolean get() = this < ERROR
}

/**
 * A row of PrintHostQueueDialog: an upload the user sent, which waits for the
 * ones before it, goes out, and ends completed, with an error or cancelled.
 */
data class PrintHostJob(
    /** The ID column: 1 for the first upload since the app started. */
    val id: Int,
    /** printhost->get_host(): the Host column. */
    val host: String,
    /** upload_path: the Filename column. */
    val uploadPath: String,
    /** The Size column in bytes; -1 for "unknown". */
    val size: Long,
    val state: PrintHostJobState,
    /** The Progress column, 0 to 100. */
    val progress: Int = 0,
    /**
     * COL_ERRORMSG: what on_error() shows, "Error uploading to print host:"
     * and the host's message; empty unless the job ended with an error.
     */
    val error: List<OrcaText> = emptyList(),
    /** A page the host opens after the upload (SimplyPrint's import, 3DPrinterOS's quick print). */
    val openUrl: String? = null,
    /** PrintHostJob::switch_to_device_tab: the Device tab shows once the upload went through. */
    val switchToDeviceTab: Boolean = false,
    /** Whether the screen has shown how the job ended. */
    val reported: Boolean = false,
)
