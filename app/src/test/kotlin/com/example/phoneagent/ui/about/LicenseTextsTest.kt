package com.example.phoneagent.ui.about

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LicenseTextsTest {
    @Test
    fun `official license texts are present and not truncated`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val expectedTexts =
            listOf(
                LicenseTextExpectation(
                    resourceName = "license_apache_2_0",
                    markers = listOf("Apache License", "Version 2.0, January 2004", "limitations under the License."),
                    minimumLength = 11_000,
                ),
                LicenseTextExpectation(
                    resourceName = "license_ofl_1_1",
                    markers = listOf("SIL OPEN FONT LICENSE Version 1.1", "OTHER DEALINGS IN THE FONT SOFTWARE."),
                    minimumLength = 4_500,
                ),
                LicenseTextExpectation(
                    resourceName = "license_bsd_3_clause",
                    markers =
                        listOf(
                            "Copyright 2008 Google Inc.  All rights reserved.",
                            "Redistribution and use",
                            "support library is itself covered by the above license.",
                        ),
                    minimumLength = 1_700,
                ),
                LicenseTextExpectation(
                    resourceName = "license_mit",
                    markers =
                        listOf(
                            "MIT License",
                            "Copyright (c) 2021 Erik Hellman",
                            "Permission is hereby granted",
                            "THE SOFTWARE IS PROVIDED \"AS IS\"",
                            "SOFTWARE.",
                        ),
                    minimumLength = 1_000,
                ),
            )

        expectedTexts.forEach { expectation ->
            val resourceId = context.resources.getIdentifier(expectation.resourceName, "raw", context.packageName)
            assertTrue("${expectation.resourceName} resource must exist", resourceId != 0)
            val text =
                context.resources
                    .openRawResource(resourceId)
                    .bufferedReader()
                    .use { it.readText() }
            expectation.markers.forEach { marker ->
                assertTrue("${expectation.resourceName} must contain $marker", text.contains(marker))
            }
            assertTrue(
                "${expectation.resourceName} must contain the complete license text",
                text.length > expectation.minimumLength,
            )
        }
    }

    @Test
    fun `Boost Software License text is present and complete`() {
        val text = rawText("license_boost_1_0")

        listOf(
            "Boost Software License - Version 1.0 - August 17th, 2003",
            "Permission is hereby granted, free of charge",
            "THE SOFTWARE IS PROVIDED \"AS IS\"",
            "DEALINGS IN THE SOFTWARE.",
        ).forEach { marker ->
            assertTrue("Boost Software License must contain $marker", text.contains(marker))
        }
        assertTrue("Boost Software License text must be complete", text.length > 1_000)
    }

    @Test
    fun `Kotlin time copyright and BSD terms are included`() {
        val text = rawText("license_bsd_3_clause")

        listOf(
            "Copyright (c) 2007-present, Stephen Colebourne & Michael Nascimento Santos.",
            "Redistribution and use in source and binary forms",
            "Neither the name of JSR-310 nor the names of its contributors",
            "SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.",
        ).forEach { marker ->
            assertTrue("BSD license bundle must contain $marker", text.contains(marker))
        }
        assertTrue("Both BSD notices must be included in full", text.length > 2_700)
    }

    private fun rawText(resourceName: String): String {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resourceId = context.resources.getIdentifier(resourceName, "raw", context.packageName)
        assertTrue("$resourceName resource must exist", resourceId != 0)
        return context.resources
            .openRawResource(resourceId)
            .bufferedReader()
            .use { it.readText() }
    }
}

private data class LicenseTextExpectation(
    val resourceName: String,
    val markers: List<String>,
    val minimumLength: Int,
)
