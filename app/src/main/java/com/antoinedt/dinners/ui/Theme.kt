package com.antoinedt.dinners.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Light = lightColorScheme(
    primary = Color(0xFF3E6B3A),
    secondary = Color(0xFF8A5A2B),
    tertiary = Color(0xFF2A6A7A),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFA3D39A),
    secondary = Color(0xFFE6BD8F),
    tertiary = Color(0xFF8ED0E2),
)

@Composable
fun DinnersTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Categorical chart colors, in fixed order (validated light/dark steps). */
@Composable
fun seriesColors(): List<Color> = if (isSystemInDarkTheme()) {
    listOf(Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70), Color(0xFFC98500), Color(0xFFD55181))
} else {
    listOf(Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A), Color(0xFFEDA100), Color(0xFFE87BA4))
}

val OtherSeriesColor = Color(0xFF9A9A96)
