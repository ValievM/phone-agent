package com.example.phoneagent.ui.about

/** A license whose complete notice is bundled in the app's raw resources. */
enum class LicenseKind {
    APACHE_2_0,
    OFL_1_1,
    BSD_3_CLAUSE,
    MIT,
    BOOST_1_0,
}

data class LicenseNotice(
    val holder: String,
    val license: LicenseKind,
)

data class LicenseEntry(
    val name: String,
    val holder: String,
    val license: LicenseKind,
    val source: String,
    val additionalNotices: List<LicenseNotice> = emptyList(),
)

/** Release licenses are grouped by project family; nested licenses remain separate notices. */
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
                "Eclipse Foundation; The JSR-330 Expert Group",
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
                "https://kotlinlang.org/; https://github.com/JetBrains/kotlin; " +
                    "https://github.com/Kotlin/kotlinx.coroutines; https://github.com/Kotlin/kotlinx.serialization; " +
                    "https://github.com/Kotlin/kotlinx.collections.immutable",
                additionalNotices =
                    listOf(
                        LicenseNotice("Eric Ford and Hubert Holin", LicenseKind.BOOST_1_0),
                        LicenseNotice(
                            "Stephen Colebourne; Michael Nascimento Santos",
                            LicenseKind.BSD_3_CLAUSE,
                        ),
                    ),
            ),
            LicenseEntry(
                "Markdown renderer и подсветка кода",
                "Mike Penz; Valentin Fondaratov; Tomasz Kądziołka",
                LicenseKind.APACHE_2_0,
                "https://github.com/mikepenz/multiplatform-markdown-renderer; " +
                    "https://github.com/JetBrains/markdown; https://github.com/SnipMeDev/Highlights",
                additionalNotices = listOf(LicenseNotice("Erik Hellman", LicenseKind.MIT)),
            ),
            // 3.0.2_preview2 source marks the JSR-305 reference implementation as New BSD:
            // https://github.com/findbugsproject/findbugs/blob/3.0.2_preview2/findbugs/licenses/LICENSE-jsr305.txt
            // Full notice and holder: https://chromium.googlesource.com/external/jsr-305/+/fdfdc61084f5084c0ea2fd344c3840c4bda3e893/ri/LICENSE
            LicenseEntry(
                "JSR-305",
                "JSR305 expert group",
                LicenseKind.BSD_3_CLAUSE,
                "http://findbugs.sourceforge.net/",
            ),
            // Holder source for the release version: https://github.com/jspecify/jspecify/blob/v1.0.0/AUTHORS
            LicenseEntry(
                "JSpecify",
                "Google LLC; SpotBugs Team",
                LicenseKind.APACHE_2_0,
                "https://github.com/jspecify/jspecify",
            ),
            LicenseEntry(
                "Аннотации JetBrains",
                "JetBrains s.r.o.",
                LicenseKind.APACHE_2_0,
                "https://github.com/JetBrains/java-annotations",
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
