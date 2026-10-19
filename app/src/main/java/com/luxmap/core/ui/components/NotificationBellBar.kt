package com.luxmap.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.BadgeTextStyle
import com.luxmap.core.theme.Danger600
import com.luxmap.core.theme.Navy900
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing

// Persistent, thin top element shown on the 4 tab screens only - lives in NavGraph.kt's own
// Scaffold (symmetric with the existing bottomBar slot), not inside AppTopBar, because none of
// the 4 tab root screens (Home/Survey Hub/Map/Profile) use AppTopBar today (design spec section 3).
//
// Floats over screen content instead of reserving layout space: NavGraph.kt only feeds the
// Scaffold's bottom inset to NavHost (see its own comment on why), so each tab screen still owns
// its own top inset exactly as before - this bar applies statusBarsPadding() to itself so it
// still lands below the status bar without pushing every screen's content down underneath it.
//
// onDarkHero switches to the style the F02 redesign mockup uses on top of its navy hero
// (translucent white chip, white icon, dark-ringed badge) instead of the plain default-tint
// icon the other 3 tabs still use - NavGraph.kt passes this only when the current route is Home.
@Composable
fun NotificationBellBar(
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDarkHero: Boolean = false,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.End,
    ) {
        if (onDarkHero) {
            DarkHeroBell(unreadCount = unreadCount, onClick = onClick)
        } else {
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
}

private val DarkHeroBellSize = 48.dp
private val DarkHeroBadgeSize = 16.dp

@Composable
private fun DarkHeroBell(
    unreadCount: Int,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(DarkHeroBellSize)
                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(Radius.control))
                .testTag("notification_bell"),
        contentAlignment = Alignment.Center,
    ) {
        val description = if (unreadCount > 0) "Thông báo, $unreadCount chưa đọc" else "Thông báo"
        IconButton(onClick = onClick) {
            Icon(imageVector = Icons.Filled.Notifications, contentDescription = description, tint = Color.White)
        }
        if (unreadCount > 0) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = Spacing.xs, end = Spacing.xs)
                        .defaultMinSize(minWidth = DarkHeroBadgeSize, minHeight = DarkHeroBadgeSize)
                        .background(Danger600, CircleShape)
                        .border(2.dp, Navy900, CircleShape)
                        .padding(horizontal = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                    style = BadgeTextStyle,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
