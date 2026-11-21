package com.luxmap.feature.workorder.ui.detail

import androidx.lifecycle.SavedStateHandle
import com.luxmap.core.network.ConnectivityObserver
import com.luxmap.feature.survey.data.PoleListRepository
import com.luxmap.feature.survey.data.PoleListResult
import com.luxmap.feature.workorder.data.WorkOrderCompletionRepository
import com.luxmap.feature.workorder.data.WorkOrderDetail
import com.luxmap.feature.workorder.data.WorkOrderDetailRepository
import com.luxmap.feature.workorder.data.WorkOrderDraftRepository
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderCompletionEntity
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderEvidenceEntity
import com.luxmap.feature.workorder.polegps.data.PoleGpsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DetailMainDispatcherRule : TestWatcher() {
    val dispatcher = StandardTestDispatcher()

    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}

internal class DetailViewModelFixture {
    val details = mockk<WorkOrderDetailRepository>()
    val completions = mockk<WorkOrderCompletionRepository>(relaxed = true)
    val poles = mockk<PoleListRepository>()
    val drafts = mockk<WorkOrderDraftRepository>(relaxed = true)
    val completion = MutableStateFlow<LocalWorkOrderCompletionEntity?>(null)
    private val gps = mockk<PoleGpsRepository>(relaxed = true)
    private val connectivity = mockk<ConnectivityObserver>()

    init {
        every { completions.observeEvidenceList("W") } returns
            flowOf(
                listOf(
                    LocalWorkOrderEvidenceEntity(
                        "E", "W", "observation", "/photo.jpg", Instant.EPOCH, 10.0, 106.0, "draft",
                    ),
                ),
            )
        every { completions.observeCompletion("W") } returns completion
        every { gps.observeMeasurements("W") } returns flowOf(emptyList())
        every { gps.observeAttachments("W") } returns flowOf(emptyList())
        every { connectivity.isOnline } returns flowOf(false)
        coEvery { poles.poles("W") } returns Result.success(PoleListResult(emptyList(), false))
        coEvery { drafts.ownerUserId() } returns "U"
        coEvery { drafts.load("U", "W") } returns null
    }

    fun create(detail: WorkOrderDetail = detail()): WorkOrderDetailViewModel {
        every { details.observeWorkOrderDetail("W") } returns flowOf(detail)
        return WorkOrderDetailViewModel(
            SavedStateHandle(mapOf(WorkOrderDetailViewModel.WORK_ORDER_ID_ARG to "W")),
            details,
            completions,
            gps,
            poles,
            connectivity,
            drafts,
        )
    }

    fun detail(kind: String = "inspection") =
        WorkOrderDetail(
            workOrderId = "W", title = "Lệnh kiểm tra", woStatus = "in_progress", taskKind = kind,
            dueDate = null, scheduledDate = null, note = null,
            allowedActions =
                listOf(
                    "complete",
                ),
            faults = emptyList(),
        )
}

internal val WorkOrderDetailViewModel.success: WorkOrderDetailUiState.Success
    get() = uiState.value as WorkOrderDetailUiState.Success
