package com.example.phoneagent.testing

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
public fun ProvideFakeActivityResultRegistry(
    registry: ActivityResultRegistry,
    content: @Composable () -> Unit,
) {
    val owner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry = registry
    }
    CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner, content = content)
}
