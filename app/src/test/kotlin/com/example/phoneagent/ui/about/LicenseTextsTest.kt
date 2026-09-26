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
}

private data class LicenseTextExpectation(
    val resourceName: String,
    val markers: List<String>,
    val minimumLength: Int,
)
