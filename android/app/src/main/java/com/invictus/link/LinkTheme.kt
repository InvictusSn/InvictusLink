package com.invictus.link

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object InvictusBrand {
    val Navy = Color(0xFF0B0B1E)
    val NavySurface = Color(0xFF12122A)
    val NavyElevated = Color(0xFF1A1A32)
    val White = Color(0xFFE0E0E0)
    val Muted = Color(0xFF9BA3B5)
    val Accent = Color(0xFF5B6EE8)
    val Success = Color(0xFF4ADE80)
    val Warning = Color(0xFFFBBF24)
    val Error = Color(0xFFF87171)
}

@Composable
fun invictusColorScheme() = darkColorScheme(
    primary = InvictusBrand.Accent,
    onPrimary = InvictusBrand.White,
    primaryContainer = InvictusBrand.NavyElevated,
    onPrimaryContainer = InvictusBrand.White,
    secondary = InvictusBrand.Muted,
    onSecondary = InvictusBrand.Navy,
    background = InvictusBrand.Navy,
    onBackground = InvictusBrand.White,
    surface = InvictusBrand.NavySurface,
    onSurface = InvictusBrand.White,
    surfaceVariant = InvictusBrand.NavyElevated,
    onSurfaceVariant = InvictusBrand.Muted,
    error = InvictusBrand.Error,
    onError = InvictusBrand.Navy,
    outline = Color(0xFF2A2A40),
)

val InvictusTypography = Typography(
    headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

enum class BottomTab(val label: String, val selectedIcon: ImageVector, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home, Icons.Outlined.Home),
    Activity("Activity", Icons.Filled.Assessment, Icons.Outlined.Assessment),
    Connection("Connection", Icons.Filled.Link, Icons.Outlined.Link),
    Settings("Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
}

object LinkIcons {
    val Build = Icons.Filled.Build
    val BuildOutlined = Icons.Outlined.Build
}

@Composable
fun invictusButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = InvictusBrand.Accent,
    contentColor = InvictusBrand.White,
    disabledContainerColor = InvictusBrand.Accent.copy(alpha = 0.4f),
    disabledContentColor = InvictusBrand.White.copy(alpha = 0.6f),
)

@Composable
fun invictusOutlinedButtonColors(): ButtonColors = ButtonDefaults.outlinedButtonColors(
    contentColor = InvictusBrand.White,
    disabledContentColor = InvictusBrand.Muted,
)

@Composable
fun InvictusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = invictusColorScheme(),
        typography = InvictusTypography,
        content = content,
    )
}
