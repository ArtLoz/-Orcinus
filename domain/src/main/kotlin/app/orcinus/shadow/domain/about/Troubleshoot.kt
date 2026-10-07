package app.orcinus.shadow.domain.about

import app.orcinus.shadow.core.model.AppInfo
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.ProfilesOverview
import app.orcinus.shadow.domain.plate.PlateRepository
import app.orcinus.shadow.slicing.api.Troubleshooting
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Port: the phone, where TroubleshootDialog tells of the computer
 * (GetOSinfo(), GetPackageType(), GetCPUinfo(), GetRAMinfo(), GetGPUinfo()
 * and GetMONinfo()).
 */
fun interface DeviceInformation {
    suspend fun describe(): DeviceDetails
}

/** What [DeviceInformation] finds. */
data class DeviceDetails(
    /** The Android release and its API level. */
    val androidRelease: String,
    val apiLevel: Int,
    val manufacturer: String,
    val model: String,
    /** The system on a chip, and the ABI the app runs as. */
    val processor: String,
    val abi: String,
    val memoryBytes: Long,
    /** The OpenGL ES renderer and its shading language; null when no context could be made. */
    val renderer: String?,
    val shadingLanguage: String?,
    val displays: List<DisplayDetails>,
    /** The font size the user chose, as a factor of the normal one. */
    val fontScale: Float,
    /** GetPackageType(): "Local Build" for a debuggable build, otherwise the app that installed this one. */
    val packageType: String,
)

/** A screen in pixels, and the factor its density scales the app by. */
data class DisplayDetails(
    val width: Int,
    val height: Int,
    val scale: Float,
)

/**
 * Port: the files of the Troubleshoot Center. On a phone OrcaSlicer's log
 * goes to logcat, so the session's log is what logcat keeps of the app.
 */
interface TroubleshootFiles {
    /** ExportAsJson(): [text] into [document]; false when it could not be written. */
    suspend fun writeText(text: String, document: ExternalDocumentReference): Boolean

    /**
     * PackAll()'s SaveAsZip(): the session's log and, when there is one, the
     * project's document [project] under its name, zipped into [document];
     * false when either could not be read or the archive written.
     */
    suspend fun pack(document: ExternalDocumentReference, project: ExternalDocumentReference?): Boolean
}

/**
 * TroubleshootDialog's system information: the lines beside its logo
 * (sys_info_lines()), and GetSysInfoAll(), which its Copy puts on the
 * clipboard. [build] is the commit of the OrcaSlicer release [engine] the
 * engine is built from, which the button under the version opens.
 */
data class SystemInformation(
    val version: String,
    val engine: String,
    val build: String?,
    val buildUrl: String?,
    /** sys_info_lines(true) */
    val lines: List<String>,
    /** sys_info_lines(false): GetOStype() alone. */
    val osType: String,
    /** GetSysInfoAll() */
    val text: String,
)

/** What PackAll() asks before its file dialog. */
enum class PackQuestion {
    NONE,

    /** "The current project has unsaved changes, save it before continue?" */
    SAVE_CHANGES,

    /** "No project file on current session. Only logs will be included to package" */
    NO_PROJECT,
}

/**
 * OrcaSlicer's Troubleshoot Center (GUI_App::troubleshoot()) on a phone: the
 * system information, the overview of the loaded profiles and the cleaning of
 * their cache, and the pack of the project and the session's log that a bug
 * report asks for.
 */
class TroubleshootUseCase(
    private val appInfo: AppInfo,
    private val device: DeviceInformation,
    private val engine: Troubleshooting,
    private val files: TroubleshootFiles,
    private val repository: PlateRepository,
    /** Plater::save_project() into the project's document; false when it could not be saved. */
    private val saveProject: suspend (ExternalDocumentReference) -> Boolean,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) {
    suspend fun systemInformation(): SystemInformation = systemInformationOf(appInfo, device.describe())

    suspend fun profilesOverview(): ProfilesOverview? = engine.profilesOverview()

    /** RebuildSystemProfiles() after its question; false when the cleaning could not be arranged. */
    suspend fun cleanSystemProfiles(): Boolean = engine.cleanSystemProfiles()

    /** ExportAsJson(GetProfilesOverview(), "ProfilesOverview"): the name its file dialog offers. */
    val profilesOverviewName: String = "ProfilesOverview.json"

    /** "Export..." of "Loaded profiles overview": the overview as the engine has it now, into [document]. */
    suspend fun exportProfilesOverview(document: ExternalDocumentReference): Boolean {
        val overview = engine.profilesOverview() ?: return false
        return files.writeText(overview.json, document)
    }

    /** PackAll(): a project with a file is saved first when it has unsaved changes; without one only the log goes. */
    fun packQuestion(): PackQuestion {
        val state = repository.state.value
        return when {
            state.project.document == null -> PackQuestion.NO_PROJECT
            !state.projectUpToDate -> PackQuestion.SAVE_CHANGES
            else -> PackQuestion.NONE
        }
    }

    /** PackAll()'s Yes: save_project(), which the pack goes on after whatever came of it. */
    suspend fun saveProject() {
        val document = repository.state.value.project.document ?: return
        withContext(NonCancellable) { saveProject(document) }
    }

    /** "OrcaSlicer_PackedDebugInfo_" + GetTimestamp(), with the app's name. */
    fun packName(): String = "${appInfo.name}_PackedDebugInfo_${now().format(TIMESTAMP)}.zip"

    /** ExportAsZip() of PackAll(): the session's log and the project's file into [document]. */
    suspend fun pack(document: ExternalDocumentReference): Boolean = files.pack(document, repository.state.value.project.document)

    /**
     * "Report issue": OrcaSlicer opens a bug report of its own tracker with
     * the version and the system filled in. The app's issues belong to its
     * own repository, whose new issue gets the system information as its
     * text; null for a build without a public source.
     */
    fun reportIssueUrl(information: SystemInformation): String? {
        val source = appInfo.sourceUrl?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return null
        return "$source/issues/new?body=" + urlEncode("```\n${information.text}\n```")
    }

    private companion object {
        // GetTimestamp()
        val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm", Locale.ROOT)
    }
}

/** The repository whose commits the button under the version opens, as the desktop dialog opens the build's commit. */
private const val ORCA_COMMIT_URL = "https://github.com/OrcaSlicer/OrcaSlicer/commit/"

/** GetOStype() */
private const val OS_TYPE = "Android"

/** TroubleshootDialog's lines and GetSysInfoAll() for the app's [info] on the phone [device]. */
internal fun systemInformationOf(info: AppInfo, device: DeviceDetails): SystemInformation {
    val version = "${info.name} ${info.version}"
    val build = info.orcaCommit?.take(SHORT_HASH)
    val engine = listOfNotNull("OrcaSlicer ${info.orcaRelease}", build?.let { "($it)" }).joinToString(" ")
    val platform = "$OS_TYPE ${device.androidRelease} (API ${device.apiLevel})"
    val phone = if (device.model.startsWith(device.manufacturer, ignoreCase = true)) device.model else "${device.manufacturer} ${device.model}"
    val processor = when {
        device.processor.isBlank() -> device.abi
        device.abi.isBlank() -> device.processor
        else -> "${device.processor} (${device.abi})"
    }
    val memory = ramInfo(device.memoryBytes) + " RAM"
    val renderer = device.renderer?.let { "$it  GLSL:${device.shadingLanguage.orEmpty()}" } ?: UNKNOWN
    val monitors = monitorsInfo(device.displays, device.fontScale)
    return SystemInformation(
        version = version,
        engine = info.orcaRelease,
        build = build,
        buildUrl = info.orcaCommit?.let { ORCA_COMMIT_URL + it },
        lines = listOf(platform, phone, device.packageType, processor, memory, renderer, monitors),
        osType = OS_TYPE,
        text = listOf(
            "Version" to version,
            "Engine" to engine,
            "Package" to device.packageType,
            "Platform" to platform,
            "Device" to phone,
            "Processor" to processor,
            "Memory" to memory,
            "Renderer" to renderer,
            "Monitors" to monitors,
        ).joinToString("\n") { (label, value) -> label.padEnd(LABEL_WIDTH) + ":  " + value },
    )
}

/** GetRAMinfo(): the memory in GB with one decimal. */
internal fun ramInfo(bytes: Long): String {
    val tenths = (bytes / 107374182.40).roundToLong()
    return "${tenths / 10}.${tenths % 10} GB"
}

/** GetMONinfo(): each screen as "<width>x<height>-<scale>%", then the text scaling as Windows has it. */
internal fun monitorsInfo(displays: List<DisplayDetails>, fontScale: Float): String {
    if (displays.isEmpty()) return UNKNOWN
    val screens = displays.joinToString("  ") { String.format(Locale.ROOT, "%dx%d-%.0f%%", it.width, it.height, it.scale * 100.0) }
    return screens + String.format(Locale.ROOT, "  TextScaling-%.0f%%", fontScale * 100.0)
}

/** The encodeStr() of "Report issue": the unreserved characters as they are, every other byte of UTF-8 as %XX. */
internal fun urlEncode(text: String): String = buildString {
    for (byte in text.toByteArray(Charsets.UTF_8)) {
        val c = byte.toInt() and 0xFF
        val char = c.toChar()
        if (c < 0x80 && (char.isLetterOrDigit() || char in "-_.~")) append(char) else append(String.format(Locale.ROOT, "%%%02X", c))
    }
}

private const val SHORT_HASH = 7
private const val LABEL_WIDTH = 10
private const val UNKNOWN = "Unknown"
