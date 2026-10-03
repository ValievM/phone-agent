package com.example.phoneagent.voice

public object RecognizerLevel {
    public fun normalizeRms(rmsDb: Float): Float {
        if (!rmsDb.isFinite()) return 0f
        return ((rmsDb - MIN_RMS_DB) / RMS_RANGE_DB).coerceIn(0f, 1f)
    }

    private const val MIN_RMS_DB = -2f
    private const val RMS_RANGE_DB = 12f
}

public class LevelThrottle(
    private val minimumChange: Float = MINIMUM_CHANGE,
) {
    private var hasSent = false
    private var lastSent = 0f

    public fun shouldEmit(level: Float): Boolean {
        if (hasSent && level != 0f && kotlin.math.abs(level - lastSent) < minimumChange) return false
        lastSent = level
        hasSent = true
        return true
    }

    private companion object {
        const val MINIMUM_CHANGE = 0.05f
    }
}
