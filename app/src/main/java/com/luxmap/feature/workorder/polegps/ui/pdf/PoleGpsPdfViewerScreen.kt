package com.luxmap.feature.workorder.polegps.ui.pdf

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.AppTopBar
import com.luxmap.core.ui.components.PrimaryButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PoleGpsPdfViewerRoute(
    onBack: () -> Unit,
    viewModel: PoleGpsPdfViewerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    PoleGpsPdfViewerContent(state = state, onBack = onBack, onRetry = viewModel::onRetry)
}

@Composable
fun PoleGpsPdfViewerContent(
    state: PoleGpsPdfViewerUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = "Xem file PDF", onBack = onBack)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (state) {
                PoleGpsPdfViewerUiState.Loading -> CenteredPdfMessage("Đang mở file PDF...", showProgress = true)
                PoleGpsPdfViewerUiState.Empty -> CenteredPdfMessage("Không tìm thấy file này.")
                is PoleGpsPdfViewerUiState.Error ->
                    CenteredPdfMessage(
                        state.message,
                        actionLabel = if (state.canRetry) "Thử lại" else null,
                        onAction = onRetry,
                    )
                is PoleGpsPdfViewerUiState.Ready -> PdfPages(state.filePath)
            }
        }
    }
}

@Composable
private fun CenteredPdfMessage(
    text: String,
    showProgress: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        if (showProgress) CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        if (actionLabel != null) {
            PrimaryButton(text = actionLabel, onClick = onAction, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PdfPages(filePath: String) {
    val renderer = remember(filePath) { PdfPageRenderer(File(filePath)) }
    DisposableEffect(renderer) { onDispose { renderer.close() } }
    val pagerState = rememberPagerState(pageCount = { renderer.pageCount })
    Column(Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().weight(1f)) { page ->
            PdfPage(renderer = renderer, pageIndex = page)
        }
        Text(
            text = "Trang ${pagerState.currentPage + 1}/${renderer.pageCount}",
            modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun PdfPage(
    renderer: PdfPageRenderer,
    pageIndex: Int,
) {
    var bitmap by remember(pageIndex) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(pageIndex) {
        bitmap = withContext(Dispatchers.IO) { renderer.renderPage(pageIndex) }
    }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(pageIndex) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 4f)
                        offset += pan
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            androidx.compose.foundation.Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Trang ${pageIndex + 1}",
                modifier =
                    Modifier.graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
            )
        }
    }
}
