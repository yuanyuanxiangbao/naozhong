package com.taskalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color

// 创建 PendingIntent 时只能声明"创建者允许"，发送者的放行选项必须留给 send()，
// 否则 Android 会抛 pendingIntentBackgroundActivityStartMode must not be set
internal fun creatorBalOptions(): android.os.Bundle? =
    if (android.os.Build.VERSION.SDK_INT >= 35) {
        android.app.ActivityOptions.makeBasic().apply {
            setPendingIntentCreatorBackgroundActivityStartMode(
                android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            )
        }.toBundle()
    } else {
        null
    }

internal fun senderBalOptions(): android.os.Bundle? =
    if (android.os.Build.VERSION.SDK_INT >= 34) {
        android.app.ActivityOptions.makeBasic().apply {
            setPendingIntentBackgroundActivityStartMode(
                android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            )
        }.toBundle()
    } else {
        null
    }

object NotificationHelper {
    const val CHANNEL_RINGING = "ringing"
    const val CHANNEL_INFO = "info"
    const val NOTIFICATION_ID = 1001

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val ringing = NotificationChannel(
            CHANNEL_RINGING,
            "闹钟响铃",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "响铃中的常驻通知，无法滑动关闭"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        val info = NotificationChannel(
            CHANNEL_INFO,
            "闹钟提醒",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "漏响提醒与状态提示"
        }
        manager.createNotificationChannels(listOf(ringing, info))
    }

    private fun ringPendingIntent(context: Context, alarm: Alarm): PendingIntent {
        val intent = Intent(context, AlarmRingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(AlarmRingActivity.EXTRA_ID, alarm.id)
            putExtra(AlarmRingActivity.EXTRA_WINDOW, AlarmStore.ringingWindow(context))
        }
        return PendingIntent.getActivity(
            context, alarm.id + 200000, intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            creatorBalOptions(),
        )
    }

    fun ringingNotification(context: Context, alarm: Alarm): Notification {
        ensureChannels(context)
        val fullScreen = ringPendingIntent(context, alarm)
        val title = alarm.label.ifBlank { "闹钟" }
        return Notification.Builder(context, CHANNEL_RINGING)
            .setSmallIcon(R.drawable.ic_alarm)
            .setColor(Color.parseColor("#5B4CDB"))
            .setContentTitle(title)
            .setContentText("点击打开答题页，完成任务后自动关闭")
            .setCategory(Notification.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setPriority(Notification.PRIORITY_MAX)
            .setDeleteIntent(
                PendingIntent.getBroadcast(
                    context, alarm.id + 300000,
                    Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_RESTORE),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .build()
    }

    fun showRingingNotification(context: Context, alarm: Alarm) {
        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, ringingNotification(context, alarm))
    }

    fun showMissed(context: Context, alarm: Alarm) {
        ensureChannels(context)
        val text = "%02d:%02d %s".format(alarm.hour, alarm.minute, alarm.label)
        val notification = Notification.Builder(context, CHANNEL_INFO)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("有闹钟没有按时响")
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID + 1, notification)
    }
}
