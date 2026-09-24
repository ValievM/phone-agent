package com.example.phoneagent.ui.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.phoneagent.R

// inter.ttf is a variable font: without an explicit `wght` value every entry below would render at
// the file's default weight, so Medium titles and **bold** text would look like Regular.
@OptIn(ExperimentalTextApi::class)
private fun inter(weight: FontWeight) =
    Font(R.font.inter, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

private val Inter =
    FontFamily(
        inter(FontWeight.Normal),
        inter(FontWeight.Medium),
        inter(FontWeight.Bold),
    )

val AppMonospace: FontFamily = FontFamily(Font(R.font.jetbrains_mono, FontWeight.Normal))

private val AppTypography =
    Typography(
        bodyLarge = TextStyle(fontFamily = Inter, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = Inter, fontSize = 12.sp, lineHeight = 16.sp),
        titleLarge = TextStyle(fontFamily = Inter, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
        titleMedium = TextStyle(fontFamily = Inter, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
        titleSmall = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        labelLarge = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        labelMedium = TextStyle(fontFamily = Inter, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    )

private val AppShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

@Immutable
data class AppSpacing(
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp,
)

@Immutable
data class AppExtendedColors(
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
)

private val LocalSpacing = staticCompositionLocalOf { AppSpacing() }
private val LocalExtendedColors = staticCompositionLocalOf { BrandLightExtendedColors }

object AppTheme {
    val spacing: AppSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalSpacing.current

    val extendedColors: AppExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalExtendedColors.current
}

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme =
        when {
            useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> dynamicDarkColorScheme(context)
            useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
            darkTheme -> BrandDarkColorScheme
            else -> BrandLightColorScheme
        }
    val extendedColors = if (darkTheme) BrandDarkExtendedColors else BrandLightExtendedColors

    androidx.compose.runtime.CompositionLocalProvider(
        LocalSpacing provides AppSpacing(),
        LocalExtendedColors provides extendedColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
