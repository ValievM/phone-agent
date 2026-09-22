package com.example.phoneagent.data.llm

public data class BackendProfile(
    public val baseUrl: String,
    public val model: String,
    // Placeholder until stage 4.8 writes the real system prompt (tool-use and untrusted-data
    // rules); only needs to be non-empty for now, see docs/stages/3-model-client.md.
    public val instructions: String = "You are a helpful assistant running on the user's Android phone. Answer concisely.",
) {
    public companion object {
        public val ChatGptSubscription: BackendProfile =
            BackendProfile(
                baseUrl = "https://chatgpt.com/backend-api/codex",
                model = "gpt-5.6-sol",
            )
    }
}
