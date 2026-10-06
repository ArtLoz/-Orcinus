package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.PresetsOutcome
import app.orcinus.shadow.core.model.ProfileCheckAnswer
import app.orcinus.shadow.core.model.ProfileDownload
import app.orcinus.shadow.core.model.ProfileUpdate
import app.orcinus.shadow.core.model.ProfileUpdateRequest
import app.orcinus.shadow.core.model.ProfileUpdatesNotice
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.slicing.api.ProfileUpdater
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ProfileUpdatesTest {
    private val repository = object : PlateRepository {
        private val flow = MutableStateFlow(PlateState())
        override val state: StateFlow<PlateState> = flow

        override fun update(transform: (PlateState) -> PlateState) {
            flow.value = transform(flow.value)
        }
    }
    private val updater = FakeUpdater()
    private val checked = mutableListOf<String>()
    private val source = object : ProfileUpdateSource {
        override suspend fun check(url: String): ProfileCheckAnswer {
            checked += url
            return ProfileCheckAnswer(200, """{"vendor_version":"02.03.02.76","download_url":"https://example.invalid/Creality.zip"}""")
        }

        override suspend fun download(url: String, target: String): Boolean = true
    }
    private val applied = mutableListOf<SlicingProfileSelection?>()
    private var saveAnswer = true

    // Unconfined: the launched work runs at once, as the fakes never wait.
    private val useCase = ProfileUpdatesUseCase(
        updater,
        source,
        repository,
        { before, _ -> applied += before },
        savePresetChanges = { saveAnswer },
        applicationScope = CoroutineScope(Dispatchers.Unconfined),
    )

    @Test
    fun `an offered update is notified, and OK installs it and loads the presets`() {
        useCase.syncAtStartup()
        useCase.syncAtStartup()
        assertEquals(1, checked.size)
        val notice = repository.state.value.profileUpdates
        assertEquals(listOf(UPDATE), notice?.updates)
        assertTrue(notice!!.notified)
        assertEquals(0, updater.performed)

        // "Detail." opens the dialog, whose OK installs.
        DismissPlateProblemUseCase(repository).profileUpdates(detail = true)
        assertTrue(repository.state.value.profileUpdates!!.confirming)
        useCase.answer(install = true)
        assertNull(repository.state.value.profileUpdates)
        assertEquals(1, updater.performed)
        assertEquals(1, updater.reloaded)
        assertEquals(listOf<SlicingProfileSelection?>(null), applied)
    }

    @Test
    fun `a forced update installs at once and tells what it installed`() {
        updater.updates = listOf(UPDATE.copy(forced = true))
        useCase.printerChanged()
        assertNull(repository.state.value.profileUpdates)
        assertEquals(1, updater.performed)
        assertEquals(1, updater.reloaded)
        assertEquals(listOf(UPDATE.copy(forced = true)), repository.state.value.profileUpdatesInstalled)
    }

    @Test
    fun `cancelling the question about unsaved changes loads nothing anew`() {
        saveAnswer = false
        useCase.syncAtStartup()
        useCase.answer(install = true)
        assertEquals(1, updater.performed)
        assertEquals(0, updater.reloaded)
        assertTrue(applied.isEmpty())
    }

    @Test
    fun `Cancel and a request without an address leave everything as it was`() {
        updater.request = ProfileUpdateRequest(enabled = false, vendor = "Creality", url = "")
        useCase.syncAtStartup()
        assertTrue(checked.isEmpty())
        assertNull(repository.state.value.profileUpdates)

        repository.update { it.copy(profileUpdates = ProfileUpdatesNotice(listOf(UPDATE))) }
        useCase.answer(install = false)
        assertNull(repository.state.value.profileUpdates)
        assertEquals(0, updater.performed)
    }

    private class FakeUpdater : ProfileUpdater {
        var request = ProfileUpdateRequest(enabled = true, vendor = "Creality", url = "https://check-version.orcaslicer.com/profile?vendor=Creality")
        var updates = listOf(UPDATE)
        var performed = 0
        var reloaded = 0

        override suspend fun profileUpdateRequest(startup: Boolean): ProfileUpdateRequest = request

        override suspend fun profileUpdateAnswer(vendor: String, answer: ProfileCheckAnswer): ProfileDownload? =
            ProfileDownload("https://example.invalid/Creality.zip", "/data/ota/Creality.data").takeIf { answer.status == 200 }

        override suspend fun cacheProfileUpdate(vendor: String): Boolean = true

        override suspend fun profileUpdates(): List<ProfileUpdate> = updates

        override suspend fun performProfileUpdates(): Boolean {
            performed++
            return true
        }

        override suspend fun reloadSystemPresets(): PresetsOutcome {
            reloaded++
            return PresetsOutcome.Failure("not loaded in the test")
        }
    }

    private companion object {
        val UPDATE = ProfileUpdate("Creality", "2.3.2.76", "Changes", forced = false)
    }
}
