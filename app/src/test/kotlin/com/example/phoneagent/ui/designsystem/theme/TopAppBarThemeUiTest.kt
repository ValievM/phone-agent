package com.example.phoneagent.ui.designsystem.theme

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.ui.designsystem.components.appTopAppBarColors
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class TopAppBarThemeUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun `app bar colors disable the M3 container animation and use current theme roles`() {
        var appBarColors: TopAppBarColors? = null
        var onSurface = Color.Unspecified
        var onSurfaceVariant = Color.Unspecified
        composeRule.setContent {
            AppTheme(useDynamicColor = false) {
                val scheme = MaterialTheme.colorScheme
                val colors = appTopAppBarColors()
                SideEffect {
                    appBarColors = colors
                    onSurface = scheme.onSurface
                    onSurfaceVariant = scheme.onSurfaceVariant
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals("M3 must not add a second container fade", Color.Unspecified, appBarColors?.containerColor)
        assertEquals(Color.Unspecified, appBarColors?.scrolledContainerColor)
        assertEquals(onSurface, appBarColors?.titleContentColor)
        assertEquals(onSurface, appBarColors?.navigationIconContentColor)
        assertEquals(onSurfaceVariant, appBarColors?.actionIconContentColor)
        assertEquals(onSurfaceVariant, appBarColors?.subtitleContentColor)
    }
}
