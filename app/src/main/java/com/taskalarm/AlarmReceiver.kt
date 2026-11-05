package com.taskalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_FIRE = "com.taskalarm.FIRE"
        const val ACTION_RESTORE = "com.taskalarm.RESTORE"
        const val EXTRA_ID = "alarm_id"
        const val EXTRA_WINDOW = "alarm_window"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE -> handleFire(context, intent)
            ACTION_RESTORE -> {
                val id = AlarmStore.ringingId(context)
                if (id < 0) return
                val alarm = AlarmStore.find(context, id) ?: return
                NotificationHelper.ensureChannels(context)
                NotificationHelper.showRingingNotification(context, alarm)
            }
        }
    }

    private fun handleFire(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, -1)
        val window = intent.getLongExtra(EXTRA_WINDOW, -1L)
        if (id < 0 || window <= 0) return
        DiagLog.record(context, "收到闹钟广播", "id=$id 计划 ${DiagLog.formatTime(window)}")

        val alarm = AlarmStore.find(context, id) ?: run {
            DiagLog.record(context, "忽略闹钟", "id=$id 已不存在")
            return
        }
        if (!alarm.enabled) {
            DiagLog.record(context, "忽略闹钟", "id=$id 已关闭")
            return
        }
        if (alarm.lastFiredWindow == window) {
            DiagLog.record(context, "忽略闹钟", "id=$id 重复触发")
            return
        }

        val now = System.currentTimeMillis()
        if (now - window > AlarmScheduler.GRACE_MS) {
            AlarmStore.setLastFired(context, id, window)
            DiagLog.record(
                context, "闹钟延迟超限",
                "id=$id 延迟 ${(now - window) / 1000} 秒，标记漏响"
            )
            if (alarm.repeatDays == 0) {
                alarm.enabled = false
                AlarmStore.upsert(context, alarm)
            } else {
                AlarmScheduler.apply(context, alarm)
            }
            NotificationHelper.showMissed(context, alarm)
            return
        }

        AlarmScheduler.trigger(context, id, window)
    }
}
