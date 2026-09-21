package com.example.phoneagent.data.llm

public data class BackendProfile(
    public val baseUrl: String,
    public val model: String,
) {
    public companion object {
        public val StagePlaceholder: BackendProfile =
            BackendProfile(
                baseUrl = "https://example.invalid",
                model = "stage-placeholder",
            )
    }
}
