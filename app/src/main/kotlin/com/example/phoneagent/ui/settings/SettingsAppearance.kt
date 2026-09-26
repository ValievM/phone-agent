package com.example.phoneagent.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.phoneagent.R
import com.example.phoneagent.ui.designsystem.components.StatusMessage
import com.example.phoneagent.ui.designsystem.theme.AppTheme

/**
 * Theme choice (system / light / dark) and the separate "system colors" switch (Android 12+). Both are
 * off until the saved settings have been read, and stay off if reading them failed, so a tap can never
 * overwrite the saved model or folder. A failed save keeps the previous choice and says so.
 */
@Composable
internal fun AppearanceSection(
    state: SettingsUiState,
    onEvent: (SettingsUiEvent) -> Unit,
) {
    SettingsSection(title = stringResource(R.string.settings_appearance_title)) {
        val modes = ThemeMode.entries
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val useList = maxWidth <= 320.dp || LocalDensity.current.fontScale >= 1.5f
            if (useList) {
                Column {
                    modes.forEach { mode ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .selectable(
                                        selected = state.themeMode == mode,
                                        enabled = state.canEditSettings,
                                        role = Role.RadioButton,
                                        onClick = { onEvent(SettingsUiEvent.SelectThemeMode(mode)) },
                                    ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = state.themeMode == mode,
                                onClick = null,
                                enabled = state.canEditSettings,
                            )
                            Text(
                                text = stringResource(mode.label()),
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            } else {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { index, mode ->
                        SegmentedButton(
                            modifier = Modifier.weight(1f),
                            selected = state.themeMode == mode,
                            onClick = { onEvent(SettingsUiEvent.SelectThemeMode(mode)) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                            enabled = state.canEditSettings,
                        ) {
                            Text(stringResource(mode.label()), maxLines = 1)
                        }
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SystemColorsRow(state = state, onEvent = onEvent)
        }
        if (state.themeSaveFailed) {
            StatusMessage(stringResource(R.string.settings_theme_save_failed))
        }
    }
}

/** The whole row is the switch's touch target, and TalkBack reads its title and explanation as one control. */
@Composable
private fun SystemColorsRow(
    state: SettingsUiState,
    onEvent: (SettingsUiEvent) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .toggleable(
                    value = state.useSystemColors,
                    enabled = state.canEditSettings,
                    role = Role.Switch,
                    onValueChange = { onEvent(SettingsUiEvent.SetSystemColors(it)) },
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_system_colors_title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.settings_system_colors_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = state.useSystemColors, onCheckedChange = null, enabled = state.canEditSettings)
    }
}

private fun ThemeMode.label(): Int =
    when (this) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    }
