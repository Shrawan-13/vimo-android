package com.teamshryne.vimo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Vimo palette.
 *
 * A video editor is a dark room: the picture has to be the brightest thing on
 * screen, so the chrome is a set of near-neutrals with a single vivid accent
 * that is reserved for the playhead and the primary action.
 */
object VimoColors {
    /** The window background, behind everything. */
    val Background = Color(0xFF0B0C0E)

    /** The panels (the inspector, the media bin). */
    val Surface = Color(0xFF141619)

    /** The raised elements (cards, the transport bar). */
    val SurfaceRaised = Color(0xFF1C1F24)

    /** The pressed or selected state of a surface. */
    val SurfaceActive = Color(0xFF262A31)

    /** The hairline that separates the panels. */
    val Outline = Color(0xFF2A2E35)

    /** The primary text. */
    val OnSurface = Color(0xFFE8EAED)

    /** The secondary text (timestamps, hints). */
    val OnSurfaceMuted = Color(0xFF9AA0A6)

    /** The disabled text. */
    val OnSurfaceDisabled = Color(0xFF5A6068)

    /** The accent: the playhead, the primary action, the selection. */
    val Accent = Color(0xFFFF5A5F)

    /** The accent used on the timeline for a video track. */
    val AccentVideo = Color(0xFF4C8DFF)

    /** The accent used on the timeline for an audio track. */
    val AccentAudio = Color(0xFF3FC98B)

    /** The warning colour (thermal pressure). */
    val Warning = Color(0xFFFFB020)

    /** The error colour. */
    val Error = Color(0xFFFF5A5F)

    /** The clip label colours, cycled per track. */
    val ClipPalette = listOf(
        Color(0xFF3A6EA5),
        Color(0xFF7A4E9E),
        Color(0xFF2F7D6B),
        Color(0xFF9E5B3F),
        Color(0xFF4E6E9E),
        Color(0xFF8E8A3F),
    )
}

private val VimoTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 0.4.sp,
    ),
)

private val VimoShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
)

/**
 * The Vimo theme.
 *
 * The app is always dark: a light chrome would wash out the preview, and
 * editors are used in dim rooms.
 */
@Composable
fun VimoTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = VimoColors.Accent,
            onPrimary = Color.Black,
            secondary = VimoColors.AccentVideo,
            background = VimoColors.Background,
            onBackground = VimoColors.OnSurface,
            surface = VimoColors.Surface,
            onSurface = VimoColors.OnSurface,
            surfaceVariant = VimoColors.SurfaceRaised,
            onSurfaceVariant = VimoColors.OnSurfaceMuted,
            outline = VimoColors.Outline,
            error = VimoColors.Error,
        ),
        typography = VimoTypography,
        shapes = VimoShapes,
        content = content,
    )
}
