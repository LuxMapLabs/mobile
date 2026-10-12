package com.luxmap.feature.survey.ui.aiconfirm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.theme.AssetCondition
import com.luxmap.feature.auth.data.AuthRepository
import com.luxmap.feature.survey.data.AiClassificationRepository
import com.luxmap.feature.survey.data.LocalAiClassificationReview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AiConfirmationViewModel
    @Inject
    constructor(
        private val repository: AiClassificationRepository,
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<AiConfirmationUiState>(AiConfirmationUiState.Loading)
        val uiState: StateFlow<AiConfirmationUiState> = _uiState.asStateFlow()

        private val filterState = MutableStateFlow(ReviewFilter.ALL)

        private val _sheet = MutableStateFlow<AiReviewSheetState?>(null)
        val sheet: StateFlow<AiReviewSheetState?> = _sheet.asStateFlow()

        fun load(surveySweepId: String) {
            viewModelScope.launch {
                combine(repository.observeReviews(surveySweepId), filterState) { reviews, filter ->
                    if (reviews.isEmpty()) {
                        AiConfirmationUiState.Empty
                    } else {
                        AiConfirmationUiState.Success(
                            reviews = reviews.filter { matchesFilter(it, filter) },
                            filter = filter,
                            normalCount = reviews.count { it.finalClass == AssetCondition.NORMAL },
                            dimCount = reviews.count { it.finalClass == AssetCondition.DIM },
                            outCount = reviews.count { it.finalClass == AssetCondition.OUT },
                        )
                    }
                }.catch { e ->
                    _uiState.value = AiConfirmationUiState.Error(e.message ?: "Không tải được kết quả AI")
                }.collect { state -> _uiState.value = state }
            }
        }

        fun onFilterSelected(filter: ReviewFilter) {
            filterState.value = filter
        }

        fun onReviewSelected(review: LocalAiClassificationReview) {
            _sheet.value =
                AiReviewSheetState(
                    review = review,
                    chosenClass = review.finalClass,
                    reason = review.overrideReason ?: "",
                )
        }

        fun onDismissSheet() {
            _sheet.value = null
        }

        fun onConditionChosen(condition: AssetCondition) {
            _sheet.value = _sheet.value?.copy(chosenClass = condition)
        }

        fun onReasonChanged(reason: String) {
            _sheet.value = _sheet.value?.copy(reason = reason)
        }

        fun onSaveSheet() {
            val current = _sheet.value ?: return
            val isOverride = current.chosenClass != current.review.aiClass
            if (isOverride && current.reason.isBlank()) return
            viewModelScope.launch {
                val username = authRepository.observeUsername().first() ?: "Không rõ"
                repository.setFinalClass(
                    reviewId = current.review.reviewId,
                    finalClass = current.chosenClass,
                    overrideReason = if (isOverride) current.reason else null,
                    overriddenBy = username,
                )
                _sheet.value = null
            }
        }

        fun onSubmit(surveySweepId: String) {
            viewModelScope.launch { repository.submit(surveySweepId) }
        }

        private fun matchesFilter(
            review: LocalAiClassificationReview,
            filter: ReviewFilter,
        ): Boolean =
            when (filter) {
                ReviewFilter.ALL -> true
                ReviewFilter.NEEDS_REVIEW -> review.aiClass != AssetCondition.NORMAL || review.overrideReason != null
                ReviewFilter.OVERRIDDEN -> review.overrideReason != null
            }
    }
