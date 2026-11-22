package com.luxmap.feature.workorder.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.network.ConnectivityObserver
import com.luxmap.feature.survey.data.PoleListRepository
import com.luxmap.feature.workorder.data.WorkOrderCompletionRepository
import com.luxmap.feature.workorder.data.WorkOrderDetail
import com.luxmap.feature.workorder.data.WorkOrderDetailRepository
import com.luxmap.feature.workorder.data.WorkOrderDraft
import com.luxmap.feature.workorder.data.WorkOrderDraftRepository
import com.luxmap.feature.workorder.data.sync.FaultOutcome
import com.luxmap.feature.workorder.polegps.data.PoleGpsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class WorkOrderDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val detailRepository: WorkOrderDetailRepository,
        private val completionRepository: WorkOrderCompletionRepository,
        private val poleGpsRepository: PoleGpsRepository,
        private val poleListRepository: PoleListRepository,
        private val connectivityObserver: ConnectivityObserver,
        private val draftRepository: WorkOrderDraftRepository,
    ) : ViewModel() {
        private val workOrderId: String = checkNotNull(savedStateHandle[WORK_ORDER_ID_ARG])

        private val _uiState = MutableStateFlow<WorkOrderDetailUiState>(WorkOrderDetailUiState.Loading)
        val uiState: StateFlow<WorkOrderDetailUiState> = _uiState.asStateFlow()

        private val orderPolesState = MutableStateFlow<OrderPolesUiState>(OrderPolesUiState.Loading)

        private val detailState = MutableStateFlow<WorkOrderDetail?>(null)
        private val poleReferencesState =
            MutableStateFlow<List<com.luxmap.feature.workorder.polegps.data.PoleReference>>(emptyList())

        private val _startedSurveyWorkOrderId = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val startedSurveyWorkOrderId: SharedFlow<String> = _startedSurveyWorkOrderId.asSharedFlow()

        private val submitEventChannel = Channel<ReportSubmitEvent>(Channel.BUFFERED)
        val submitEvents = submitEventChannel.receiveAsFlow()

        private var polesJob: Job? = null
        private var loadJob: Job? = null
        private val draftMutex = Mutex()
        private var draftJob: Job? = null
        private var draftRevision = 0L
        private var draftDirty = false
        private var draftLoaded = false
        private var draftOwner: String? = null

        init {
            load()
        }

        fun retry() {
            val current = _uiState.value
            if (current !is WorkOrderDetailUiState.Error && current !is WorkOrderDetailUiState.Empty) return
            detailState.value = null
            _uiState.value = WorkOrderDetailUiState.Loading
            load()
        }

        private fun load() {
            loadJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    collectDetail()
                }
        }

        private suspend fun collectDetail() =
            coroutineScope {
                launch {
                    val detail =
                        try {
                            detailRepository.observeWorkOrderDetail(workOrderId).first()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            _uiState.value =
                                WorkOrderDetailUiState.Error(
                                    message = e.message ?: "Không tải được dữ liệu lệnh",
                                    isNetworkError = e is IOException,
                                )
                            return@launch
                        }
                    if (detail == null) {
                        _uiState.value = WorkOrderDetailUiState.Empty
                    } else {
                        if (detail.taskKind == "pole_gps_update") {
                            poleListRepository.poles(workOrderId)
                            poleReferencesState.value = poleGpsRepository.poleReferences(workOrderId)
                        }
                        detailState.value = detail
                        if (detail.taskKind in setOf("inspection", "repair")) {
                            retryPoles()
                        }
                    }
                }
                combine(
                    detailState,
                    completionRepository.observeEvidenceList(workOrderId),
                    completionRepository.observeCompletion(workOrderId),
                    connectivityObserver.isOnline,
                ) { latestDetail, evidence, completion, isOnline -> Quad(latestDetail, evidence, completion, isOnline) }
                    .combine(
                        poleGpsRepository.observeMeasurements(workOrderId),
                    ) { quad, measurements -> quad to measurements }
                    .combine(poleGpsRepository.observeAttachments(workOrderId)) { (quad, measurements), attachments ->
                        Triple(quad, measurements, attachments)
                    }
                    .combine(poleReferencesState) { (quad, measurements, attachments), references ->
                        Quint(
                            quad.first,
                            quad.second,
                            quad.third,
                            quad.fourth,
                            PoleGpsData(measurements, attachments, references),
                        )
                    }
                    .combine(orderPolesState) { data, poles -> data to poles }
                    .collect { (data, poles) ->
                        val (latestDetail, evidence, completion, isOnline, poleGpsData) = data
                        if (latestDetail != null) {
                            val current = _uiState.value as? WorkOrderDetailUiState.Success
                            val next =
                                current?.copy(
                                    detail = latestDetail,
                                    localEvidence = evidence,
                                    localCompletion = completion,
                                    isOnline = isOnline,
                                    poleMeasurements = poleGpsData.measurements,
                                    poleAttachments = poleGpsData.attachments,
                                    poleReferences = poleGpsData.references,
                                    orderPoles = poles,
                                ) ?: WorkOrderDetailUiState.Success(
                                    detail = latestDetail,
                                    localEvidence = evidence,
                                    localCompletion = completion,
                                    isOnline = isOnline,
                                    poleMeasurements = poleGpsData.measurements,
                                    poleAttachments = poleGpsData.attachments,
                                    poleReferences = poleGpsData.references,
                                    orderPoles = poles,
                                )
                            val restoredDraft =
                                completion?.takeIf {
                                    it.submitStatus == "draft" && current?.localCompletion?.submitStatus != "draft"
                                }
                            _uiState.value =
                                if (restoredDraft == null) {
                                    next
                                } else {
                                    val outcomes =
                                        restoredDraft.faultOutcomesJson?.let {
                                            Json.decodeFromString<List<FaultOutcome>>(it)
                                        }.orEmpty()
                                    next.copy(
                                        reportNote = restoredDraft.reportNote,
                                        materialsUsed = restoredDraft.materialsUsed.orEmpty(),
                                        faultOutcomes = outcomes.associate { it.faultId to it.outcome },
                                        justSubmitted = false,
                                    )
                                }
                            if (!draftLoaded) {
                                draftLoaded = true
                                restoreDraft()
                            }
                        }
                    }
            }

        fun retryPoles() {
            if (detailState.value?.taskKind !in setOf("inspection", "repair") || polesJob?.isActive == true) return
            orderPolesState.value = OrderPolesUiState.Loading
            polesJob =
                viewModelScope.launch {
                    val result =
                        try {
                            poleListRepository.poles(workOrderId)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Result.failure(e)
                        }
                    orderPolesState.value =
                        result.fold(
                            onSuccess = {
                                if (it.poles.isEmpty()) {
                                    OrderPolesUiState.Empty
                                } else {
                                    OrderPolesUiState.Success(it.poles, it.isStale)
                                }
                            },
                            onFailure = { OrderPolesUiState.Error(it.message ?: "Không tải được danh sách cột") },
                        )
                }
        }

        fun retryAttachment(attachmentId: String) {
            viewModelScope.launch { poleGpsRepository.retryAttachment(workOrderId, attachmentId) }
        }

        fun start() {
            val current = _uiState.value
            if (current !is WorkOrderDetailUiState.Success || current.isStarting) return
            _uiState.value = current.copy(isStarting = true, startError = null)
            viewModelScope.launch {
                detailRepository.start(workOrderId).fold(
                    onSuccess = { detail ->
                        updateSuccess { it.copy(detail = detail, isStarting = false, startError = null) }
                        detailState.value = detail
                        if (detail.taskKind == "survey") {
                            _startedSurveyWorkOrderId.tryEmit(workOrderId)
                        }
                    },
                    onFailure = { e ->
                        updateSuccess { it.copy(isStarting = false, startError = startErrorMessage(e)) }
                    },
                )
            }
        }

        fun onReportNoteChanged(value: String) {
            if ((_uiState.value as? WorkOrderDetailUiState.Success)?.isSubmitting == true) return
            updateSuccess { it.copy(reportNote = value) }
            draftDirty = true
            draftRevision++
            scheduleDraftSave()
        }

        fun onMaterialsUsedChanged(value: String) {
            if ((_uiState.value as? WorkOrderDetailUiState.Success)?.isSubmitting == true) return
            updateSuccess { it.copy(materialsUsed = value) }
            draftDirty = true
            draftRevision++
            scheduleDraftSave()
        }

        fun onFaultOutcomeSelected(
            faultId: String,
            outcome: String,
        ) {
            if ((_uiState.value as? WorkOrderDetailUiState.Success)?.isSubmitting == true) return
            updateSuccess { it.copy(faultOutcomes = it.faultOutcomes + (faultId to outcome)) }
            draftDirty = true
            draftRevision++
            scheduleDraftSave()
        }

        fun submit() {
            val current = _uiState.value
            if (current !is WorkOrderDetailUiState.Success || !current.canSubmit() ||
                current.isSubmitting || current.justSubmitted
            ) {
                return
            }
            _uiState.value = current.copy(isSubmitting = true, submitError = null)
            viewModelScope.launch {
                try {
                    draftJob?.cancelAndJoin()
                    prepareReworkIfNeeded(current)
                    val faultOutcomes =
                        if (current.detail.taskKind == "inspection" && current.detail.faults.isNotEmpty()) {
                            current.detail.faults.map { fault ->
                                FaultOutcome(fault.faultId, current.faultOutcomes.getValue(fault.faultId))
                            }
                        } else {
                            null
                        }
                    completionRepository.submitCompletion(
                        workOrderId = workOrderId,
                        taskKind = current.detail.taskKind,
                        reportNote = current.reportNote,
                        materialsUsed = current.materialsUsed.ifBlank { null },
                        faultOutcomes = faultOutcomes,
                    )
                    updateSuccess { it.copy(justSubmitted = true) }
                    // Submission already succeeded locally. A cleanup failure must not turn it into
                    // a failed submit or invite a duplicate submission.
                    try {
                        draftMutex.withLock {
                            draftOwner?.let { draftRepository.delete(it, workOrderId) }
                            draftDirty = false
                        }
                        updateSuccess { it.copy(hasDraft = false, draftError = null) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        updateSuccess { it.copy(draftError = "Báo cáo đã lưu; chưa xoá được bản nháp") }
                    }
                    submitEventChannel.send(ReportSubmitEvent.SavedLocally)
                    val afterSubmit = _uiState.value
                    if (afterSubmit is WorkOrderDetailUiState.Success) {
                        _uiState.value = afterSubmit.copy(isSubmitting = false, justSubmitted = true)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    updateSuccess {
                        it.copy(
                            submitError = e.message ?: "Không lưu được báo cáo.",
                            justSubmitted = false,
                        )
                    }
                    submitEventChannel.send(ReportSubmitEvent.Failed(e.message ?: "Không lưu được báo cáo."))
                } finally {
                    updateSuccess { it.copy(isSubmitting = false) }
                    if ((_uiState.value as? WorkOrderDetailUiState.Success)?.justSubmitted != true) {
                        scheduleDraftSave()
                    }
                }
            }
        }

        fun requestCancellation(reason: String) {
            val current = _uiState.value
            if (current !is WorkOrderDetailUiState.Success || current.isRequestingCancellation) return
            _uiState.value = current.copy(isRequestingCancellation = true, cancellationError = null)
            viewModelScope.launch {
                detailRepository.requestCancellation(workOrderId, reason).fold(
                    onSuccess = { detail ->
                        updateSuccess { it.copy(detail = detail, isRequestingCancellation = false) }
                        detailState.value = detail
                    },
                    onFailure = { e ->
                        updateSuccess {
                            it.copy(
                                isRequestingCancellation = false,
                                cancellationError = cancellationErrorMessage(e),
                            )
                        }
                    },
                )
            }
        }

        fun removeEvidence(clientOpId: String) {
            viewModelScope.launch {
                completionRepository.removeDraftEvidence(workOrderId, clientOpId)
            }
        }

        fun recoverEvidenceSubmission(replace: Boolean) {
            val current = _uiState.value as? WorkOrderDetailUiState.Success ?: return
            if (current.isSubmitting || current.localCompletion?.submitStatus != "failed") return
            updateSuccess { it.copy(isSubmitting = true, submitError = null) }
            viewModelScope.launch {
                val changed =
                    try {
                        if (replace) {
                            completionRepository.reopenEvidenceSubmission(workOrderId)
                        } else {
                            completionRepository.retryEvidenceSubmission(workOrderId)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        false
                    }
                updateSuccess {
                    it.copy(
                        isSubmitting = false,
                        submitError = if (changed) null else "Không cập nhật được bản nộp. Vui lòng thử lại.",
                    )
                }
            }
        }

        fun prepareEvidenceCapture(onReady: () -> Unit) {
            val current = _uiState.value as? WorkOrderDetailUiState.Success ?: return
            viewModelScope.launch {
                flushDraft()
                prepareReworkIfNeeded(current)
                onReady()
            }
        }

        private fun WorkOrderDetailUiState.Success.isDraftEditable(): Boolean =
            "complete" in detail.allowedActions && !hasActiveLocalCompletion() && !justSubmitted &&
                !workOrderScheduleWindow(detail.scheduledDate, LocalDate.now()).isOverdueToComplete

        private suspend fun restoreDraft() {
            try {
                draftOwner = draftRepository.ownerUserId()
                val owner = draftOwner ?: return
                val current = _uiState.value as? WorkOrderDetailUiState.Success ?: return
                if (!current.isDraftEditable()) return
                val draft = draftRepository.load(owner, workOrderId) ?: return
                val latest = _uiState.value as? WorkOrderDetailUiState.Success ?: return
                if (draftDirty || !latest.isDraftEditable() || latest.isSubmitting) return
                updateSuccess {
                    it.copy(
                        reportNote = draft.reportNote,
                        materialsUsed = draft.materialsUsed,
                        faultOutcomes = draft.faultOutcomes,
                        hasDraft = true,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                updateSuccess { it.copy(draftError = "Không đọc được bản nháp trên máy") }
            }
        }

        private fun scheduleDraftSave() {
            draftJob?.cancel()
            if (!draftDirty) return
            draftJob =
                viewModelScope.launch {
                    delay(500)
                    saveDraft()
                }
        }

        suspend fun flushDraft() {
            draftJob?.cancelAndJoin()
            saveDraft()
        }

        private suspend fun saveDraft() =
            draftMutex.withLock {
                val current = _uiState.value as? WorkOrderDetailUiState.Success ?: return@withLock
                if (!draftDirty || !current.isDraftEditable() || current.isSubmitting) return@withLock
                val revision = draftRevision
                try {
                    val owner =
                        draftOwner ?: draftRepository.ownerUserId()?.also { draftOwner = it }
                            ?: error("Chưa xác định được người dùng để lưu nháp")
                    draftRepository.save(
                        owner,
                        workOrderId,
                        WorkOrderDraft(current.reportNote, current.materialsUsed, current.faultOutcomes),
                    )
                    if (revision == draftRevision) draftDirty = false
                    updateSuccess { it.copy(hasDraft = true, draftError = null) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    updateSuccess { it.copy(draftError = "Chưa lưu được bản nháp trên máy") }
                }
            }

        private suspend fun prepareReworkIfNeeded(state: WorkOrderDetailUiState.Success) {
            val previous = state.localCompletion ?: return
            if (state.detail.taskKind !in listOf("repair", "inspection", "pole_gps_update")) return
            if (previous.submitStatus == "synced" && isReturnedForRework(state)) {
                completionRepository.prepareEvidenceRework(workOrderId, previous.clientOpId)
            }
        }

        private fun updateSuccess(transform: (WorkOrderDetailUiState.Success) -> WorkOrderDetailUiState.Success) {
            val current = _uiState.value
            if (current is WorkOrderDetailUiState.Success) {
                _uiState.value = transform(current)
            }
        }

        private fun startErrorMessage(e: Throwable): String =
            if (e is IOException) "Mất kết nối. Vui lòng thử lại." else "Không bắt đầu được lệnh này"

        private fun cancellationErrorMessage(e: Throwable): String =
            if (e is IOException) "Mất kết nối. Vui lòng thử lại." else "Không gửi được yêu cầu hủy"

        private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

        private data class Quint<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)

        private data class PoleGpsData(
            val measurements: List<com.luxmap.feature.workorder.polegps.data.PoleMeasurement>,
            val attachments: List<com.luxmap.feature.workorder.polegps.data.PoleGpsAttachment>,
            val references: List<com.luxmap.feature.workorder.polegps.data.PoleReference>,
        )

        companion object {
            const val WORK_ORDER_ID_ARG = "workOrderId"
        }
    }
