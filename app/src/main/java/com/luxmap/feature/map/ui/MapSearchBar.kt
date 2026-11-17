package com.luxmap.feature.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

// Single search entry point for F12 (replaces the old always-open layer-toggle card) — the filter
// icon opens a bottom sheet with layer/status filters instead of a floating card, per FM-36.
// Search matches pole_id and segment_name/segment_id together (both confirmed fields, see
// FM-35/FM-36) — no separate tab per data type, field crew just type and see both kinds of
// results, see MapSearchResultsList.
@Composable
fun MapSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onFilterClick: () -> Unit,
    hiddenStatusCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            placeholder = { Text("Tìm theo mã cột hoặc tên tuyến") },
            leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                val description =
                    if (hiddenStatusCount > 0) {
                        "Lọc và lớp bản đồ. Đang lọc: ẩn $hiddenStatusCount trạng thái"
                    } else {
                        "Lọc và lớp bản đồ"
                    }
                IconButton(onClick = onFilterClick) {
                    Box {
                        Icon(imageVector = Icons.Filled.Layers, contentDescription = description)
                        if (hiddenStatusCount > 0) {
                            Box(
                                modifier =
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .size(8.dp)
                                        .background(MaterialTheme.colorScheme.error, CircleShape)
                                        .testTag("map_filter_dot"),
                            )
                        }
                    }
                }
            },
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
        )
    }
}
