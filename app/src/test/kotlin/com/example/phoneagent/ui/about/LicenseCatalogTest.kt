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
                "Java-аннотации (JSR-305, JSpecify и JetBrains)" to LicenseKind.APACHE_2_0,
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
    fun `every catalog license kind has one bundled text mapping`() {
        val catalogKinds = LicenseCatalog.entries.map { it.license }.toSet()

        assertEquals(LicenseKind.entries.toSet(), catalogKinds)
        assertEquals(LicenseKind.entries.toSet(), LicenseTextResources.textResourceIds.keys)
    }
}
