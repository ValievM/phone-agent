package com.example.phoneagent.tools.status

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

internal class AndroidDeviceStatus
    @Inject
    constructor(
        @param:ApplicationContext private val applicationContext: Context,
    ) : DeviceStatusPort {
        override fun read(): DeviceStatus {
            val battery = readBattery()
            val powerSaver = readPowerSaver()
            val storage = readStorage()
            return DeviceStatus(
                batteryPercent = battery.first,
                charging = battery.second,
                powerSaver = powerSaver,
                freeStorageMb = storage.first,
                totalStorageMb = storage.second,
            )
        }

        private fun readBattery(): Pair<Int?, Boolean?> =
            try {
                val intent =
                    applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                        ?: return null to null
                val percent =
                    if (intent.hasExtra(BatteryManager.EXTRA_LEVEL) && intent.hasExtra(BatteryManager.EXTRA_SCALE)) {
                        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 0)
                        if (level >= 0 && scale > 0) ((level.toLong() * BATTERY_PERCENT_SCALE) / scale).toInt() else null
                    } else {
                        null
                    }
                val charging =
                    if (intent.hasExtra(BatteryManager.EXTRA_STATUS)) {
                        when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)) {
                            BatteryManager.BATTERY_STATUS_CHARGING -> true

                            BatteryManager.BATTERY_STATUS_DISCHARGING,
                            BatteryManager.BATTERY_STATUS_NOT_CHARGING,
                            BatteryManager.BATTERY_STATUS_FULL,
                            -> false

                            else -> null
                        }
                    } else {
                        null
                    }
                percent to charging
            } catch (_: Exception) {
                null to null
            }

        private fun readPowerSaver(): Boolean? =
            try {
                applicationContext.getSystemService(PowerManager::class.java)?.isPowerSaveMode
            } catch (_: Exception) {
                null
            }

        private fun readStorage(): Pair<Long, Long> =
            try {
                val stats = StatFs(Environment.getDataDirectory().path)
                stats.availableBytes / BYTES_PER_MEGABYTE to stats.totalBytes / BYTES_PER_MEGABYTE
            } catch (_: Exception) {
                0L to 0L
            }

        private companion object {
            const val BATTERY_PERCENT_SCALE: Int = 100
            const val BYTES_PER_MEGABYTE: Long = 1_048_576L
        }
    }
