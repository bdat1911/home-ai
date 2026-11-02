package com.example.revroom.features.design_studio

import androidx.lifecycle.ViewModelStore
import com.example.revroom.features.design_studio.model.DesignJobStatus
import com.example.revroom.features.design_studio.model.DesignPhase
import com.example.revroom.features.design_studio.model.DesignResult
import com.example.revroom.features.design_studio.model.DesignStyle
import com.example.revroom.features.design_studio.model.DesignUiState
import com.example.revroom.features.design_studio.viewmodel.DesignViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DesignViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private var uploadCount = 0
    private var baseUrl = SERVER_A

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }

    @After fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test fun pollingFailureThenRetryChecksTheSameJobWithoutUploading() = runTest {
        val checkedIds = mutableListOf<String>()
        val vm = createViewModel {
            checkedIds += it
            if (checkedIds.size == 1) Result.failure(IOException("offline"))
            else Result.success(result(DesignJobStatus.Completed))
        }

        vm.retry()
        runCurrent()
        assertEquals(DesignPhase.StatusUnavailable, vm.uiState.value.phase)
        assertEquals(JOB_ID, vm.uiState.value.designId)

        vm.retry()
        vm.retry()
        vm.createDesign()
        runCurrent()
        assertEquals(listOf(JOB_ID, JOB_ID), checkedIds)
        assertEquals(DesignPhase.Completed, vm.uiState.value.phase)
        assertEquals(0, uploadCount)
    }

    @Test fun uncertainSubmissionNeverRetriesThePost() = runTest {
        val vm = createViewModel(
            initialState = DesignUiState(
                phase = DesignPhase.SubmissionUncertain,
                submissionAttempted = true,
                submittedApiBaseUrl = SERVER_A
            )
        ) { error("No status ID exists") }
        vm.retry()
        vm.createDesign()
        runCurrent()
        assertEquals(0, uploadCount)
        assertEquals(DesignPhase.SubmissionUncertain, vm.uiState.value.phase)
    }

    @Test fun requestTimeoutPausesStatusWithoutFailingOrResubmittingTheJob() = runTest {
        var statusCalls = 0
        val vm = createViewModel {
            statusCalls++
            awaitCancellation()
        }
        vm.retry()
        runCurrent()
        advanceTimeBy(300_001)
        runCurrent()
        assertEquals(DesignPhase.StatusUnavailable, vm.uiState.value.phase)
        assertEquals(JOB_ID, vm.uiState.value.designId)
        assertEquals(1, statusCalls)
        assertEquals(0, uploadCount)
    }

    @Test fun changingServerBlocksStatusRequestsForTheOldJob() = runTest {
        val vm = createViewModel { error("Must not read an old job on a new server") }
        baseUrl = SERVER_B
        vm.retry()
        runCurrent()
        assertEquals(DesignPhase.StatusUnavailable, vm.uiState.value.phase)
        assertTrue(vm.uiState.value.errorMessage.orEmpty().contains("address changed"))
    }

    @Test fun leavingScreenCancelsTheReadWithoutMarkingServerJobFailed() = runTest {
        var cancelled = false
        val vm = createViewModel {
            try { awaitCancellation() } finally { cancelled = true }
        }
        vm.retry()
        runCurrent()
        vm.leaveDesignScreen()
        runCurrent()
        assertTrue(cancelled)
        assertEquals(DesignPhase.StatusUnavailable, vm.uiState.value.phase)
        assertEquals(JOB_ID, vm.uiState.value.designId)
    }

    @Test fun serverChangeReloadsStylesAndDiscardsLateResponseFromOldServer() = runTest {
        val endpoint = MutableStateFlow(SERVER_A)
        val oldStyles = CompletableDeferred<List<DesignStyle>>()
        val newStyles = CompletableDeferred<List<DesignStyle>>()
        val vm = DesignViewModel(
            uploadDesign = { error("Not used") },
            getDesignStatus = { error("Not used") },
            getDesignStyles = {
                val response = if (baseUrl == SERVER_A) oldStyles else newStyles
                Result.success(withContext(NonCancellable) { response.await() })
            },
            apiBaseUrl = { baseUrl },
            endpointChanges = endpoint
        )
        store.put("styles", vm)
        runCurrent()
        baseUrl = SERVER_B
        endpoint.value = SERVER_B
        runCurrent()
        newStyles.complete(listOf(DesignStyle(2, "New server style")))
        runCurrent()
        oldStyles.complete(listOf(DesignStyle(1, "Old server style")))
        runCurrent()
        assertEquals(listOf("New server style"), vm.designStyles.value.map { it.label })
        assertEquals(false, vm.isStylesLoading.value)
    }

    private fun createViewModel(
        initialState: DesignUiState = DesignUiState(
            phase = DesignPhase.StatusUnavailable,
            designId = JOB_ID,
            submissionAttempted = true,
            submittedApiBaseUrl = SERVER_A
        ),
        status: suspend (String) -> Result<DesignResult>
    ): DesignViewModel = DesignViewModel(
        uploadDesign = { uploadCount++; error("A status retry must never submit a design") },
        getDesignStatus = status,
        getDesignStyles = { Result.success(emptyList()) },
        apiBaseUrl = { baseUrl },
        endpointChanges = emptyFlow(),
        initialState = initialState
    ).also { store.put("design", it) }

    private fun result(status: DesignJobStatus) = DesignResult(
        designId = JOB_ID,
        status = status,
        originalImageUrl = "https://images.example/original.jpg",
        designedImageUrl = "https://images.example/result.jpg",
        errorMessage = null
    )

    companion object {
        private const val SERVER_A = "http://192.168.1.2:5207/"
        private const val SERVER_B = "http://192.168.1.3:5207/"
        private const val JOB_ID = "existing-design"
    }
}
