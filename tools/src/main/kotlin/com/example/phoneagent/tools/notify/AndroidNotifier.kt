package com.example.phoneagent.tools.notify

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.phoneagent.tools.R
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
            val notificationId = notificationIds.nextId()
            val builder =
                NotificationCompat
                    .Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_brand)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
            val launchIntent =
                applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
            if (launchIntent != null) {
                builder.setContentIntent(
                    PendingIntent.getActivity(
                        applicationContext,
                        notificationId,
                        launchIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            }
            NotificationManagerCompat.from(applicationContext).notify(notificationId, builder.build())
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
private val notificationIds = NotificationIds()
