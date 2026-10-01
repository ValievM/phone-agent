package com.example.phoneagent.testing

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class FakeActivityResultRegistryTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `launcher start records its request and input`() {
        val registry = FakeActivityResultRegistry()
        val intent = Intent("example.ACTION")
        showLauncher(registry, intent)

        composeRule.onNodeWithText("Launch").performClick()

        assertEquals(1, registry.launches.size)
        assertSame(intent, registry.launches.single().input)
    }

    @Test
    fun `respondWith delivers a result exactly once`() {
        var activeRegistry = FakeActivityResultRegistry()
        val received = mutableListOf<ActivityResult>()
        val restoration = StateRestorationTester(composeRule)
        val intent = Intent("example.RESULT")
        restoration.setContent {
            ProvideFakeActivityResultRegistry(activeRegistry) {
                val launcher =
                    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                        received += it
                    }
                Button(onClick = { launcher.launch(Intent("example.ACTION")) }) { Text("Launch") }
            }
        }
        composeRule.onNodeWithText("Launch").performClick()

        activeRegistry.respondWith(Activity.RESULT_OK, intent)
        val savedRegistryState = Bundle()
        activeRegistry.onSaveInstanceState(savedRegistryState)
        val restoredRegistry = FakeActivityResultRegistry()
        restoredRegistry.onRestoreInstanceState(savedRegistryState)
        activeRegistry = restoredRegistry
        restoration.emulateSavedInstanceStateRestore()

        assertEquals(1, received.size)
        assertEquals(Activity.RESULT_OK, received.single().resultCode)
        assertSame(intent, received.single().data)
    }

    @Test
    fun `launch failure is thrown from launcher`() {
        val registry = FakeActivityResultRegistry()
        val failure = ActivityNotFoundException("Recognizer unavailable")
        registry.launchFailure = failure
        showLauncher(registry, Intent("example.ACTION"))

        val thrown =
            assertThrows(ActivityNotFoundException::class.java) {
                composeRule.onNodeWithText("Launch").performClick()
            }

        assertSame(failure, thrown)
        assertThrows(IllegalStateException::class.java) {
            registry.respondWith(Activity.RESULT_OK, null)
        }
    }

    @Test
    fun `result reaches launcher registered after saved state restoration`() {
        val registry = FakeActivityResultRegistry()
        val received = mutableListOf<ActivityResult>()
        val restoration = StateRestorationTester(composeRule)
        val intent = Intent("example.ACTION")
        restoration.setContent {
            ProvideFakeActivityResultRegistry(registry) {
                val launcher =
                    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                        received += it
                    }
                Button(onClick = { launcher.launch(intent) }) { Text("Launch") }
            }
        }
        composeRule.onNodeWithText("Launch").performClick()
        restoration.emulateSavedInstanceStateRestore()

        registry.respondWith(Activity.RESULT_OK, Intent("example.RESULT"))

        assertEquals(1, received.size)
        assertEquals(Activity.RESULT_OK, received.single().resultCode)
    }

    @Test
    fun `respondWith without a pending launch fails clearly`() {
        val registry = FakeActivityResultRegistry()

        val failure =
            assertThrows(IllegalStateException::class.java) {
                registry.respondWith(Activity.RESULT_OK, null)
            }

        assertEquals("No pending activity result launch to respond to", failure.message)
    }

    @Test
    fun `respondWith answers the most recent pending launch`() {
        val registry = FakeActivityResultRegistry()
        val firstReceived = mutableListOf<ActivityResult>()
        val lastReceived = mutableListOf<ActivityResult>()
        composeRule.setContent {
            ProvideFakeActivityResultRegistry(registry) {
                val firstLauncher =
                    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                        firstReceived += it
                    }
                val lastLauncher =
                    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                        lastReceived += it
                    }
                Column {
                    Button(onClick = { firstLauncher.launch(Intent("example.FIRST")) }) { Text("Launch first") }
                    Button(onClick = { lastLauncher.launch(Intent("example.LAST")) }) { Text("Launch last") }
                }
            }
        }
        composeRule.onNodeWithText("Launch first").performClick()
        composeRule.onNodeWithText("Launch last").performClick()

        registry.respondWith(Activity.RESULT_OK, Intent("example.RESULT"))

        assertEquals(2, registry.launches.size)
        assertEquals(0, firstReceived.size)
        assertEquals(1, lastReceived.size)
    }

    private fun showLauncher(
        registry: FakeActivityResultRegistry,
        intent: Intent,
        received: MutableList<ActivityResult> = mutableListOf(),
    ) {
        composeRule.setContent {
            ProvideFakeActivityResultRegistry(registry) {
                val launcher =
                    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                        received += it
                    }
                Button(onClick = { launcher.launch(intent) }) { Text("Launch") }
            }
        }
    }
}
