package com.example.phoneagent

import com.example.phoneagent.auth.AppAuthStatus
import com.example.phoneagent.data.auth.AuthMethod
import com.example.phoneagent.domain.storage.AppSettings
import com.example.phoneagent.ui.root.ThemeState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure decision behind [MainActivity]'s splash [android.window.SplashScreen] hold condition. */
class SplashVisibilityTest {
    @Test
    fun `splash stays while the theme is still loading`() {
        assertTrue(
            shouldKeepSplashVisible(
                themeState = ThemeState.Loading,
                authStatus = AppAuthStatus.SignedOut(sessionExpired = false),
                nowMillis = 0,
                deadlineMillis = 1_000,
            ),
        )
    }

    @Test
    fun `splash stays while the session is still restoring, avoiding a second identical screen`() {
        assertTrue(
            shouldKeepSplashVisible(
                themeState = ThemeState.Ready(settings = AppSettings()),
                authStatus = AppAuthStatus.Restoring,
                nowMillis = 0,
                deadlineMillis = 1_000,
            ),
        )
    }

    @Test
    fun `splash is dismissed once the theme is known and the session is signed in`() {
        assertFalse(
            shouldKeepSplashVisible(
                themeState = ThemeState.Ready(settings = AppSettings()),
                authStatus = AppAuthStatus.SignedIn(AuthMethod.API_KEY),
                nowMillis = 0,
                deadlineMillis = 1_000,
            ),
        )
    }

    @Test
    fun `splash is dismissed once the theme is known and the session is signed out`() {
        assertFalse(
            shouldKeepSplashVisible(
                themeState = ThemeState.Ready(settings = AppSettings()),
                authStatus = AppAuthStatus.SignedOut(sessionExpired = false),
                nowMillis = 0,
                deadlineMillis = 1_000,
            ),
        )
    }

    @Test
    fun `a slow restore does not hold the splash forever, past its deadline`() {
        assertFalse(
            shouldKeepSplashVisible(
                themeState = ThemeState.Ready(settings = AppSettings()),
                authStatus = AppAuthStatus.Restoring,
                nowMillis = 1_500,
                deadlineMillis = 1_000,
            ),
        )
    }
}
