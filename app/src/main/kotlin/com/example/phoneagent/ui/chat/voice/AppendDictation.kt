package com.example.phoneagent.ui.chat.voice

/** Adds recognized text to a draft without creating duplicate separators. */
fun appendDictation(
    draft: String,
    text: String,
): String {
    val appended = text.trim()
    val existingDraft = draft.takeIf(String::isNotBlank).orEmpty()
    val separator =
        if (existingDraft.isEmpty() || existingDraft.last() == ' ' || existingDraft.last() == '\n') {
            ""
        } else {
            " "
        }
    return if (appended.isEmpty()) draft else existingDraft + separator + appended
}
