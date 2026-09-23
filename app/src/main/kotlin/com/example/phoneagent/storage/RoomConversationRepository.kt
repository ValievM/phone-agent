package com.example.phoneagent.storage

import com.example.phoneagent.domain.conversation.HistoryItem
import com.example.phoneagent.domain.storage.ConversationRepository
import javax.inject.Inject

internal class RoomConversationRepository
    @Inject
    constructor(
        private val dao: ConversationDao,
    ) : ConversationRepository {
        override suspend fun load(): List<HistoryItem> = dao.loadAll().map(HistoryItemEntityMapper::toDomain)

        override suspend fun replace(items: List<HistoryItem>) {
            dao.replaceAll(items.mapIndexed { position, item -> HistoryItemEntityMapper.toEntity(item, position) })
        }
    }
