package com.example.phoneagent.ui.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseCatalogTest {
    @Test
    fun `catalog lists each bundled project family exactly once`() {
        val expectedLicenseKinds =
            mapOf(
                "Inter" to LicenseKind.OFL_1_1,
                "JetBrains Mono" to LicenseKind.OFL_1_1,
                "Material Symbols Rounded" to LicenseKind.APACHE_2_0,
                "AndroidX" to LicenseKind.APACHE_2_0,
                "Jetpack Compose" to LicenseKind.APACHE_2_0,
                "Dagger и Hilt" to LicenseKind.APACHE_2_0,
                "API внедрения зависимостей (JSR-330 и Jakarta Inject)" to LicenseKind.APACHE_2_0,
                "Google Guava ListenableFuture" to LicenseKind.APACHE_2_0,
                "OkHttp и Okio" to LicenseKind.APACHE_2_0,
                "Kotlin: стандартная библиотека, корутины, сериализация и неизменяемые коллекции" to LicenseKind.APACHE_2_0,
                "Markdown renderer и подсветка кода" to LicenseKind.APACHE_2_0,
                "JSR-305" to LicenseKind.BSD_3_CLAUSE,
                "JSpecify" to LicenseKind.APACHE_2_0,
                "Аннотации JetBrains" to LicenseKind.APACHE_2_0,
                "Protocol Buffers (внутри DataStore)" to LicenseKind.BSD_3_CLAUSE,
            )

        assertEquals(expectedLicenseKinds, LicenseCatalog.entries.associate { it.name to it.license })
        assertEquals(
            LicenseCatalog.entries.size,
            LicenseCatalog.entries
                .map { it.name }
                .toSet()
                .size,
        )
        assertTrue(LicenseCatalog.entries.all { it.holder.isNotBlank() && it.source.startsWith("http") })
    }

    @Test
    fun `markdown renderer includes MIT attribution for composer`() {
        val markdownEntry = LicenseCatalog.entries.single { it.name == "Markdown renderer и подсветка кода" }

        assertTrue("MIT license kind must be available", LicenseKind.MIT in LicenseKind.entries)
        assertEquals(listOf(LicenseNotice("Erik Hellman", LicenseKind.MIT)), markdownEntry.additionalNotices)
    }

    @Test
    fun `Kotlin stdlib exposes nested Boost and BSD notices`() {
        val kotlinEntry =
            LicenseCatalog.entries.single {
                it.name == "Kotlin: стандартная библиотека, корутины, сериализация и неизменяемые коллекции"
            }

        assertEquals(
            listOf(
                "Eric Ford and Hubert Holin" to "BOOST_1_0",
                "Stephen Colebourne; Michael Nascimento Santos" to "BSD_3_CLAUSE",
            ),
            kotlinEntry.additionalNotices.map { it.holder to it.license.name },
        )
    }

    @Test
    fun `Java annotation entries name verified rights holders`() {
        val jsr305Entry = LicenseCatalog.entries.single { it.name == "JSR-305" }
        val jspecifyEntry = LicenseCatalog.entries.single { it.name == "JSpecify" }
        val jetBrainsEntry = LicenseCatalog.entries.single { it.name == "Аннотации JetBrains" }

        assertEquals("JSR305 expert group", jsr305Entry.holder)
        assertEquals("Google LLC; SpotBugs Team", jspecifyEntry.holder)
        assertEquals("JetBrains s.r.o.", jetBrainsEntry.holder)
    }

    @Test
    fun `every catalog license kind has one bundled text mapping`() {
        val catalogKinds =
            LicenseCatalog.entries
                .flatMap { entry -> listOf(entry.license) + entry.additionalNotices.map { it.license } }
                .toSet()

        assertEquals(LicenseKind.entries.toSet(), catalogKinds)
        assertEquals(LicenseKind.entries.toSet(), LicenseTextResources.textResourceIds.keys)
        assertTrue(LicenseKind.entries.any { it.name == "BOOST_1_0" })
    }
}
