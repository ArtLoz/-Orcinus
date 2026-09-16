package app.orcinus.shadow.domain.about

import app.orcinus.shadow.core.model.ComponentId
import app.orcinus.shadow.core.model.License
import app.orcinus.shadow.core.model.LicenseId
import app.orcinus.shadow.core.model.ThirdPartyComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

class NoticeUseCasesTest {
    private val agpl = License(LicenseId("AGPL-3.0-only"), "GNU Affero General Public License v3.0", null, "text")
    private val catalog = object : NoticeCatalog {
        val components = listOf(component("native:qhull", "Qhull"), component("native:boost", "boost"), component("native:admesh", "ADMesh"))

        override suspend fun components() = components

        override suspend fun license(id: LicenseId) = agpl.takeIf { it.id == id }
    }

    @Test
    fun componentsAreOrderedByNameIgnoringCase() = runBlocking {
        assertEquals(listOf("ADMesh", "boost", "Qhull"), GetThirdPartyComponentsUseCase(catalog)().map { it.name })
    }

    @Test
    fun componentIsFoundById() = runBlocking {
        assertEquals("Qhull", GetThirdPartyComponentUseCase(catalog)(ComponentId("native:qhull"))?.name)
        assertNull(GetThirdPartyComponentUseCase(catalog)(ComponentId("native:missing")))
    }

    @Test
    fun licenseIsFoundById() = runBlocking {
        assertEquals(agpl, GetLicenseUseCase(catalog)(LicenseId("AGPL-3.0-only")))
        assertNull(GetLicenseUseCase(catalog)(LicenseId("MIT")))
    }

    private fun component(id: String, name: String) =
        ThirdPartyComponent(ComponentId(id), name, version = null, description = null, website = null, licenses = listOf(agpl))
}
