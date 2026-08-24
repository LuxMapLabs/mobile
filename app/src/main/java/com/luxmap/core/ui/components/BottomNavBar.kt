package com.luxmap.core.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

// 4 tab: Việc hôm nay · Khảo sát · Bản đồ · Cá nhân (mục A5 đặc tả) — danh sách item
// do navigation/ truyền vào khi wire thật, tránh đoán trước icon/thứ tự tab ở đây.
@Composable
fun BottomNavBar(
    items: List<BottomNavItem>,
    currentRoute: String?,
    onItemSelected: (BottomNavItem) -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.primary) {
        items.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = { onItemSelected(item) },
                icon = { Icon(imageVector = item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
            )
        }
    }
}
