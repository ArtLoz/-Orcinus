package app.orcinus.shadow.data.notices

import app.orcinus.shadow.core.model.ComponentId
import app.orcinus.shadow.core.model.License
import app.orcinus.shadow.core.model.LicenseId
import app.orcinus.shadow.core.model.ThirdPartyComponent
import app.orcinus.shadow.domain.about.NoticeCatalog
import com.mikepenz.aboutlibraries.Libs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Notices from the definitions the AboutLibraries Gradle plugin generates at
 * build time: the app's Maven dependencies plus the native engine libraries and
 * the font described in app/notices. [readDefinitions] returns that JSON; it is
 * parsed once, on first use.
 */
class AboutLibrariesNoticeCatalog(
    private val readDefinitions: () -> String,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : NoticeCatalog {
    private val mutex = Mutex()
    private var libs: Libs? = null

    override suspend fun components(): List<ThirdPartyComponent> = libs().libraries.map { library ->
        ThirdPartyComponent(
            id = ComponentId(library.uniqueId),
            name = library.name,
            version = library.artifactVersion?.takeIf { it.isNotBlank() },
            description = library.description?.takeIf { it.isNotBlank() },
            website = library.website?.takeIf { it.isNotBlank() },
            // The parser keeps licenses in a hash set; order them for a stable page.
            licenses = library.licenses.map { it.toLicense() }.sortedBy { it.name },
        )
    }

    override suspend fun license(id: LicenseId): License? =
        libs().licenses.firstOrNull { it.hash == id.value }?.toLicense()

    private suspend fun libs(): Libs = mutex.withLock {
        libs ?: withContext(dispatcher) { Libs.Builder().withJson(readDefinitions()).build() }.also { libs = it }
    }
}

private fun com.mikepenz.aboutlibraries.entity.License.toLicense() = License(
    id = LicenseId(hash),
    name = name,
    url = url?.takeIf { it.isNotBlank() },
    text = licenseContent?.takeIf { it.isNotBlank() },
)
