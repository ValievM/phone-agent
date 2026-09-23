package com.example.phoneagent.domain.storage

import com.example.phoneagent.domain.conversation.HistoryItem

public interface ConversationRepository {
    public suspend fun load(): List<HistoryItem>

    public suspend fun replace(items: List<HistoryItem>)
}

public data class AppSettings(
    public val useDynamicColors: Boolean = true,
    public val useDarkTheme: Boolean? = null,
    public val model: String? = null,
)

public interface SettingsRepository {
    public suspend fun load(): AppSettings

    public suspend fun save(settings: AppSettings)
}
