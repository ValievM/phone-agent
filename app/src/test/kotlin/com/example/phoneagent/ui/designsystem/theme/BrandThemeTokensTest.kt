package com.example.phoneagent.ui.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.pow

/** The brand tokens live in docs/design/brand/joint-tokens.json; the Kotlin schemes must never drift from them. */
class BrandThemeTokensTest {
    private val tokens: JsonObject by lazy {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }.first { File(it, "settings.gradle.kts").isFile }
        Json.parseToJsonElement(File(root, "docs/design/brand/joint-tokens.json").readText()).jsonObject
    }

    @Test
    fun `the light scheme has exactly the brand token values`() = assertSchemeMatches("light", BrandLightColorScheme)

    @Test
    fun `the dark scheme has exactly the brand token values`() = assertSchemeMatches("dark", BrandDarkColorScheme)

    @Test
    fun `the extended success colors have the brand token values`() {
        for ((mode, extended) in listOf("light" to BrandLightExtendedColors, "dark" to BrandDarkExtendedColors)) {
            val expected = tokens.getValue(mode).jsonObject
            assertTrue("$mode success", extended.success == color(expected, "success"))
            assertTrue("$mode successContainer", extended.successContainer == color(expected, "successContainer"))
            assertTrue("$mode onSuccessContainer", extended.onSuccessContainer == color(expected, "onSuccessContainer"))
        }
    }

    @Test
    fun `text and control pairs keep their contrast in both themes`() {
        val failures = mutableListOf<String>()
        for ((mode, scheme) in listOf("light" to BrandLightColorScheme, "dark" to BrandDarkColorScheme)) {
            val pairs =
                listOf(
                    Triple("onPrimary", "primary", 4.5),
                    Triple("onPrimaryContainer", "primaryContainer", 4.5),
                    Triple("onSecondaryContainer", "secondaryContainer", 4.5),
                    Triple("onTertiaryContainer", "tertiaryContainer", 4.5),
                    Triple("onError", "error", 4.5),
                    Triple("onErrorContainer", "errorContainer", 4.5),
                    Triple("onSurface", "surface", 4.5),
                    Triple("onSurfaceVariant", "surface", 4.5),
                    Triple("onBackground", "background", 4.5),
                    Triple("inverseOnSurface", "inverseSurface", 4.5),
                    Triple("primary", "surface", 4.5),
                    Triple("outline", "surface", 3.0),
                )
            for ((foreground, background, minimum) in pairs) {
                val ratio = contrast(scheme.role(foreground), scheme.role(background))
                if (ratio < minimum) failures += "$mode $foreground on $background = $ratio (needs $minimum)"
            }
        }
        assertTrue("Contrast below the minimum: $failures", failures.isEmpty())
    }

    private fun assertSchemeMatches(
        mode: String,
        scheme: ColorScheme,
    ) {
        val expected = tokens.getValue(mode).jsonObject
        val mismatches =
            ROLES
                .filter { role -> scheme.role(role) != color(expected, role) }
                .map { role -> "$role: expected ${expected.getValue(role).jsonPrimitive.content}" }
        assertTrue("$mode scheme differs from the brand tokens: $mismatches", mismatches.isEmpty())
    }

    private fun color(
        json: JsonObject,
        role: String,
    ): Color =
        Color(
            json
                .getValue(role)
                .jsonPrimitive.content
                .removePrefix("#")
                .toLong(16) or 0xFF000000L,
        )

    private fun ColorScheme.role(name: String): Color = roles().getValue(name)

    private fun ColorScheme.roles(): Map<String, Color> =
        mapOf(
            "primary" to primary,
            "onPrimary" to onPrimary,
            "primaryContainer" to primaryContainer,
            "onPrimaryContainer" to onPrimaryContainer,
            "inversePrimary" to inversePrimary,
            "secondary" to secondary,
            "onSecondary" to onSecondary,
            "secondaryContainer" to secondaryContainer,
            "onSecondaryContainer" to onSecondaryContainer,
            "tertiary" to tertiary,
            "onTertiary" to onTertiary,
            "tertiaryContainer" to tertiaryContainer,
            "onTertiaryContainer" to onTertiaryContainer,
            "background" to background,
            "onBackground" to onBackground,
            "surface" to surface,
            "onSurface" to onSurface,
            "surfaceVariant" to surfaceVariant,
            "onSurfaceVariant" to onSurfaceVariant,
            "surfaceTint" to surfaceTint,
            "inverseSurface" to inverseSurface,
            "inverseOnSurface" to inverseOnSurface,
            "error" to error,
            "onError" to onError,
            "errorContainer" to errorContainer,
            "onErrorContainer" to onErrorContainer,
            "outline" to outline,
            "outlineVariant" to outlineVariant,
            "scrim" to scrim,
            "surfaceBright" to surfaceBright,
            "surfaceDim" to surfaceDim,
            "surfaceContainer" to surfaceContainer,
            "surfaceContainerHigh" to surfaceContainerHigh,
            "surfaceContainerHighest" to surfaceContainerHighest,
            "surfaceContainerLow" to surfaceContainerLow,
            "surfaceContainerLowest" to surfaceContainerLowest,
            "primaryFixed" to primaryFixed,
            "primaryFixedDim" to primaryFixedDim,
            "onPrimaryFixed" to onPrimaryFixed,
            "onPrimaryFixedVariant" to onPrimaryFixedVariant,
            "secondaryFixed" to secondaryFixed,
            "secondaryFixedDim" to secondaryFixedDim,
            "onSecondaryFixed" to onSecondaryFixed,
            "onSecondaryFixedVariant" to onSecondaryFixedVariant,
            "tertiaryFixed" to tertiaryFixed,
            "tertiaryFixedDim" to tertiaryFixedDim,
            "onTertiaryFixed" to onTertiaryFixed,
            "onTertiaryFixedVariant" to onTertiaryFixedVariant,
        )

    private fun contrast(
        a: Color,
        b: Color,
    ): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminance(color: Color): Double {
        val argb = color.toArgb()

        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private companion object {
        val ROLES =
            listOf(
                "primary",
                "onPrimary",
                "primaryContainer",
                "onPrimaryContainer",
                "inversePrimary",
                "secondary",
                "onSecondary",
                "secondaryContainer",
                "onSecondaryContainer",
                "tertiary",
                "onTertiary",
                "tertiaryContainer",
                "onTertiaryContainer",
                "background",
                "onBackground",
                "surface",
                "onSurface",
                "surfaceVariant",
                "onSurfaceVariant",
                "surfaceTint",
                "inverseSurface",
                "inverseOnSurface",
                "error",
                "onError",
                "errorContainer",
                "onErrorContainer",
                "outline",
                "outlineVariant",
                "scrim",
                "surfaceBright",
                "surfaceDim",
                "surfaceContainer",
                "surfaceContainerHigh",
                "surfaceContainerHighest",
                "surfaceContainerLow",
                "surfaceContainerLowest",
                "primaryFixed",
                "primaryFixedDim",
                "onPrimaryFixed",
                "onPrimaryFixedVariant",
                "secondaryFixed",
                "secondaryFixedDim",
                "onSecondaryFixed",
                "onSecondaryFixedVariant",
                "tertiaryFixed",
                "tertiaryFixedDim",
                "onTertiaryFixed",
                "onTertiaryFixedVariant",
            )
    }
}
