package app.orcinus.shadow.domain

import app.orcinus.shadow.core.model.EngineStatus
import app.orcinus.shadow.core.model.EngineVersion
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.ModelSource
import app.orcinus.shadow.core.model.OutputPath
import app.orcinus.shadow.core.model.ProfileId
import app.orcinus.shadow.core.model.SliceFailureCode
import app.orcinus.shadow.core.model.SliceJobId
import app.orcinus.shadow.core.model.SliceOutcome
import app.orcinus.shadow.core.model.SliceProgress
import app.orcinus.shadow.core.model.SliceRequest
import app.orcinus.shadow.core.model.SliceStatistics
import app.orcinus.shadow.slicing.api.SliceProgressListener
import app.orcinus.shadow.slicing.api.SlicerEngine
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SliceModelUseCaseTest {
    @Test
    fun `empty model path is rejected before calling engine`() {
        val engine = FakeEngine()
        val outcome = runSuspend {
            SliceModelUseCase(engine)(request(modelPath = ""), SliceProgressObserver {})
        }

        val failure = assertIs<SliceOutcome.Failure>(outcome)
        assertEquals(SliceFailureCode.INVALID_REQUEST, failure.code)
        assertEquals(JOB_ID, failure.jobId)
        assertFalse(engine.sliceCalled)
    }

    @Test
    fun `input and output paths must differ`() {
        val engine = FakeEngine()
        val outcome = runSuspend {
            SliceModelUseCase(engine)(
                request(
                    modelPath = "/models/cube.stl",
                    outputPath = "/models/cube.stl",
                ),
                SliceProgressObserver {},
            )
        }

        val failure = assertIs<SliceOutcome.Failure>(outcome)
        assertEquals(SliceFailureCode.INVALID_REQUEST, failure.code)
        assertFalse(engine.sliceCalled)
    }

    @Test
    fun `valid request and progress listener are delegated to engine`() {
        val engine = FakeEngine()
        val progress = mutableListOf<SliceProgress>()
        val outcome = runSuspend {
            SliceModelUseCase(engine)(
                request(),
                SliceProgressObserver(progress::add),
            )
        }

        assertEquals(engine.outcome, outcome)
        assertTrue(engine.sliceCalled)
        assertEquals(engine.progress, progress)
    }

    @Test
    fun `cancel use case targets a single job`() {
        val engine = FakeEngine()

        assertTrue(runSuspend { CancelSliceUseCase(engine)(JOB_ID) })
        assertEquals(JOB_ID, engine.cancelledJobId)
    }

    @Test
    fun `engine start-up failure is reported as not ready`() {
        val engine = object : SlicerEngine by FakeEngine() {
            override suspend fun status(): EngineStatus = error("profiles missing")
        }

        val status = runSuspend { GetEngineStatusUseCase(engine)() }

        assertFalse(status.ready)
        assertEquals("profiles missing", status.message)
    }

    private fun request(
        modelPath: String = "/models/cube.stl",
        outputPath: String = "/output/cube.gcode",
    ) = SliceRequest(
        jobId = JOB_ID,
        model = ModelSource.LocalFile(ModelPath(modelPath)),
        output = OutputPath(outputPath),
        printerProfile = ProfileId("printer"),
        filamentProfile = ProfileId("filament"),
        processProfile = ProfileId("process"),
    )

    private fun <T> runSuspend(block: suspend () -> T): T {
        var completion: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<T>) {
                completion = result
            }
        })
        return checkNotNull(completion).getOrThrow()
    }

    private class FakeEngine : SlicerEngine {
        var sliceCalled = false
        var lastRequest: SliceRequest? = null
        var cancelledJobId: SliceJobId? = null
        val progress = listOf(
            SliceProgress(
                jobId = JOB_ID,
                fraction = 1f,
                stage = app.orcinus.shadow.core.model.SliceStage.COMPLETED,
            ),
        )
        val outcome = SliceOutcome.Success(
            jobId = JOB_ID,
            gcodePath = OutputPath("/output/cube.gcode"),
            statistics = SliceStatistics(100, 3600, 1200.0),
        )

        override suspend fun status() = EngineStatus(EngineVersion("fake"), ready = true)

        override suspend fun slice(
            request: SliceRequest,
            progressListener: SliceProgressListener,
        ): SliceOutcome {
            sliceCalled = true
            lastRequest = request
            progress.forEach(progressListener::onProgress)
            return outcome
        }

        override suspend fun cancel(jobId: SliceJobId): Boolean {
            cancelledJobId = jobId
            return true
        }
    }

    private companion object {
        val JOB_ID = SliceJobId("job-1")
    }
}
