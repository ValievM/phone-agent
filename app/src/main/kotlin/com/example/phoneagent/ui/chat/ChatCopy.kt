package com.example.phoneagent.ui.chat

/**
 * User-facing text ChatViewModel produces itself (not from strings.xml: a ViewModel must not hold
 * a Context, see architecture/testing.md rule 4). Kept in one file so a future localization pass
 * has one place to change, not a hunt through the ViewModel's logic.
 */
internal object ChatCopy {
    fun toolRejected(reason: String): String = "Отклонено: $reason"

    fun toolFailed(reason: String): String = "Ошибка: $reason"
}
