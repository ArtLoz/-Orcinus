package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.AppConfigKeys
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.domain.preferences.AppPreferences
import app.orcinus.shadow.slicing.api.AppConfigStore
import app.orcinus.shadow.storage.api.DocumentAccess
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URLDecoder
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

/** What joins the recent files (MainFrame::add_to_recent_projects()), in their order, the last on top. */
fun interface RecentProjects {
    fun add(documents: List<ExternalDocumentReference>)
}

/**
 * A recent file as the home page lists it (MainFrame::get_recent_projects()):
 * its name, when it last changed, or that it is missing.
 */
data class RecentProject(
    val document: ExternalDocumentReference,
    val name: String,
    val modified: Long?,
    val missing: Boolean,
)

/**
 * MainFrame's recent projects (m_recent_projects): the documents of the
 * projects opened and saved and, with "Add STL/STEP files to recent files
 * list", of the models added, the most recent first and at most "Maximum
 * recent files" of them, which OrcaSlicer.conf keeps
 * (AppConfig::set_recent_projects()). Android grants the app its access to a
 * document per document; the list keeps it across starts while the document
 * is on it.
 */
class RecentProjectsUseCase(
    private val store: AppConfigStore,
    private val preferences: AppPreferences,
    private val documents: DocumentAccess,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) : RecentProjects {
    private val lock = Mutex()
    private var loaded = false
    private val state = MutableStateFlow<List<ExternalDocumentReference>>(emptyList())

    /** The documents, the most recent first. */
    val recent: StateFlow<List<ExternalDocumentReference>> = state.asStateFlow()

    init {
        // MainFrame::set_max_recent_count() as "Maximum recent files" changes.
        applicationScope.launch {
            preferences.values.map { AppConfigKeys.maxRecentCount(it[AppConfigKeys.MAX_RECENT_COUNT]) }.distinctUntilChanged().collect { max ->
                lock.withLock { if (ensureLoaded()) keep(state.value.take(max), state.value.drop(max)) }
            }
        }
    }

    /** The list as OrcaSlicer.conf keeps it, read once the engine can answer. */
    suspend fun load() {
        lock.withLock { ensureLoaded() }
    }

    /**
     * MainFrame::add_to_recent_projects(): a document that is there goes to
     * the top (FileHistory::AddFileToHistory()), and the oldest beyond the
     * maximum leave; with a maximum of 0 nothing is added.
     */
    override fun add(documents: List<ExternalDocumentReference>) {
        if (documents.isEmpty()) return
        applicationScope.launch(Dispatchers.IO) {
            lock.withLock {
                if (!ensureLoaded()) return@withLock
                val max = maxCount()
                if (max == 0) return@withLock
                var list = state.value
                for (document in documents) {
                    if (this@RecentProjectsUseCase.documents.describe(document) == null) continue
                    this@RecentProjectsUseCase.documents.keep(document, HOLDER)
                    list = listOf(document) + list.filter { it != document }
                }
                keep(list.take(max), list.drop(max))
            }
        }
    }

    /** MainFrame::remove_recent_project(): the document, or with none every one ("Clear all"). */
    fun remove(document: ExternalDocumentReference?) {
        applicationScope.launch(Dispatchers.IO) {
            lock.withLock {
                if (!ensureLoaded()) return@withLock
                val (gone, kept) = state.value.partition { document == null || it == document }
                if (gone.isNotEmpty()) keep(kept, gone)
            }
        }
    }

    /** What the home page shows of [document]: its name, when it last changed, or that it is missing. */
    suspend fun describe(document: ExternalDocumentReference): RecentProject = withContext(Dispatchers.IO) {
        val description = documents.describe(document)
        RecentProject(document, description?.name ?: nameOf(document), description?.modified, missing = description == null)
    }

    /** bbs_3mf_get_thumbnail(): the picture the 3MF document carries; null for none. */
    suspend fun thumbnail(document: ExternalDocumentReference): ByteArray? = withContext(Dispatchers.IO) {
        ThreeMfThumbnail.read { documents.open(document) }
    }

    /** wxFileExists() of a recent file before it opens (MainFrame::open_recent_project()). */
    suspend fun exists(document: ExternalDocumentReference): Boolean = withContext(Dispatchers.IO) { documents.describe(document) != null }

    private suspend fun ensureLoaded(): Boolean {
        if (loaded) return true
        val kept = store.recentProjects() ?: return false
        loaded = true
        // MainFrame(): every kept file added to the history, the most recent last,
        // within "Maximum recent files".
        val list = kept.distinct().map(::ExternalDocumentReference)
        val max = maxCount()
        state.value = list
        if (list.size > max) keep(list.take(max), list.drop(max))
        return true
    }

    /** The list becomes [list], written into OrcaSlicer.conf; the documents [gone] lose the access the list kept. */
    private suspend fun keep(list: List<ExternalDocumentReference>, gone: List<ExternalDocumentReference>) {
        if (list == state.value && gone.isEmpty()) return
        state.value = list
        store.setRecentProjects(list.map { it.value })
        // The project open now keeps its document, which Save writes into.
        val current = repository.state.value.project.document
        gone.filter { it != current && it !in list }.forEach { documents.release(it, HOLDER) }
    }

    private fun maxCount(): Int = AppConfigKeys.maxRecentCount(preferences[AppConfigKeys.MAX_RECENT_COUNT])

    private companion object {
        /** What holds the access to the documents of the list (DocumentAccess). */
        const val HOLDER = "recent_projects"

        /** The file's name of a document that does not tell it: the last part of its path. */
        fun nameOf(document: ExternalDocumentReference): String {
            val last = document.value.substringAfterLast('/')
            val decoded = runCatching { URLDecoder.decode(last, "UTF-8") }.getOrDefault(last)
            return decoded.substringAfterLast('/').substringAfterLast(':')
        }
    }
}

/**
 * _BBS_3MF_Importer::get_thumbnail(): the picture the relationships name — the
 * Bambu cover's middle size, or the package thumbnail — or else
 * Metadata/plate_1.png; none without relationships.
 */
internal object ThreeMfThumbnail {
    private const val RELATIONSHIPS_FILE = "_rels/.rels"
    private const val THUMBNAIL_FILE = "Metadata/plate_1.png"

    fun read(open: () -> InputStream?): ByteArray? {
        val relationships = entry(open, RELATIONSHIPS_FILE) ?: return null
        var thumbnail = ""
        var middle = ""
        val handler = object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
                if ((localName?.ifEmpty { null } ?: qName) != "Relationship") return
                val path = attributes.getValue("Target").orEmpty()
                val type = attributes.getValue("Type").orEmpty()
                when {
                    type.startsWith("http://schemas.openxmlformats.org/") && type.endsWith("thumbnail") -> if (path.endsWith(".png")) thumbnail = path
                    type.startsWith("http://schemas.bambulab.com/") && type.endsWith("cover-thumbnail-middle") -> middle = path
                }
            }
        }
        runCatching { SAXParserFactory.newInstance().newSAXParser().parse(relationships.inputStream(), handler) }.getOrElse { return null }
        if (middle.isEmpty()) middle = thumbnail
        return entry(open, middle.ifEmpty { THUMBNAIL_FILE }.removePrefix("/"))
    }

    /** The bytes of the archive's entry [name]; null where it has none. */
    private fun entry(open: () -> InputStream?, name: String): ByteArray? = runCatching {
        open()?.use { stream ->
            ZipInputStream(stream.buffered()).use { zip ->
                generateSequence { zip.nextEntry }.firstOrNull { it.name == name } ?: return@use null
                ByteArrayOutputStream().also { zip.copyTo(it) }.toByteArray()
            }
        }
    }.getOrNull()
}
