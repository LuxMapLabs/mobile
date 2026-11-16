package com.luxmap.core.ui.components

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Night Field",
    showBackground = true,
    backgroundColor = 0xFF0D0D0D,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(name = "Light · font 2.0", showBackground = true, fontScale = 2f)
annotation class LuxPreviews
