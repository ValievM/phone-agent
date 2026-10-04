package com.example.phoneagent.ui.designsystem.components

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AnswerSharingTest {
    @Test
    fun `share chooser carries original answer as plain text`() {
        val chooser = shareIntent("текст")

        assertNotNull(chooser)
        assertEquals(Intent.ACTION_CHOOSER, chooser?.action)
        val sendIntent = chooser?.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        assertEquals(Intent.ACTION_SEND, sendIntent?.action)
        assertEquals("text/plain", sendIntent?.type)
        assertEquals("текст", sendIntent?.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun `empty answer cannot be shared`() {
        assertNull(shareIntent(""))
    }
}
