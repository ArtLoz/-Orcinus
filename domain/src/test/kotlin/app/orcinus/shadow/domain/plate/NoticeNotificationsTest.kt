package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.AppConfigOutcome
import app.orcinus.shadow.core.model.DialogIcon
import app.orcinus.shadow.core.model.NoticeNotificationLevel
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.SettingsDialog
import app.orcinus.shadow.core.model.notificationLevel
import app.orcinus.shadow.domain.preferences.AppPreferences
import app.orcinus.shadow.slicing.api.AppConfigStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NoticeNotificationsTest {
    private val repository = object : PlateRepository {
        private val flow = MutableStateFlow(PlateState())
        override val state: StateFlow<PlateState> = flow

        override fun update(transform: (PlateState) -> PlateState) {
            flow.value = transform(flow.value)
        }
    }
    private val preferences = AppPreferences(
        object : AppConfigStore {
            override suspend fun appConfigValues(keys: List<String>, section: String) = AppConfigOutcome.Success(emptyMap())

            override suspend fun setAppConfigValue(key: String, value: String, section: String) = AppConfigOutcome.Success(mapOf(key to value))
        },
    )

    @Test
    fun `the notices OrcaSlicer shows on the 3D view have their levels, the others are message boxes`() {
        assertEquals(NoticeNotificationLevel.ERROR, notice("3mf_invalid_values").notificationLevel)
        // A load of several files follows the id with the file's place.
        assertEquals(NoticeNotificationLevel.ERROR, notice("3mf_invalid_values@1").notificationLevel)
        assertEquals(NoticeNotificationLevel.REGULAR, notice("pa_pattern_accelerations").notificationLevel)
        assertEquals(NoticeNotificationLevel.REGULAR, notice("pa_pattern_speeds").notificationLevel)
        assertEquals(NoticeNotificationLevel.REGULAR, notice("paint_removed").notificationLevel)
        assertNull(notice("load_failed").notificationLevel)
        assertNull(notice("paint_removed").copy(question = true).notificationLevel)
    }

    @Test
    fun `OK dismisses the first message box, and a notification closes on its own`() {
        val invalid = notice("3mf_invalid_values")
        val failed = notice("load_failed")
        val removed = notice("paint_removed")
        repository.update { it.copy(plateNotices = listOf(invalid, failed, removed)) }

        DismissPlateNoticeUseCase(repository, preferences, CoroutineScope(Dispatchers.Unconfined))()
        assertEquals(listOf(invalid, removed), repository.state.value.plateNotices)

        DismissPlateProblemUseCase(repository).notice(removed)
        assertEquals(listOf(invalid), repository.state.value.plateNotices)

        // No message box is left for OK to dismiss.
        DismissPlateNoticeUseCase(repository, preferences, CoroutineScope(Dispatchers.Unconfined))()
        assertEquals(listOf(invalid), repository.state.value.plateNotices)
    }

    private fun notice(id: String) =
        SettingsDialog(id = id, icon = DialogIcon.INFO, title = emptyList(), text = listOf(OrcaText(id)), question = false, yes = null, no = null)
}
