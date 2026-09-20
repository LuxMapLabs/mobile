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
import com.luxmap.feature.auth.data.AuthRepository
import com.luxmap.navigation.NavGraph
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

// No safeDrawing inset padding here on purpose — each screen decides for itself which parts
// bleed under the status/navigation bars (e.g. a full-bleed map or hero background) and which
// parts need statusBarsPadding()/navigationBarsPadding()/windowInsetsPadding(safeDrawing). A
// blanket padding here would force every screen's background into the same inset-safe box,
// leaving the background color visible in a gap at the screen edges on screens that want to
// bleed to the edge.
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // savedInstanceState is null only on a fresh launch. When Android restores the app after
        // killing its process it is not null, so a user in the middle of work is not logged out.
        // This must finish before LoginViewModel reads the login state, so it blocks briefly
        // (one local DataStore read).
        if (savedInstanceState == null) {
            runBlocking { authRepository.endSessionIfNotRemembered() }
        }
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
