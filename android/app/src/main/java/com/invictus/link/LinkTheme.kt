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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object InvictusBrand {
    val NavyDeep = Color(0xFF08090A)
    val Navy = Color(0xFF0B0B1E)
    val NavySurface = Color(0xFF12122A)
    val NavyElevated = Color(0xFF1A1A32)
    val White = Color(0xFFE8EAED)
    val Muted = Color(0xFF9BA3B5)
    val Accent = Color(0xFF5B6EE8)
    val Success = Color(0xFF4ADE80)
    val Warning = Color(0xFFFBBF24)
    val Error = Color(0xFFF87171)
    val Hairline = Color(0x14FFFFFF)
    val HairlineStrong = Color(0x24FFFFFF)
    val Outline = Color(0xFF2A2A40)
}

val InvictusFontFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold),
)

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
    outline = InvictusBrand.Outline,
)

val InvictusTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = InvictusFontFamily,
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.8).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = InvictusFontFamily,
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.35).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = InvictusFontFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = InvictusFontFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = InvictusFontFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.15.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = InvictusFontFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.1.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = InvictusFontFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.2.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = InvictusFontFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = InvictusFontFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = InvictusFontFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.3.sp,
    ),
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
