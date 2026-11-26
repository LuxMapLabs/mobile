package com.luxmap.feature.workorder.polegps.ui.pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import com.luxmap.core.ui.components.LuxChildTopBar
import com.luxmap.core.ui.components.LuxEmptyState
import com.luxmap.core.ui.components.LuxErrorState
import com.luxmap.core.ui.components.LuxSecondaryButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
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
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        LuxChildTopBar(
            title = "Tuyến đường",
            onBack = onBack,
            subtitle = (state as? PoleGpsPdfViewerUiState.Ready)?.fileName,
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (state) {
                PoleGpsPdfViewerUiState.Loading -> OpeningMessage()
                PoleGpsPdfViewerUiState.Empty ->
                    LuxEmptyState(title = "Chưa có file", message = "Không tìm thấy file này.")
                is PoleGpsPdfViewerUiState.Error ->
                    if (state.canRetry) {
                        LuxErrorState(onRetry = onRetry, title = "Chưa mở được file", message = state.message)
                    } else {
                        LuxEmptyState(
                            title = "Chưa mở được file",
                            message = state.message,
                            icon = Icons.Rounded.ErrorOutline,
                        )
                    }
                is PoleGpsPdfViewerUiState.Ready -> PdfPages(state.filePath)
            }
        }
    }
}

@Composable
private fun OpeningMessage() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Text(
            text = "Đang mở file PDF...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PdfPages(filePath: String) {
    var renderer by remember(filePath) { mutableStateOf<PdfPageRenderer?>(null) }
    var openError by remember(filePath) { mutableStateOf<String?>(null) }
    LaunchedEffect(filePath) {
        var opened: PdfPageRenderer? = null
        try {
            opened = withContext(Dispatchers.IO) { PdfPageRenderer(File(filePath)) }
            renderer = opened
            awaitCancellation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            openError = "Không mở được file PDF này."
        } finally {
            opened?.close()
        }
    }
    when {
        openError != null ->
            LuxEmptyState(title = "Chưa mở được file", message = openError!!, icon = Icons.Rounded.ErrorOutline)
        renderer == null -> OpeningMessage()
        else -> PdfPagerBody(renderer!!)
    }
}

@Composable
private fun PdfPagerBody(renderer: PdfPageRenderer) {
    val pagerState = rememberPagerState(pageCount = { renderer.pageCount })
    var resetToken by remember { mutableIntStateOf(0) }
    var zoomed by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            PdfPage(
                renderer = renderer,
                pageIndex = page,
                resetToken = resetToken,
                onZoomChanged = { zoomed = it },
            )
        }
        Surface(
            color = MaterialTheme.colorScheme.inverseSurface,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(Spacing.lg),
        ) {
            Text(
                text = "Trang ${pagerState.currentPage + 1}/${renderer.pageCount}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            )
        }
        if (zoomed) {
            LuxSecondaryButton(
                text = "Đặt lại thu phóng",
                onClick = { resetToken++ },
                modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.md),
            )
        }
    }
}

@Composable
private fun PdfPage(
    renderer: PdfPageRenderer,
    pageIndex: Int,
    resetToken: Int,
    onZoomChanged: (Boolean) -> Unit,
) {
    var bitmap by remember(pageIndex) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(resetToken) {
        scale = 1f
        offset = Offset.Zero
    }
    LaunchedEffect(scale) { onZoomChanged(scale > 1f) }
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
