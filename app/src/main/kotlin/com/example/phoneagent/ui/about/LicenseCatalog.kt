package com.example.phoneagent.ui.about

/** A license whose complete notice is bundled in the app's raw resources. */
enum class LicenseKind {
    APACHE_2_0,
    OFL_1_1,
    BSD_3_CLAUSE,
}

data class LicenseEntry(
    val name: String,
    val holder: String,
    val license: LicenseKind,
    val source: String,
)

/** License notices grouped by project families; all release POMs were audited individually. */
object LicenseCatalog {
    val fonts =
        listOf(
            LicenseEntry("Inter", "The Inter Project Authors", LicenseKind.OFL_1_1, "https://github.com/rsms/inter"),
            LicenseEntry(
                "JetBrains Mono",
                "The JetBrains Mono Project Authors",
                LicenseKind.OFL_1_1,
                "https://github.com/JetBrains/JetBrainsMono",
            ),
        )

    val icons =
        listOf(
            LicenseEntry("Material Symbols Rounded", "Google", LicenseKind.APACHE_2_0, "https://fonts.google.com/icons"),
        )

    val libraries =
        listOf(
            LicenseEntry(
                "AndroidX",
                "The Android Open Source Project",
                LicenseKind.APACHE_2_0,
                "https://developer.android.com/jetpack/androidx",
            ),
            LicenseEntry(
                "Jetpack Compose",
                "The Android Open Source Project",
                LicenseKind.APACHE_2_0,
                "https://developer.android.com/jetpack/compose",
            ),
            LicenseEntry("Dagger и Hilt", "Google, Inc.", LicenseKind.APACHE_2_0, "https://github.com/google/dagger"),
            LicenseEntry(
                "API внедрения зависимостей (JSR-330 и Jakarta Inject)",
                "Eclipse Foundation; правообладатель javax.inject не указан в POM",
                LicenseKind.APACHE_2_0,
                "http://code.google.com/p/atinject/; https://github.com/eclipse-ee4j/injection-api",
            ),
            LicenseEntry(
                "Google Guava ListenableFuture",
                "Google, Inc.",
                LicenseKind.APACHE_2_0,
                "https://github.com/google/guava",
            ),
            LicenseEntry(
                "OkHttp и Okio",
                "Square, Inc.",
                LicenseKind.APACHE_2_0,
                "https://lysine.dev/okhttp/; https://github.com/lysine-dev/okio/",
            ),
            LicenseEntry(
                "Kotlin: стандартная библиотека, корутины, сериализация и неизменяемые коллекции",
                "Kotlin Team; JetBrains Team",
                LicenseKind.APACHE_2_0,
                "https://kotlinlang.org/; https://github.com/Kotlin/kotlinx.coroutines; " +
                    "https://github.com/Kotlin/kotlinx.serialization; https://github.com/Kotlin/kotlinx.collections.immutable",
            ),
            LicenseEntry(
                "Markdown renderer и подсветка кода",
                "Mike Penz; Valentin Fondaratov; Tomasz Kądziołka",
                LicenseKind.APACHE_2_0,
                "https://github.com/mikepenz/multiplatform-markdown-renderer; " +
                    "https://github.com/JetBrains/markdown; https://github.com/SnipMeDev/Highlights",
            ),
            LicenseEntry(
                "Java-аннотации (JSR-305, JSpecify и JetBrains)",
                "Правообладатель не указан в POM",
                LicenseKind.APACHE_2_0,
                "http://findbugs.sourceforge.net/; http://jspecify.org/; https://github.com/JetBrains/java-annotations",
            ),
            LicenseEntry(
                "Protocol Buffers (внутри DataStore)",
                "Google Inc.",
                LicenseKind.BSD_3_CLAUSE,
                "https://github.com/protocolbuffers/protobuf",
            ),
        )

    val entries: List<LicenseEntry> = fonts + icons + libraries
}
