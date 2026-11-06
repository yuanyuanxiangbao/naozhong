package com.taskalarm

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator

class AlarmService : Service() {
    companion object {
        const val ACTION_START = "com.taskalarm.service.START"
        const val ACTION_TASK_DONE = "com.taskalarm.service.TASK_DONE"
        const val EXTRA_ID = "alarm_id"
        const val EXTRA_WINDOW = "alarm_window"
        const val EXTRA_ROUND = "alarm_round"
    }

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var originalAlarmVolume = -1
    private var currentId = -1
    private var currentRound = 0
    private var currentWindow = -1L
    private var screenLock: PowerManager.WakeLock? = null

    // debug 包用于真机自测时压低音量，release 包才强制拉满，避免测试时吵到用户
    private val quietMode: Boolean by lazy {
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    private val handler = Handler(Looper.getMainLooper())
    private val volumeGuard = object : Runnable {
        override fun run() {
            enforceAlarmVolume()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
        audioManager = getSystemService(AudioManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TASK_DONE -> {
                handleTaskDone(intent)
                return START_NOT_STICKY
            }

            ACTION_START -> {
                val id = intent.getIntExtra(EXTRA_ID, -1)
                if (id >= 0) {
                    startRinging(
                        id,
                        intent.getIntExtra(EXTRA_ROUND, 0),
                        intent.getLongExtra(EXTRA_WINDOW, -1L),
                    )
                }
            }

            null -> {
                val id = AlarmStore.ringingId(this)
                if (id >= 0) {
                    val round = if (AlarmStore.confirmId(this) == id) AlarmStore.confirmRound(this) else 0
                    startRinging(id, round, AlarmStore.ringingWindow(this))
                } else {
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun startRinging(id: Int, round: Int, window: Long) {
        val alarm = AlarmStore.find(this, id)
        if (alarm == null) {
            stopSelf()
            return
        }
        if (currentId == id && player != null) {
            NotificationHelper.showRingingNotification(this, alarm, round)
            return
        }
        currentId = id
        currentRound = round
        currentWindow = window
        val detail = if (round > 0) {
            "%02d:%02d 第 %d 次复查".format(alarm.hour, alarm.minute, round)
        } else {
            "%02d:%02d %s".format(alarm.hour, alarm.minute, alarm.label.ifBlank { "无标签" })
        }
        DiagLog.record(this, "开始响铃", detail)
        acquireScreenLock()
        NotificationHelper.cancelConfirm(this)
        startForeground(NotificationHelper.NOTIFICATION_ID, NotificationHelper.ringingNotification(this, alarm, round))
        startSound(alarm)
        handler.removeCallbacks(volumeGuard)
        handler.post(volumeGuard)
        launchRingScreen(id, round)
    }

    // 屏幕熄灭时仅靠全屏通知不一定点亮屏幕，响铃期间持有一个亮屏锁
    private fun acquireScreenLock() {
        if (screenLock != null) return
        val powerManager = getSystemService(PowerManager::class.java)
        try {
            screenLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                "taskalarm:ringing"
            ).apply {
                setReferenceCounted(false)
                acquire(15 * 60 * 1000L)
            }
        } catch (e: Exception) {
            screenLock = null
        }
    }

    private fun launchRingScreen(id: Int, round: Int) {
        val intent = Intent(this, AlarmRingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(AlarmRingActivity.EXTRA_ID, id)
            putExtra(AlarmRingActivity.EXTRA_WINDOW, AlarmStore.ringingWindow(this@AlarmService))
            putExtra(EXTRA_ROUND, round)
        }
        // Android 14 起后台弹页会被 BAL 拦截；创建者与发送者要分别声明放行，
        // 创建时只能设置创建者选项，发送者的选项必须通过 send 传入
        val launched = if (Build.VERSION.SDK_INT >= 34) {
            try {
                val pending = PendingIntent.getActivity(
                    this, id + 400000, intent,
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    creatorBalOptions(),
                )
                pending.send(null, 0, null, null, null, null, senderBalOptions())
                true
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }
        if (!launched) {
            try {
                startActivity(intent)
            } catch (e: Exception) {
                // 仍然失败时依赖常驻通知，用户点击即可进入答题页
            }
        }
    }

    private fun handleTaskDone(intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, currentId)
        val round = intent.getIntExtra(EXTRA_ROUND, currentRound)
        val alarm = if (id >= 0) AlarmStore.find(this, id) else null
        if (alarm == null) {
            stopRinging()
            return
        }
        val step = ConfirmPlan.next(alarm, round, AlarmStore.confirmLeft(this))
        if (step == null) {
            finishAlarm(id, alarm)
            return
        }
        val now = System.currentTimeMillis()
        val window = currentWindow.takeIf { it > 0 } ?: now
        val deadline = ConfirmPlan.deadline(now, alarm)
        AlarmStore.setConfirm(this, id, window, deadline, step.left, step.round)
        AlarmScheduler.scheduleFollowUp(this, id, step.round, window, deadline)
        NotificationHelper.showConfirm(this, alarm, step.round, deadline)
        DiagLog.record(
            this, "进入起床复查",
            "%02d:%02d 第 %d 次，%d 分钟后".format(
                alarm.hour, alarm.minute, step.round, alarm.confirmMinutes
            )
        )
        stopRinging()
    }

    private fun finishAlarm(id: Int, alarm: Alarm) {
        NotificationHelper.cancelConfirm(this)
        AlarmStore.clearConfirm(this)
        if (alarm.isTest) AlarmStore.delete(this, id)
        DiagLog.record(this, "复查结束", "id=$id，闹钟关闭")
        stopRinging()
    }

    private fun startSound(alarm: Alarm) {
        stopSound()

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val am = audioManager
        if (am != null) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .build()
            focusRequest = request
            am.requestAudioFocus(request)
        }

        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        if (uri != null) {
            try {
                player = MediaPlayer().apply {
                    setAudioAttributes(attributes)
                    setDataSource(this@AlarmService, uri)
                    isLooping = true
                    val level = if (quietMode) 0.25f else 1f
                    setVolume(level, level)
                    setWakeMode(this@AlarmService, PowerManager.PARTIAL_WAKE_LOCK)
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                player = null
            }
        }

        vibrator = getSystemService(Vibrator::class.java)
        try {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0))
        } catch (e: Exception) {
            // 设备可能禁用了震动
        }

        if (originalAlarmVolume < 0) {
            originalAlarmVolume = am?.getStreamVolume(AudioManager.STREAM_ALARM) ?: -1
        }
        enforceAlarmVolume()
    }

    private fun enforceAlarmVolume() {
        val am = audioManager ?: return
        try {
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val target = if (quietMode) (max / 5).coerceAtLeast(1) else max
            am.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
        } catch (e: Exception) {
            // 勿扰模式或系统策略可能拒绝调整音量
        }
    }

    private fun restoreAlarmVolume() {
        if (originalAlarmVolume < 0) return
        try {
            audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, originalAlarmVolume, 0)
        } catch (e: Exception) {
            // 忽略恢复失败
        }
        originalAlarmVolume = -1
    }

    private fun stopSound() {
        try {
            player?.stop()
        } catch (e: Exception) {
            // 播放器可能已经停止
        }
        player?.release()
        player = null
        vibrator?.cancel()
        vibrator = null
        focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun stopRinging() {
        handler.removeCallbacks(volumeGuard)
        restoreAlarmVolume()
        stopSound()
        releaseScreenLock()
        AlarmStore.clearRinging(this)
        DiagLog.record(this, "停止响铃", "id=$currentId")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseScreenLock() {
        try {
            if (screenLock?.isHeld == true) screenLock?.release()
        } catch (e: Exception) {
            // 锁可能已被系统回收
        }
        screenLock = null
    }

    override fun onDestroy() {
        handler.removeCallbacks(volumeGuard)
        restoreAlarmVolume()
        stopSound()
        releaseScreenLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
