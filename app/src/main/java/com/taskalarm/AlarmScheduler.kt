package com.taskalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import java.time.ZonedDateTime

object AlarmScheduler {
    const val GRACE_MS = 10 * 60 * 1000L

    fun scheduleAll(context: Context) {
        AlarmStore.load(context).forEach { apply(context, it) }
    }

    fun apply(context: Context, alarm: Alarm) {
        cancel(context, alarm.id)
        val previous = AlarmStore.getExpected(context, alarm.id)
        if (!alarm.enabled) {
            AlarmStore.setExpected(context, alarm.id, null)
            if (previous != null) DiagLog.record(context, "取消排定", "%02d:%02d 已关闭".format(alarm.hour, alarm.minute))
            return
        }
        val next = NextTrigger.next(alarm, ZonedDateTime.now()) ?: return
        val millis = NextTrigger.windowMillis(next)
        AlarmStore.setExpected(context, alarm.id, millis)
        scheduleAt(context, alarm.id, millis)
        if (previous != millis) {
            DiagLog.record(
                context, "排定闹钟",
                "%02d:%02d %s，下次 %s".format(
                    alarm.hour, alarm.minute, alarm.repeatText(), NextTrigger.format(next)
                )
            )
        }
    }

    fun scheduleNext(context: Context, id: Int) {
        val alarm = AlarmStore.find(context, id) ?: return
        apply(context, alarm)
    }

    private fun scheduleAt(context: Context, id: Int, millis: Long) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val operation = PendingIntent.getBroadcast(
            context, id,
            Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_FIRE
                putExtra(AlarmReceiver.EXTRA_ID, id)
                putExtra(AlarmReceiver.EXTRA_WINDOW, millis)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val showIntent = PendingIntent.getActivity(
            context, id + 100000,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(millis, showIntent), operation)
        } catch (e: SecurityException) {
            // 没有精确闹钟权限时退回允许空闲唤醒的调度，至少不会完全静默
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation)
        }
    }

    fun cancel(context: Context, id: Int) {
        val pending = PendingIntent.getBroadcast(
            context, id,
            Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_FIRE),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(pending)
        pending.cancel()
    }

    fun trigger(context: Context, alarmId: Int, window: Long) {
        val alarm = AlarmStore.find(context, alarmId) ?: return
        AlarmStore.setLastFired(context, alarmId, window)
        AlarmStore.setRinging(context, alarmId, window)

        if (alarm.repeatDays == 0) {
            // 本地对象也要带上本次窗口，否则 upsert 会把 lastFiredWindow 覆盖回 -1
            alarm.lastFiredWindow = window
            alarm.enabled = false
            AlarmStore.upsert(context, alarm)
        } else {
            // 先把下一次排上，避免用户一直不关闭导致后续闹钟丢失
            AlarmStore.find(context, alarmId)?.let { apply(context, it) }
        }

        DiagLog.record(
            context, "闹钟触发",
            "%02d:%02d %s，计划 %s".format(
                alarm.hour, alarm.minute, alarm.label.ifBlank { "无标签" },
                DiagLog.formatTime(window)
            )
        )

        val intent = Intent(context, AlarmService::class.java).apply {
            action = AlarmService.ACTION_START
            putExtra(AlarmService.EXTRA_ID, alarmId)
            putExtra(AlarmService.EXTRA_WINDOW, window)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun reconcile(context: Context) {
        val now = System.currentTimeMillis()
        val ringing = AlarmStore.isRinging(context)

        AlarmStore.load(context).forEach { alarm ->
            val expected = AlarmStore.getExpected(context, alarm.id)
            val due = alarm.enabled && expected != null && expected <= now &&
                alarm.lastFiredWindow != expected

            when {
                due && !ringing && now - expected!! <= GRACE_MS -> {
                    trigger(context, alarm.id, expected)
                }

                due -> {
                    AlarmStore.setLastFired(context, alarm.id, expected!!)
                    DiagLog.record(
                        context, "判定漏响",
                        "%02d:%02d 计划 %s，已超过补偿窗口".format(
                            alarm.hour, alarm.minute, DiagLog.formatTime(expected)
                        )
                    )
                    if (alarm.repeatDays == 0) {
                        alarm.enabled = false
                        alarm.lastFiredWindow = expected
                        AlarmStore.upsert(context, alarm)
                    } else {
                        apply(context, alarm)
                    }
                    NotificationHelper.showMissed(context, alarm)
                }

                else -> apply(context, alarm)
            }
        }
    }
}
