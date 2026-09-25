package com.tagsmith.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Semantic roles for the board's palette. Material 3's scheme carries the same
 * colours so that stock components inherit them, but screens read these names:
 * they say what a colour is for rather than what it looks like.
 */
@Immutable
data class TagsmithPalette(
    val isDark: Boolean,
    val ground: Color,
    val groundSubtle: Color,
    val groundBar: Color,
    val neutralTint: Color,
    val accentTint: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val inkFainter: Color,
    val rule: Color,
    val hairline: Color,
    val hairlineWarm: Color,
    val border: Color,
    val borderDashed: Color,
    val trackOff: Color,
    val accent: Color,
    val accentPressed: Color,
    val accentOn: Color,
    val accentLight: Color,
    val success: Color,
    val successTint: Color,
    val danger: Color,
    val dangerTint: Color,
    val dangerBorder: Color,
    val locked: Color,
    val lockedTint: Color,
)

private val LightPalette = TagsmithPalette(
    isDark = false,
    ground = Ground,
    groundSubtle = GroundSubtle,
    groundBar = GroundBar,
    neutralTint = NeutralTint,
    accentTint = EmberTint,
    ink = Ink,
    inkMuted = InkMuted,
    inkFaint = InkFaint,
    inkFainter = InkFainter,
    rule = Ink,
    hairline = Hairline,
    hairlineWarm = HairlineWarm,
    border = BorderSoft,
    borderDashed = BorderDashed,
    trackOff = TrackOff,
    accent = Ember,
    accentPressed = EmberPressed,
    accentOn = Color.White,
    accentLight = EmberLight,
    success = SuccessInk,
    successTint = SuccessTint,
    danger = DangerInk,
    dangerTint = DangerTint,
    dangerBorder = DangerBorder,
    locked = LockedInk,
    lockedTint = LockedTint,
)

private val DarkPalette = TagsmithPalette(
    isDark = true,
    ground = DarkGround,
    groundSubtle = DarkSurface,
    groundBar = DarkSurface,
    neutralTint = DarkSurfaceHigh,
    accentTint = DarkSurfaceWarn,
    ink = DarkOn,
    inkMuted = DarkOnMuted,
    inkFaint = DarkOnFaint,
    inkFainter = DarkOnWarn,
    rule = DarkRule,
    hairline = DarkHairline,
    hairlineWarm = DarkHairline,
    border = DarkBorder,
    borderDashed = DarkBorder,
    trackOff = DarkBorder,
    accent = Ember,
    accentPressed = EmberPressed,
    accentOn = Color.White,
    accentLight = EmberLight,
    success = DarkSuccess,
    successTint = SuccessGround,
    danger = DarkDanger,
    dangerTint = DarkSurfaceWarn,
    dangerBorder = DarkDanger,
    locked = LockedTint,
    lockedTint = DarkSurfaceHigh,
)

/**
 * The dark ground the tap always uses. Scan, batch and the tap prompt render on
 * this whatever the app theme is — the rings need a dark field to radiate into.
 */
val TapPalette = DarkPalette

val LocalTagsmithPalette = staticCompositionLocalOf { LightPalette }

object Tagsmith {
    val colors: TagsmithPalette
        @Composable @ReadOnlyComposable get() = LocalTagsmithPalette.current
    val type = TagsmithType
}

private fun schemeFor(p: TagsmithPalette) = if (p.isDark) {
    darkColorScheme(
        primary = p.accent,
        onPrimary = p.accentOn,
        background = p.ground,
        onBackground = p.ink,
        surface = p.ground,
        onSurface = p.ink,
        surfaceVariant = p.groundSubtle,
        onSurfaceVariant = p.inkMuted,
        outline = p.border,
        outlineVariant = p.hairline,
        error = p.danger,
        onError = p.accentOn,
    )
} else {
    lightColorScheme(
        primary = p.accent,
        onPrimary = p.accentOn,
        background = p.ground,
        onBackground = p.ink,
        surface = p.ground,
        onSurface = p.ink,
        surfaceVariant = p.groundSubtle,
        onSurfaceVariant = p.inkMuted,
        outline = p.border,
        outlineVariant = p.hairline,
        error = p.danger,
        onError = p.accentOn,
    )
}

/** Nothing rounds a corner — radius is 0 on purpose, everywhere. */
private val Square = RoundedCornerShape(0.dp)

private val SquareShapes = Shapes(
    extraSmall = Square,
    small = Square,
    medium = Square,
    large = Square,
    extraLarge = Square,
)

private val M3Typography = Typography(
    displayLarge = TagsmithType.Hero,
    headlineLarge = TagsmithType.ScreenTitle,
    titleLarge = TagsmithType.SheetTitle,
    titleMedium = TagsmithType.RowTitle,
    bodyLarge = TagsmithType.Body,
    bodyMedium = TagsmithType.BodySmall,
    labelLarge = TagsmithType.Button,
    labelMedium = TagsmithType.Chip,
    labelSmall = TagsmithType.Kicker,
)

@Composable
fun TagsmithTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    CompositionLocalProvider(LocalTagsmithPalette provides palette) {
        MaterialTheme(
            colorScheme = schemeFor(palette),
            typography = M3Typography,
            shapes = SquareShapes,
            content = content,
        )
    }
}

/**
 * Renders [content] on the dark tap ground regardless of the app theme.
 * Scan, the tap prompt and batch use this.
 */
@Composable
fun OnTapGround(content: @Composable () -> Unit) {
    // The tap ground is dark whatever the theme, so the status bar is too.
    com.tagsmith.ui.util.StatusBarIcons(lightIcons = true)
    CompositionLocalProvider(LocalTagsmithPalette provides TapPalette) {
        MaterialTheme(
            colorScheme = schemeFor(TapPalette),
            typography = M3Typography,
            shapes = SquareShapes,
            content = content,
        )
    }
}
