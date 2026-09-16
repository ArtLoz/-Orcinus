package app.orcinus.shadow.domain.about

import app.orcinus.shadow.core.model.ComponentId
import app.orcinus.shadow.core.model.License
import app.orcinus.shadow.core.model.LicenseId
import app.orcinus.shadow.core.model.ThirdPartyComponent

/** Port: the third-party components bundled with the app and the texts of their licenses. */
interface NoticeCatalog {
    suspend fun components(): List<ThirdPartyComponent>

    suspend fun license(id: LicenseId): License?
}

/** Every bundled component, ordered by name. */
class GetThirdPartyComponentsUseCase(private val catalog: NoticeCatalog) {
    suspend operator fun invoke(): List<ThirdPartyComponent> =
        catalog.components().sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}

class GetThirdPartyComponentUseCase(private val catalog: NoticeCatalog) {
    suspend operator fun invoke(id: ComponentId): ThirdPartyComponent? =
        catalog.components().firstOrNull { it.id == id }
}

class GetLicenseUseCase(private val catalog: NoticeCatalog) {
    suspend operator fun invoke(id: LicenseId): License? = catalog.license(id)
}
