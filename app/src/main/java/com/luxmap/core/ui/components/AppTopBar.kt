package com.luxmap.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.luxmap.core.theme.BeVietnamPro
import com.luxmap.core.theme.TitleTextStyle

private val SubtitleTextStyle =
    TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 16.sp)

// onBack is nullable for a screen that intentionally has no way back (e.g. F05 Coverage, which
// blocks the system back gesture too so a packaged session always gets an explicit decision) -
// null just omits the navigation icon, it never renders a disabled or no-op one.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)?,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Column {
                Text(text = title, style = TitleTextStyle)
                if (subtitle != null) {
                    Text(text = subtitle, style = SubtitleTextStyle)
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Quay lại")
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
    )
}
