package com.luxmap.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.luxmap.core.theme.Spacing

// Persistent, thin top element shown on the 4 tab screens only - lives in NavGraph.kt's own
// Scaffold (symmetric with the existing bottomBar slot), not inside AppTopBar, because none of
// the 4 tab root screens (Home/Survey Hub/Map/Profile) use AppTopBar today (design spec section 3).
//
// Floats over screen content instead of reserving layout space: NavGraph.kt only feeds the
// Scaffold's bottom inset to NavHost (see its own comment on why), so each tab screen still owns
// its own top inset exactly as before - this bar applies statusBarsPadding() to itself so it
// still lands below the status bar without pushing every screen's content down underneath it.
@Composable
fun NotificationBellBar(
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.End,
    ) {
        BadgedBox(
            badge = {
                if (unreadCount > 0) {
                    Badge { Text(if (unreadCount > 99) "99+" else unreadCount.toString()) }
                }
            },
        ) {
            IconButton(onClick = onClick, modifier = Modifier.testTag("notification_bell")) {
                val description = if (unreadCount > 0) "Thông báo, $unreadCount chưa đọc" else "Thông báo"
                Icon(imageVector = Icons.Filled.Notifications, contentDescription = description)
            }
        }
    }
}
