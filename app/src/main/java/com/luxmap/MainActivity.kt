package com.luxmap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.navigation.NavGraph
import dagger.hilt.android.AndroidEntryPoint

// No safeDrawing inset padding here on purpose — each screen decides for itself which parts
// bleed under the status/navigation bars (e.g. a full-bleed map or hero background) and which
// parts need statusBarsPadding()/navigationBarsPadding()/windowInsetsPadding(safeDrawing). A
// blanket padding here would force every screen's background into the same inset-safe box,
// leaving the background color visible in a gap at the screen edges on screens that want to
// bleed to the edge.
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LuxMapTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    NavGraph()
                }
            }
        }
    }
}
