package com.example.phoneagent.tools.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

internal class AndroidNotifier
    @Inject
    constructor(
        @param:ApplicationContext private val applicationContext: Context,
    ) : Notifier {
        override fun notify(
            title: String,
            message: String,
        ) {
            createChannelIfNeeded()
            val notification =
                NotificationCompat
                    .Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .build()
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        }

        private fun createChannelIfNeeded() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

            val channel =
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    NotifyCopy.CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

private const val NOTIFICATION_CHANNEL_ID: String = "phone_agent_notifications"
private const val NOTIFICATION_ID: Int = 1
