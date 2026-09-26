package com.example.phoneagent.ui

import android.content.pm.PackageManager

@Suppress("DEPRECATION")
internal fun readAppVersionName(
    packageManager: PackageManager,
    packageName: String,
    unavailableLabel: String,
): String {
    val versionName =
        try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    return versionName?.takeIf { it.isNotBlank() } ?: unavailableLabel
}
