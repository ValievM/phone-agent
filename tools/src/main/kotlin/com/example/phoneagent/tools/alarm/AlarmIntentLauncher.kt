package com.example.phoneagent.tools.alarm

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

internal class AlarmIntentLauncher
    @Inject
    constructor(
        @param:ApplicationContext private val applicationContext: Context,
    ) {
        fun launch(spec: AlarmIntentSpec): AlarmLaunchResult {
            val intent = spec.toAndroidIntent()
            if (intent.resolveActivity(applicationContext.packageManager) == null) {
                return AlarmLaunchResult.NoClockApp
            }

            return try {
                applicationContext.startActivity(intent)
                AlarmLaunchResult.Started
            } catch (_: ActivityNotFoundException) {
                AlarmLaunchResult.NoClockApp
            } catch (_: SecurityException) {
                AlarmLaunchResult.Failed
            }
        }
    }

internal sealed interface AlarmLaunchResult {
    data object Started : AlarmLaunchResult

    data object NoClockApp : AlarmLaunchResult

    data object Failed : AlarmLaunchResult
}

private fun AlarmIntentSpec.toAndroidIntent(): Intent =
    Intent(action)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .also { intent ->
            extras.forEach { (key, value) ->
                when (value) {
                    is Boolean -> intent.putExtra(key, value)
                    is Int -> intent.putExtra(key, value)
                    is String -> intent.putExtra(key, value)
                }
            }
        }
