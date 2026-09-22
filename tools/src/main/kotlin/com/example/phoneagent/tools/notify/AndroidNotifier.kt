package com.example.phoneagent.tools.notify

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

internal class AndroidNotifier
    @Inject
    constructor(
        @param:ApplicationContext private val applicationContext: Context,
    ) : Notifier {
        // POST_NOTIFICATIONS is gated by NotifyTool calling UserInteraction.ensure(NOTIFICATIONS)
        // before this is ever invoked; lint can't see across that call, hence the suppression.
        @SuppressLint("MissingPermission")
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

        // minSdk is already 26 (O); no version gate needed to create a notification channel.
        private fun createChannelIfNeeded() {
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
