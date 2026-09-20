package com.tagsmith.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.tagsmith.R

/**
 * Archivo throughout, JetBrains Mono wherever the screen shows data —
 * UIDs, byte counts, chip types — so hex reads as hex.
 *
 * Both faces ship as variable fonts; the weight axis is driven from [FontWeight].
 */
val Archivo = FontFamily(
    Font(R.font.archivo, FontWeight.Normal),
    Font(R.font.archivo, FontWeight.Medium),
    Font(R.font.archivo, FontWeight.SemiBold),
    Font(R.font.archivo, FontWeight.Bold),
    Font(R.font.archivo, FontWeight.ExtraBold),
)

val Mono = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono, FontWeight.Bold),
)

private val Trim = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun archivo(
    size: Int,
    weight: FontWeight,
    lineHeight: Float = 1.3f,
    tracking: Float = 0f,
) = TextStyle(
    fontFamily = Archivo,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * lineHeight).sp,
    letterSpacing = tracking.sp,
    lineHeightStyle = Trim,
)

private fun mono(
    size: Int,
    weight: FontWeight = FontWeight.Normal,
    lineHeight: Float = 1.3f,
    tracking: Float = 0f,
) = TextStyle(
    fontFamily = Mono,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * lineHeight).sp,
    letterSpacing = tracking.sp,
    lineHeightStyle = Trim,
)

/** The board's type scale, named for where it is used rather than for a size. */
object TagsmithType {
    /** The tap prompt — legible at arm's length. */
    val Hero = archivo(30, FontWeight.ExtraBold, 1.15f, -0.6f)
    val HeroSmall = archivo(26, FontWeight.ExtraBold, 1.15f, -0.5f)

    /** Screen titles. */
    val ScreenTitle = archivo(28, FontWeight.ExtraBold, 1.1f, -0.56f)
    val SheetTitle = archivo(24, FontWeight.ExtraBold, 1.15f, -0.48f)
    val Greeting = archivo(26, FontWeight.ExtraBold, 1.15f, -0.52f)

    val Body = archivo(16, FontWeight.Normal, 1.5f)
    val BodySmall = archivo(14, FontWeight.Normal, 1.45f)
    val BodyTiny = archivo(13, FontWeight.Normal, 1.45f)

    val RowTitle = archivo(15, FontWeight.SemiBold, 1.3f)
    val RowTitleSmall = archivo(14, FontWeight.SemiBold, 1.3f)
    val RowMeta = archivo(12, FontWeight.Normal, 1.3f)

    val Button = archivo(16, FontWeight.SemiBold, 1.2f)
    val ButtonLoud = archivo(17, FontWeight.Bold, 1.2f)
    val ButtonSmall = archivo(15, FontWeight.SemiBold, 1.2f)

    val Chip = archivo(13, FontWeight.Medium, 1.2f)
    val ChipSelected = archivo(13, FontWeight.SemiBold, 1.2f)
    val ChipSmall = archivo(12, FontWeight.Medium, 1.2f)
    val StatCaption = archivo(11, FontWeight.Normal, 1.3f)
    val NavLabel = archivo(11, FontWeight.Medium, 1.2f)
    val NavLabelActive = archivo(11, FontWeight.SemiBold, 1.2f)

    /** Flush-left section labels — mono, caps, wide tracking. */
    val Kicker = mono(10, FontWeight.Bold, 1.1f, 1.2f)
    val KickerLoud = mono(11, FontWeight.Bold, 1.1f, 1.3f)

    /** Data. */
    val Data = mono(13, FontWeight.Normal, 1.4f)
    val DataSmall = mono(12, FontWeight.Normal, 1.35f)
    val DataTiny = mono(11, FontWeight.Normal, 1.3f)
    val Stat = mono(24, FontWeight.Bold, 1.1f)
    val StatSmall = mono(26, FontWeight.Bold, 1.1f)
    val Counter = mono(76, FontWeight.ExtraBold, 0.85f, -3f)
    val StatusChip = mono(10, FontWeight.Bold, 1.1f, 0.8f)
    val StatusChipSmall = mono(9, FontWeight.Bold, 1.1f, 0.7f)
}
