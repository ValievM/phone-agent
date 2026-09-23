package com.example.phoneagent.domain.storage

import com.example.phoneagent.domain.conversation.HistoryItem
import kotlinx.coroutines.flow.Flow

public interface ConversationRepository {
    public suspend fun load(): List<HistoryItem>

    public suspend fun replace(items: List<HistoryItem>)

    public fun observe(): Flow<List<HistoryItem>>
}

public data class AppSettings(
    public val useDynamicColors: Boolean = true,
    public val useDarkTheme: Boolean? = null,
    public val model: String? = null,
    public val searchFolderUri: String? = null,
)

public interface SettingsRepository {
    public suspend fun load(): AppSettings

    public suspend fun save(settings: AppSettings)
}
