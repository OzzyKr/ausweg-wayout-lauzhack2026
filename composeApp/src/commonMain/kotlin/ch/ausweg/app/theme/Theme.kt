package ch.ausweg.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.ausweg.app.resources.Res
import ch.ausweg.app.resources.inter_bold
import ch.ausweg.app.resources.inter_medium
import ch.ausweg.app.resources.inter_regular
import ch.ausweg.app.resources.inter_semibold
import org.jetbrains.compose.resources.Font

/** Colors Material 3 has no slot for: leg chips, warnings, on-time, the replay bar. */
@Immutable
data class AuswegColors(
    /** The brand red, also used for the header and primary buttons in dark mode. */
    val brand: Color,
    val chip: Color,
    val onChip: Color,
    val warning: Color,
    val onWarning: Color,
    val good: Color,
    val onGood: Color,
    val banner: Color,
    val onBanner: Color,
    val bannerAccent: Color,
)

private val Red = Color(0xFFEB0000)

private val LightExtra =
    AuswegColors(
        brand = Red,
        chip = Color(0xFF2D2D2D),
        onChip = Color.White,
        warning = Color(0xFFFDECEC),
        onWarning = Color(0xFFB00000),
        good = Color(0xFFE3F4E8),
        onGood = Color(0xFF1B6B36),
        banner = Color(0xFF232323),
        onBanner = Color.White,
        bannerAccent = Color(0xFFFF8A8A),
    )

private val DarkExtra =
    AuswegColors(
        brand = Red,
        chip = Color(0xFF3D3D3D),
        onChip = Color.White,
        warning = Color(0xFF3A1717),
        onWarning = Color(0xFFFF9C9C),
        good = Color(0xFF173A24),
        onGood = Color(0xFF8FDCA8),
        banner = Color(0xFF2E2E2E),
        onBanner = Color.White,
        bannerAccent = Color(0xFFFF8A8A),
    )

private val LightScheme =
    lightColorScheme(
        primary = Red,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFDECEC),
        onPrimaryContainer = Color(0xFFB00000),
        secondary = Color(0xFF2D2D2D),
        onSecondary = Color.White,
        background = Color(0xFFF4F4F4),
        onBackground = Color(0xFF1A1A1A),
        surface = Color.White,
        onSurface = Color(0xFF1A1A1A),
        surfaceVariant = Color(0xFFEDEDED),
        onSurfaceVariant = Color(0xFF5E5E5E),
        surfaceContainer = Color.White,
        surfaceContainerHigh = Color.White,
        surfaceContainerLow = Color.White,
        outline = Color(0xFFD0D0D0),
        outlineVariant = Color(0xFFE6E6E6),
        error = Color(0xFFB00000),
    )

private val DarkScheme =
    darkColorScheme(
        primary = Color(0xFFFF5A5A),
        onPrimary = Color(0xFF2A0000),
        primaryContainer = Color(0xFF3A1717),
        onPrimaryContainer = Color(0xFFFF9C9C),
        secondary = Color(0xFFE0E0E0),
        onSecondary = Color(0xFF1A1A1A),
        background = Color(0xFF121212),
        onBackground = Color(0xFFEDEDED),
        surface = Color(0xFF1E1E1E),
        onSurface = Color(0xFFEDEDED),
        surfaceVariant = Color(0xFF2A2A2A),
        onSurfaceVariant = Color(0xFFABABAB),
        surfaceContainer = Color(0xFF1E1E1E),
        surfaceContainerHigh = Color(0xFF252525),
        surfaceContainerLow = Color(0xFF1A1A1A),
        outline = Color(0xFF4A4A4A),
        outlineVariant = Color(0xFF333333),
        error = Color(0xFFFF8A8A),
    )

val LocalAuswegColors = staticCompositionLocalOf { LightExtra }

/** Shortcut for the extra colors: `Extra.colors.chip`. */
object Extra {
    val colors: AuswegColors
        @Composable get() = LocalAuswegColors.current
}

@Composable
private fun interFamily(): FontFamily =
    FontFamily(
        Font(Res.font.inter_regular, FontWeight.Normal),
        Font(Res.font.inter_medium, FontWeight.Medium),
        Font(Res.font.inter_semibold, FontWeight.SemiBold),
        Font(Res.font.inter_bold, FontWeight.Bold),
    )

@Composable
private fun typography(): Typography {
    val inter = interFamily()

    fun style(
        size: Int,
        weight: FontWeight,
        line: Int,
    ) = TextStyle(fontFamily = inter, fontSize = size.sp, fontWeight = weight, lineHeight = line.sp)
    return Typography(
        headlineMedium = style(26, FontWeight.Bold, 32),
        headlineSmall = style(22, FontWeight.Bold, 28),
        titleLarge = style(20, FontWeight.SemiBold, 26),
        titleMedium = style(17, FontWeight.SemiBold, 22),
        titleSmall = style(15, FontWeight.SemiBold, 20),
        bodyLarge = style(16, FontWeight.Normal, 22),
        bodyMedium = style(15, FontWeight.Normal, 21),
        bodySmall = style(13, FontWeight.Normal, 18),
        labelLarge = style(16, FontWeight.SemiBold, 20),
        labelMedium = style(13, FontWeight.SemiBold, 16),
        labelSmall = style(12, FontWeight.SemiBold, 16),
    )
}

/** Material 3 in SBB-like red / white / charcoal, light and dark following the system, with Inter. */
@Composable
fun AuswegTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAuswegColors provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = typography(),
            shapes =
                Shapes(
                    small =
                        androidx.compose.foundation.shape
                            .RoundedCornerShape(8.dp),
                    medium =
                        androidx.compose.foundation.shape
                            .RoundedCornerShape(12.dp),
                    large =
                        androidx.compose.foundation.shape
                            .RoundedCornerShape(16.dp),
                ),
            content = content,
        )
    }
}
