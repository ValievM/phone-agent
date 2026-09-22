package com.example.phoneagent.ui.designsystem.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.example.phoneagent.R
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@PreviewLightDark
@PreviewFontScale
@Composable
private fun ToolCallCardSearchResultsPreview() {
    AppTheme(useDynamicColor = false) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ToolCallCard(
                state = ToolCallStatus.FilesFound(1),
                description =
                    CallDescription(
                        title = stringResource(R.string.preview_search_files_title),
                        summary = stringResource(R.string.preview_search_files_summary),
                        details = emptyList(),
                    ),
                arguments = buildJsonObject { put("query", "отчёт") },
                expanded = true,
                onExpandToggle = {},
                fileHits =
                    listOf(
                        FileHit(
                            name = stringResource(R.string.preview_file_name),
                            uri = "content://media/external/downloads/42",
                            size = 1_258_291L,
                            mime = "application/pdf",
                        ),
                    ),
                modifier = Modifier.padding(AppTheme.spacing.large),
            )
        }
    }
}
