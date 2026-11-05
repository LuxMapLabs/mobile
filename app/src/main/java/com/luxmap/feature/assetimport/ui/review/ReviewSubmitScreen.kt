package com.luxmap.feature.assetimport.ui.review

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun ReviewSubmitRoute(
    tripId: String,
    onBack: () -> Unit,
    onSubmitted: (String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Xem lại & Gửi · $tripId")
    }
}
