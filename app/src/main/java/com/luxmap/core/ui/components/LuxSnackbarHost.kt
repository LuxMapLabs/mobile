package com.luxmap.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.Spacing

@Composable
fun LuxSnackbarHost(
    state: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(hostState = state, modifier = modifier.fillMaxWidth()) { data ->
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            Snackbar(
                snackbarData = data,
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                actionColor = MaterialTheme.colorScheme.inversePrimary,
            )
        }
    }
}
