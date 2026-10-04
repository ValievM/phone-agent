package com.example.phoneagent.ui.interaction

import com.example.phoneagent.domain.interaction.Capability
import org.junit.Assert.assertEquals
import org.junit.Test

class CapabilityAndroidPermissionsTest {
    @Test
    fun `microphone maps to exactly record audio permission`() {
        assertEquals(listOf(android.Manifest.permission.RECORD_AUDIO), Capability.MICROPHONE.androidPermissions())
    }
}
