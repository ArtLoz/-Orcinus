package app.orcinus.shadow.data.notices

import app.orcinus.shadow.core.model.ComponentId
import app.orcinus.shadow.core.model.LicenseId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class AboutLibrariesNoticeCatalogTest {
    // The shape of the aboutlibraries.json the Gradle plugin generates.
    private val definitions = """
        {
          "libraries": [
            {
              "uniqueId": "native:orcaslicer",
              "artifactVersion": "2.4.2",
              "name": "OrcaSlicer",
              "description": "The slicing engine.",
              "website": "https://github.com/OrcaSlicer/OrcaSlicer",
              "developers": [{ "name": "SoftFever" }],
              "licenses": ["AGPL-3.0-only"]
            },
            {
              "uniqueId": "androidx.core:core",
              "artifactVersion": "1.19.0",
              "name": "Core",
              "description": "",
              "developers": [],
              "licenses": ["Apache-2.0"]
            }
          ],
          "licenses": {
            "AGPL-3.0-only": { "name": "GNU Affero General Public License v3.0", "url": "https://www.gnu.org/licenses/agpl-3.0.html", "content": "GNU AFFERO GENERAL PUBLIC LICENSE" },
            "Apache-2.0": { "name": "Apache License 2.0", "url": "https://www.apache.org/licenses/LICENSE-2.0" }
          }
        }
    """.trimIndent()

    private var reads = 0
    private val catalog = AboutLibrariesNoticeCatalog(readDefinitions = { reads++; definitions }, dispatcher = Dispatchers.Unconfined)

    @Test
    fun mapsLibrariesAndTheirLicenses() = runBlocking {
        val components = catalog.components()

        val orca = components.single { it.id == ComponentId("native:orcaslicer") }
        assertEquals("OrcaSlicer", orca.name)
        assertEquals("2.4.2", orca.version)
        assertEquals("GNU AFFERO GENERAL PUBLIC LICENSE", orca.licenses.single().text)

        val core = components.single { it.id == ComponentId("androidx.core:core") }
        assertNull(core.description)
        assertNull(core.website)
        assertNull(core.licenses.single().text)
    }

    @Test
    fun findsLicenseByIdAndParsesOnce() = runBlocking {
        assertEquals("Apache License 2.0", catalog.license(LicenseId("Apache-2.0"))?.name)
        assertNull(catalog.license(LicenseId("MIT")))
        catalog.components()
        assertEquals(1, reads)
    }
}
