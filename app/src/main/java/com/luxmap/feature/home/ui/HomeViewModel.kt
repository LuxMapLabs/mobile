package com.luxmap.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.feature.home.data.HomeData
import com.luxmap.feature.home.data.HomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        repository: HomeRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
        val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                repository
                    .observeHomeData()
                    .catch { e ->
                        _uiState.value = HomeUiState.Error(e.message ?: "Không tải được dữ liệu")
                    }.collect { data ->
                        _uiState.value =
                            if (data.isEmpty()) {
                                HomeUiState.Empty
                            } else {
                                HomeUiState.Success(data = data, lastSyncedAt = Instant.now())
                            }
                    }
            }
        }
    }

// Rỗng khi không có việc nào để làm hôm nay — cả 3 loại lệnh sửa chữa lẫn đợt khảo sát đã lên
// kế hoạch đều bằng 0 (đúng nguyên tắc A6: không coi offline/cache là Error, chỉ Empty khi thật
// sự không có dữ liệu).
private fun HomeData.isEmpty(): Boolean =
    metrics.assignedCount == 0 &&
        metrics.inProgressCount == 0 &&
        metrics.overdueCount == 0 &&
        metrics.plannedSweepCount == 0
