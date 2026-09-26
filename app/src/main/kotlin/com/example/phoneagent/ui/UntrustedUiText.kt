package com.example.phoneagent.ui

internal const val MAX_UNTRUSTED_UI_TEXT_CHARACTERS = 40

internal fun sanitizeUntrustedUiText(value: String): String {
    val sanitized = StringBuilder(minOf(value.length, MAX_UNTRUSTED_UI_TEXT_CHARACTERS * 2))
    var offset = 0
    var visibleCharacters = 0

    while (offset < value.length && visibleCharacters < MAX_UNTRUSTED_UI_TEXT_CHARACTERS) {
        val codePoint = value.codePointAt(offset)
        offset += Character.charCount(codePoint)
        if (!Character.isISOControl(codePoint)) {
            sanitized.appendCodePoint(codePoint)
            visibleCharacters++
        }
    }
    return sanitized.toString()
}
