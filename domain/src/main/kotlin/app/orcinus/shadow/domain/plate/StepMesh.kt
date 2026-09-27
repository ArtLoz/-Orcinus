package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.AppConfigKeys
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.StepMeshChoice
import app.orcinus.shadow.core.model.StepMeshOptions
import app.orcinus.shadow.core.model.StepMeshQuestion
import app.orcinus.shadow.domain.preferences.AppPreferences
import app.orcinus.shadow.slicing.api.PlateInspector
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred

/**
 * StepMeshDialog: a load or a replacement that reads a STEP file asks how to
 * mesh it before it goes on, while the Preferences' "Show options when
 * importing STEP file" is on; the dialog counts the triangles the file makes
 * with its values. The question waits on the plate for its answer, as the
 * desktop app's modal dialog waits. OK writes the values into the app
 * configuration, as the dialog's OK does, and "Don't show again" turns the
 * preference off.
 */
class StepMeshPrompt(
    private val inspector: PlateInspector,
    private val preferences: AppPreferences,
    private val repository: PlateRepository,
) {
    private var reply: CompletableDeferred<StepMeshChoice?>? = null

    /** The options the user chose for [source], which the dialog opens with [options]; null for Cancel. */
    suspend fun ask(source: ModelPath, options: StepMeshOptions): StepMeshOptions? {
        val answer = CompletableDeferred<StepMeshChoice?>()
        reply = answer
        repository.update { it.copy(stepMesh = StepMeshQuestion(source, options)) }
        val choice = try {
            answer.await()
        } finally {
            reply = null
            repository.update { it.copy(stepMesh = null) }
        }
        if (choice == null) {
            // StepMeshDialog::stop_task(), and its file goes with it.
            inspector.releaseStepFile()
            return null
        }
        if (choice.dontShowAgain) preferences.set(AppConfigKeys.ENABLE_STEP_MESH_SETTING, "false")
        preferences.set(AppConfigKeys.IS_SPLIT_COMPOUND, if (choice.options.splitCompound) "true" else "false")
        preferences.set(AppConfigKeys.LINEAR_DEFLETION, String.format(Locale.ROOT, "%.3f", choice.options.linearDeflection))
        preferences.set(AppConfigKeys.ANGLE_DEFLETION, String.format(Locale.ROOT, "%.2f", choice.options.angleDeflection))
        return choice.options
    }

    /** The dialog's OK with [choice], or Cancel with null. */
    fun answer(choice: StepMeshChoice?) {
        reply?.complete(choice)
    }

    /**
     * StepMeshDialog::update_mesh_number_text(): the triangles the file makes
     * at these deflections; a new count stops the one before, which then
     * gives 0.
     */
    suspend fun triangleCount(linearDeflection: Double, angleDeflection: Double): Long {
        val source = repository.state.value.stepMesh?.source ?: return 0
        inspector.stopStepTriangleCount()
        return inspector.stepTriangleCount(source, linearDeflection, angleDeflection)
    }
}
