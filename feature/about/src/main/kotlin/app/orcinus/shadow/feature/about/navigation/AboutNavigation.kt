package app.orcinus.shadow.feature.about.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.orcinus.shadow.core.model.AppInfo
import app.orcinus.shadow.core.model.ComponentId
import app.orcinus.shadow.core.model.LicenseId
import app.orcinus.shadow.core.ui.navigation.overlayPageMetadata
import app.orcinus.shadow.feature.about.AboutScreen
import app.orcinus.shadow.feature.about.NoticeRoute
import app.orcinus.shadow.feature.about.NoticeViewModel
import app.orcinus.shadow.feature.about.ThirdPartyRoute
import app.orcinus.shadow.feature.about.ThirdPartyViewModel
import app.orcinus.shadow.feature.about.TroubleshootRoute
import app.orcinus.shadow.feature.about.TroubleshootViewModel
import kotlinx.serialization.Serializable

/** The About page: version, license, source code, and credits. */
@Serializable
data object AboutNavKey : NavKey

/** OrcaSlicer's Troubleshoot Center, which its Help menu opens beside About. */
@Serializable
data object TroubleshootNavKey : NavKey

@Serializable
internal data object ThirdPartyNavKey : NavKey

@Serializable
internal data class ComponentNoticeNavKey(val componentId: String) : NavKey

@Serializable
internal data class LicenseNavKey(val licenseId: String) : NavKey

/** View models of the About pages, built from the app's dependencies. */
interface AboutViewModelFactory {
    fun thirdPartyViewModel(): ThirdPartyViewModel

    fun componentNoticeViewModel(id: ComponentId): NoticeViewModel

    fun licenseNoticeViewModel(id: LicenseId): NoticeViewModel

    fun troubleshootViewModel(): TroubleshootViewModel
}

/**
 * Registers the About page, the license pages it opens, and the Troubleshoot
 * Center. [logo] draws the app icon; [onNavigate] pushes a page and [onBack]
 * closes the current one.
 */
fun EntryProviderScope<NavKey>.aboutEntries(
    appInfo: AppInfo,
    viewModels: AboutViewModelFactory,
    logo: @Composable () -> Unit,
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit,
) {
    entry<AboutNavKey> {
        AboutScreen(
            appInfo = appInfo,
            logo = logo,
            onOpenLicense = { onNavigate(LicenseNavKey(it.value)) },
            onOpenThirdParty = { onNavigate(ThirdPartyNavKey) },
            onBack = onBack,
        )
    }
    entry<ThirdPartyNavKey> {
        ThirdPartyRoute(
            viewModel = viewModel { viewModels.thirdPartyViewModel() },
            onOpenComponent = { onNavigate(ComponentNoticeNavKey(it.value)) },
            onBack = onBack,
        )
    }
    entry<ComponentNoticeNavKey> { key ->
        NoticeRoute(viewModel = viewModel { viewModels.componentNoticeViewModel(ComponentId(key.componentId)) }, onBack = onBack)
    }
    entry<LicenseNavKey> { key ->
        NoticeRoute(viewModel = viewModel { viewModels.licenseNoticeViewModel(LicenseId(key.licenseId)) }, onBack = onBack)
    }
    // Over the workspace, as the Preferences: the dialog's No goes back to the project as it was.
    entry<TroubleshootNavKey>(metadata = overlayPageMetadata) {
        TroubleshootRoute(viewModel = viewModel { viewModels.troubleshootViewModel() }, logo = logo, onBack = onBack)
    }
}
