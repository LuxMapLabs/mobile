package com.luxmap.core.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.luxmap.core.theme.BadgeTextStyle
import com.luxmap.core.theme.NavBarHeight

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

// 4 tab: Việc hôm nay · Khảo sát · Bản đồ · Cá nhân (mục A5 đặc tả) — danh sách item
// do navigation/ truyền vào khi wire thật, tránh đoán trước icon/thứ tự tab ở đây.
// The selected tab's pill indicator uses primaryContainer/onPrimaryContainer (v2.1 token,
// Phase 0) instead of the old plain primary/surfaceVariant pair. Icon FILL-when-active
// (spec §3) is not implemented here — see Phase 1 plan's Review Focus for why.
@Composable
fun BottomNavBar(
    items: List<BottomNavItem>,
    currentRoute: String?,
    onItemSelected: (BottomNavItem) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.height(NavBarHeight),
    ) {
        items.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = { onItemSelected(item) },
                icon = { Icon(imageVector = item.icon, contentDescription = null) },
                label = { Text(item.label, style = BadgeTextStyle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors =
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            )
        }
    }
}
