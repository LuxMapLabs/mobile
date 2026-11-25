package com.luxmap.core.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

internal class StatusBarIconsCounter(
    private val apply: (lightIcons: Boolean) -> Unit,
) {
    private var owners = 0

    fun acquire() {
        owners += 1
        if (owners == 1) apply(true)
    }

    fun release() {
        if (owners == 0) return
        owners -= 1
        if (owners == 0) apply(false)
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private var activeController: WindowInsetsControllerCompat? = null
private var restoredLightStatusBars: Boolean = true

private val sharedCounter =
    StatusBarIconsCounter { light ->
        val controller = activeController
        if (controller != null) {
            if (light) restoredLightStatusBars = controller.isAppearanceLightStatusBars
            controller.isAppearanceLightStatusBars = if (light) false else restoredLightStatusBars
            if (!light) activeController = null
        }
    }

@Composable
internal fun LightStatusBarIcons() {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        if (window == null) {
            onDispose { }
        } else {
            activeController = WindowCompat.getInsetsController(window, view)
            sharedCounter.acquire()
            onDispose { sharedCounter.release() }
        }
    }
}
