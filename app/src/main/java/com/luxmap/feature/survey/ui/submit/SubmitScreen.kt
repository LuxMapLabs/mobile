package com.luxmap.feature.survey.ui.submit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SubmitScreen(
    sessionId: String,
    viewModel: SubmitViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column {
            when (val state = uiState) {
                is SubmitUiState.Idle -> Button(onClick = { viewModel.onSubmit(sessionId) }) { Text("Nộp ngay") }

                is SubmitUiState.Uploading -> {
                    val fraction = state.bytesSent.toFloat() / state.totalBytes.toFloat()
                    LinearProgressIndicator(progress = { fraction })
                    Text("${state.bytesSent} / ${state.totalBytes} bytes")
                }

                is SubmitUiState.Done -> Text("Đã nộp thành công", style = MaterialTheme.typography.bodyLarge)

                is SubmitUiState.Error -> Text(state.message, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
