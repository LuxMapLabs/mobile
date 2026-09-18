package com.luxmap.feature.map.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.luxmap.core.theme.Dimens

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
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(Dimens.radiusMedium),
            placeholder = { Text("Tìm theo mã cột hoặc tên tuyến") },
            leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                // Opens the "Hiển thị trên bản đồ" sheet — layer/status display options, not a
                // search filter, so the icon reads as stacked map layers, not a filter/menu icon.
                IconButton(onClick = onFilterClick) {
                    Icon(imageVector = Icons.Filled.Layers, contentDescription = "Tùy chọn hiển thị bản đồ")
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
