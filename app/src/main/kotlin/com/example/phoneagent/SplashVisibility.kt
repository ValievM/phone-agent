package com.example.phoneagent

import com.example.phoneagent.auth.AppAuthStatus
import com.example.phoneagent.ui.root.ThemeState

/**
 * Whether the OS splash screen should keep covering the window. It stays up while the theme or
 * the session is still loading, so the app never falls through to a second, redundant "restoring"
 * screen with the same brand mark - but never past [deadlineMillis], so a slow disk/network read
 * cannot leave the user stuck on the splash. Compose still renders its own restoring screen for the
 * cases past the deadline, so the loading state is never left without any feedback.
 */
internal fun shouldKeepSplashVisible(
    themeState: ThemeState,
    authStatus: AppAuthStatus,
    nowMillis: Long,
    deadlineMillis: Long,
): Boolean {
    if (nowMillis >= deadlineMillis) return false
    return themeState is ThemeState.Loading || authStatus is AppAuthStatus.Restoring
}
