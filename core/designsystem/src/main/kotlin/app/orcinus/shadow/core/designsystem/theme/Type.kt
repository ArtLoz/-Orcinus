package app.orcinus.shadow.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import app.orcinus.shadow.core.designsystem.R

/**
 * The UI font: Inter, cut down to Latin and Cyrillic by scripts/subset-fonts.ps1.
 * It stands in for OrcaSlicer's HarmonyOS Sans SC, whose license does not allow
 * a subset or a free software release.
 */
val OrcaSans = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private fun head(size: TextUnit) = TextStyle(fontFamily = OrcaSans, fontWeight = FontWeight.Bold, fontSize = size)
private fun body(size: TextUnit) = TextStyle(fontFamily = OrcaSans, fontWeight = FontWeight.Normal, fontSize = size)

/**
 * OrcaSlicer's text styles (Widgets/Label.cpp): Head is bold, Body is regular,
 * the number is the size. Orca's point sizes map to sp one to one.
 */
@Immutable
data class OrcaTypography(
    val head24: TextStyle = head(24.sp),
    val head20: TextStyle = head(20.sp),
    val head18: TextStyle = head(18.sp),
    val head16: TextStyle = head(16.sp),
    val head15: TextStyle = head(15.sp),
    val head14: TextStyle = head(14.sp),
    val head13: TextStyle = head(13.sp),
    val head12: TextStyle = head(12.sp),
    val head10: TextStyle = head(10.sp),
    val body16: TextStyle = body(16.sp),
    val body15: TextStyle = body(15.sp),
    val body14: TextStyle = body(14.sp),
    val body13: TextStyle = body(13.sp),
    val body12: TextStyle = body(12.sp),
    val body11: TextStyle = body(11.sp),
    val body10: TextStyle = body(10.sp),
    val body9: TextStyle = body(9.sp),
)

internal val DefaultOrcaTypography = OrcaTypography()

/** Material roles filled with Orca's styles, for components that read MaterialTheme. */
internal val OrcaMaterialTypography = with(DefaultOrcaTypography) {
    Typography(
        headlineLarge = head24,
        headlineMedium = head20,
        headlineSmall = head18,
        titleLarge = head18,
        titleMedium = head16,
        titleSmall = head14,
        bodyLarge = body16,
        bodyMedium = body14,
        bodySmall = body12,
        labelLarge = body14,
        labelMedium = body12,
        labelSmall = body10,
    )
}
