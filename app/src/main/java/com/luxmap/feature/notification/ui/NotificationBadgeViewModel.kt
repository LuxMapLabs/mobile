package com.luxmap.feature.notification.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.feature.notification.data.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Hoisted once in NavGraph.kt (same ViewModelStoreOwner trick as SessionViewModel) so the badge
// count survives tab switches. Does not own a polling loop itself - NavGraph.kt's own
// LaunchedEffect calls refresh() on a timer gated by the Activity's own Lifecycle, keeping this
// class free of any Android Lifecycle/Compose dependency and easy to unit test.
@HiltViewModel
class NotificationBadgeViewModel
    @Inject
    constructor(
        private val repository: NotificationRepository,
    ) : ViewModel() {
        private val _unreadCount = MutableStateFlow(0)
        val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

        fun refresh() {
            viewModelScope.launch {
                repository.unreadCount().onSuccess { _unreadCount.value = it }
            }
        }

        // Called when the session ends (NavGraph.kt) - this ViewModel is Activity-scoped and
        // survives login/logout, so without this a signed-out-then-back-in-as-someone-else user
        // would see the PREVIOUS account's unread count until the next poll tick.
        fun reset() {
            _unreadCount.value = 0
        }
    }
