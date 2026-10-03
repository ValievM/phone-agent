package com.example.phoneagent.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class RecognizerLevelTest {
    @Test
    fun `rms values are normalized and clamped`() {
        assertEquals(0f, RecognizerLevel.normalizeRms(-2f), 0f)
        assertEquals(0.5f, RecognizerLevel.normalizeRms(4f), 0f)
        assertEquals(1f, RecognizerLevel.normalizeRms(10f), 0f)
        assertEquals(0f, RecognizerLevel.normalizeRms(-50f), 0f)
        assertEquals(1f, RecognizerLevel.normalizeRms(99f), 0f)
        assertEquals(0f, RecognizerLevel.normalizeRms(Float.NaN), 0f)
        assertEquals(0f, RecognizerLevel.normalizeRms(Float.POSITIVE_INFINITY), 0f)
        assertEquals(0f, RecognizerLevel.normalizeRms(Float.NEGATIVE_INFINITY), 0f)
    }

    @Test
    fun `level throttle suppresses small changes but allows a reset to zero`() {
        val throttle = LevelThrottle()

        assertEquals(true, throttle.shouldEmit(0.5f))
        assertEquals(false, throttle.shouldEmit(0.54f))
        assertEquals(true, throttle.shouldEmit(0.55f))
        assertEquals(true, throttle.shouldEmit(0f))
        assertEquals(false, throttle.shouldEmit(0.01f))
    }
}
