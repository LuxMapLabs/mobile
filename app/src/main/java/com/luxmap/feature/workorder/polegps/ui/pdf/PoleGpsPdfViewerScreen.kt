package com.luxmap.feature.workorder.polegps.ui.pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.LuxChildTopBar
import com.luxmap.core.ui.components.LuxEmptyState
import com.luxmap.core.ui.components.LuxErrorState
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
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            PdfPage(renderer = renderer, pageIndex = page)
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
    var size by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .onSizeChanged { size = it }
                .pointerInput(pageIndex) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val cancelled = event.changes.any { it.isConsumed }
                            if (!cancelled) {
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                if (zoom != 1f || scale > 1f) {
                                    val newScale = (scale * zoom).coerceIn(1f, 4f)
                                    val maxX = size.width * (newScale - 1f) / 2f
                                    val maxY = size.height * (newScale - 1f) / 2f
                                    scale = newScale
                                    offset =
                                        Offset(
                                            (offset.x + pan.x).coerceIn(-maxX, maxX),
                                            (offset.y + pan.y).coerceIn(-maxY, maxY),
                                        )
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            }
                        } while (!cancelled && event.changes.any { it.pressed })
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            androidx.compose.foundation.Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Trang ${pageIndex + 1}",
                contentScale = ContentScale.FillWidth,
                modifier =
                    Modifier.fillMaxWidth().graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
            )
        }
    }
}
