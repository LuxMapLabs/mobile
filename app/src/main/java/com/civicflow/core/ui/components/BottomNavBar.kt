package com.civicflow.core.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.civicflow.core.theme.Navy

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

// Danh sách tab (5 tab theo NavGraph) do navigation/ truyền vào khi wire
// thật — tránh đoán trước icon/thứ tự tab ở đây khi chưa đối chiếu wireframe.
@Composable
fun BottomNavBar(
    items: List<BottomNavItem>,
    currentRoute: String?,
    onItemSelected: (BottomNavItem) -> Unit,
) {
    NavigationBar(containerColor = Navy) {
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
