package com.example.phoneagent.data.auth

public data class AuthConfig(
    public val authorizationEndpoint: String,
    public val clientId: String,
) {
    public companion object {
        public val StagePlaceholder: AuthConfig =
            AuthConfig(
                authorizationEndpoint = "https://example.invalid/authorize",
                clientId = "stage-placeholder",
            )
    }
}
