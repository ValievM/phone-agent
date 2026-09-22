package com.example.phoneagent.domain.platform

/**
 * Best-effort scrubbing of tokens, keys and other transport secrets out of free-form text before
 * it reaches [AppLogger]. This is defense in depth, not a substitute for discipline at the call
 * site: HTTP bodies, chat messages and file names must never be logged in the first place, no
 * matter what this redacts — a secret embedded in an unusual shape can still slip through a regex.
 */
public object LogRedaction {
    private val BEARER = Regex("""(?i)Bearer\s+\S+""")
    private val JWT = Regex("""eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""")

    // Key names whose value is always a secret, whether seen inline in text or as a structured
    // log field name. Deliberately broad (e.g. plain "token", "code") over narrow.
    private val SENSITIVE_KEY_NAMES =
        setOf(
            "access_token",
            "refresh_token",
            "id_token",
            "token",
            "api_key",
            "apikey",
            "client_secret",
            "secret",
            "code_verifier",
            "authorization_code",
            "code",
            "password",
        )

    // Matches `"key":"value"`, `key=value` and `key: value` for the names above, keeping the key
    // and redacting only the value.
    private val SENSITIVE_KEY_VALUE =
        Regex("""(?i)("?(?:${SENSITIVE_KEY_NAMES.joinToString("|")})"?)\s*[:=]\s*"?[^\s"&,}]+"?""")

    public fun redact(text: String): String =
        text
            .replace(BEARER, "Bearer <redacted>")
            .replace(JWT, "<jwt>")
            .replace(SENSITIVE_KEY_VALUE) { match -> "${match.groupValues[1]}${match.value[match.groupValues[1].length]}<redacted>" }

    public fun isSensitiveFieldName(name: String): Boolean = name.lowercase() in SENSITIVE_KEY_NAMES
}
