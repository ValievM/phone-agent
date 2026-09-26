package com.example.phoneagent.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = title,
        modifier = modifier.background(MaterialTheme.colorScheme.surface),
        navigationIcon = navigationIcon,
        actions = actions,
        colors = appTopAppBarColors(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun appTopAppBarColors(): TopAppBarColors {
    val colorScheme = MaterialTheme.colorScheme
    return TopAppBarColors(
        containerColor = Color.Unspecified,
        scrolledContainerColor = Color.Unspecified,
        navigationIconContentColor = colorScheme.onSurface,
        titleContentColor = colorScheme.onSurface,
        actionIconContentColor = colorScheme.onSurfaceVariant,
        subtitleContentColor = colorScheme.onSurfaceVariant,
    )
}
