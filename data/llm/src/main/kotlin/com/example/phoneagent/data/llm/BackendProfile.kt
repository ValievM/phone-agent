package com.example.phoneagent.data.llm

public data class BackendProfile(
    public val baseUrl: String,
    public val instructions: String = SYSTEM_PROMPT,
) {
    public companion object {
        public const val DEFAULT_MODEL: String = "gpt-5.6-luna"

        public val ChatGptSubscription: BackendProfile =
            BackendProfile(
                baseUrl = "https://chatgpt.com/backend-api/codex",
            )
    }
}
