package com.luxmap.feature.notification.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.feature.notification.data.NotificationItem
import com.luxmap.feature.notification.data.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationListViewModel
    @Inject
    constructor(
        private val repository: NotificationRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<NotificationListUiState>(NotificationListUiState.Loading)
        val uiState: StateFlow<NotificationListUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        fun onRetry() {
            _uiState.value = NotificationListUiState.Loading
            load()
        }

        fun onMarkRead(notificationId: String) {
            flipRead { it.notificationId == notificationId }
            viewModelScope.launch { repository.markRead(notificationId) }
        }

        fun onMarkAllRead() {
            flipRead { true }
            viewModelScope.launch { repository.markAllRead() }
        }

        fun onFilterSelected(filter: NotificationFilter) {
            val current = _uiState.value
            if (current is NotificationListUiState.Success) {
                _uiState.value = current.copy(filter = filter)
            }
        }

        // Optimistic UI (CLAUDE.md offline-first spirit): flips the row(s) immediately instead of
        // waiting for the 204 - the mark-read calls above are fire-and-forget, matching the
        // idempotent, side-effect-only nature of those two endpoints.
        private fun flipRead(matches: (NotificationListRowState) -> Boolean) {
            val current = _uiState.value
            if (current is NotificationListUiState.Success) {
                _uiState.value =
                    current.copy(items = current.items.map { if (matches(it)) it.copy(isRead = true) else it })
            }
        }

        private fun load() {
            viewModelScope.launch {
                repository.list().fold(
                    onSuccess = { page ->
                        _uiState.value =
                            if (page.items.isEmpty()) {
                                NotificationListUiState.Empty
                            } else {
                                NotificationListUiState.Success(page.items.map { it.toRowState() }, total = page.total)
                            }
                    },
                    onFailure = { e ->
                        _uiState.value =
                            NotificationListUiState.Error(e.message ?: "Không tải được danh sách thông báo")
                    },
                )
            }
        }

        private fun NotificationItem.toRowState() =
            NotificationListRowState(
                notificationId,
                type,
                title,
                body,
                entityType,
                entityId,
                createdAt,
                isRead = readAt != null,
            )
    }
