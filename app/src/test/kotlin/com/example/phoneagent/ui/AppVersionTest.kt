package com.example.phoneagent.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AppVersionTest {
    @Test
    fun `missing package uses unavailable label`() {
        val packageManager = RuntimeEnvironment.getApplication().packageManager

        assertEquals(
            "неизвестна",
            readAppVersionName(packageManager, "not.installed.phone.agent", "неизвестна"),
        )
    }
}
