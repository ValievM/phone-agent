package com.example.phoneagent.data.llm

public data class BackendProfile(
    public val baseUrl: String,
    public val model: String,
) {
    public companion object {
        public val ChatGptSubscription: BackendProfile =
            BackendProfile(
                baseUrl = "https://chatgpt.com/backend-api/codex",
                model = "gpt-5.6-sol",
            )
    }
}
