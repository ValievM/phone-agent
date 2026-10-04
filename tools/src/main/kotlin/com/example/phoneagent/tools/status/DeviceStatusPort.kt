package com.example.phoneagent.tools.status

/** Reads a one-time snapshot of device status without requesting permissions or starting observers. */
internal interface DeviceStatusPort {
    fun read(): DeviceStatus
}

internal data class DeviceStatus(
    val batteryPercent: Int?,
    val charging: Boolean?,
    val powerSaver: Boolean?,
    val freeStorageMb: Long,
    val totalStorageMb: Long,
)
