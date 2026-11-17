package com.taskalarm

import android.content.Intent
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class AlarmRingActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_ID = "alarm_id"
        const val EXTRA_WINDOW = "alarm_window"
        private const val TYPE_MATH = 1
        private const val TYPE_SHAKE = 2
        private const val TICK_MS = 200L
        private const val CONFIRM_MS = 5000L
        private const val SEC_PER_MATH = 5

        // 响铃服务据此判断答题页是否真的在前台，没在前台就继续尝试拉起
        @Volatile
        var visible = false
    }

    private lateinit var startCard: View
    private lateinit var taskCard: View
    private lateinit var confirmCard: View
    private lateinit var taskContainer: LinearLayout
    private lateinit var taskStatus: TextView
    private lateinit var confirmHint: TextView
    private lateinit var ringTime: TextView
    private lateinit var ringLabel: TextView

    private var alarmId = -1
    private var currentAlarm: Alarm? = null
    private var currentRound = 0
    private val taskTypes = mutableListOf<Int>()
    private var taskIndex = 0
    private var closing = false
    private var finishing = false
    private var taskDeadline = 0L
    private var confirmDeadline = 0L
    private var breathe: ObjectAnimator? = null

    private val handler = Handler(Looper.getMainLooper())

    // 答题限时：数学题按每题 5 秒，摇一摇按设定时长加 5 秒缓冲
    private val taskTicker = object : Runnable {
        override fun run() {
            if (closing || finishing) return
            val left = taskDeadline - SystemClock.elapsedRealtime()
            if (left <= 0) {
                DiagLog.record(this@AlarmRingActivity, "答题超时", "第 $currentRound 次，铃声恢复")
                Toast.makeText(this@AlarmRingActivity, R.string.task_timeout, Toast.LENGTH_SHORT).show()
                backToStart(true)
            } else {
                taskStatus.text = getString(R.string.task_time_left, ((left + 999) / 1000).toInt())
                handler.postDelayed(this, TICK_MS)
            }
        }
    }

    private val confirmTicker = object : Runnable {
        override fun run() {
            if (finishing) return
            val left = confirmDeadline - SystemClock.elapsedRealtime()
            if (left <= 0) {
                DiagLog.record(this@AlarmRingActivity, "起床确认超时", "第 $currentRound 次，铃声恢复")
                Toast.makeText(this@AlarmRingActivity, R.string.wake_confirm_timeout, Toast.LENGTH_SHORT).show()
                backToStart(true)
            } else {
                confirmHint.text = getString(R.string.wake_confirm_hint, ((left + 999) / 1000).toInt())
                handler.postDelayed(this, TICK_MS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        setContentView(R.layout.activity_alarm_ring)
        findViewById<android.view.View>(android.R.id.content).applySystemBarsPadding()

        startCard = findViewById(R.id.startCard)
        taskCard = findViewById(R.id.taskCard)
        confirmCard = findViewById(R.id.confirmCard)
        taskContainer = findViewById(R.id.taskContainer)
        taskStatus = findViewById(R.id.taskStatus)
        confirmHint = findViewById(R.id.confirmHint)
        ringTime = findViewById(R.id.ringTime)
        ringLabel = findViewById(R.id.ringLabel)

        findViewById<Button>(R.id.startButton).setOnClickListener { beginTask() }
        findViewById<Button>(R.id.confirmButton).setOnClickListener { confirmWake() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(this@AlarmRingActivity, R.string.solve_to_dismiss, Toast.LENGTH_SHORT).show()
            }
        })
        loadAlarm(intent)
        playEnterAnimation()
    }

    // 时间先浮出再缓慢呼吸，卡片稍后上浮，模拟老式电子钟的节奏
    private fun playEnterAnimation() {
        ringTime.alpha = 0f
        ringTime.scaleX = 0.94f
        ringTime.scaleY = 0.94f
        ringTime.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(520)
            .setInterpolator(DecelerateInterpolator())
            .start()

        startCard.alpha = 0f
        startCard.translationY = 28 * resources.displayMetrics.density
        startCard.animate()
            .alpha(1f).translationY(0f)
            .setStartDelay(140)
            .setDuration(440)
            .setInterpolator(DecelerateInterpolator())
            .start()

        breathe = ObjectAnimator.ofFloat(ringTime, View.ALPHA, 1f, 0.8f).apply {
            duration = 2600
            startDelay = 760
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
    }

    override fun onDestroy() {
        breathe?.cancel()
        handler.removeCallbacks(taskTicker)
        handler.removeCallbacks(confirmTicker)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        visible = true
    }

    // 正在答题或确认时切走，视为放弃这一轮，铃声立刻恢复
    override fun onPause() {
        visible = false
        super.onPause()
        if (!finishing && startCard.visibility != View.VISIBLE) {
            DiagLog.record(this, "离开答题页", "第 $currentRound 次，铃声恢复")
            backToStart(true)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        loadAlarm(intent)
    }

    private fun loadAlarm(intent: Intent) {
        val newId = intent.getIntExtra(EXTRA_ID, -1).let {
            if (it < 0) AlarmStore.ringingId(this) else it
        }
        val newRound = intent.getIntExtra(AlarmService.EXTRA_ROUND, 0)
        // 同一轮重复拉起时保留当前进度，不再重置
        if (newId == alarmId && newRound == currentRound && !finishing && currentAlarm != null) {
            return
        }
        alarmId = newId
        currentRound = newRound
        val alarm = if (alarmId >= 0) AlarmStore.find(this, alarmId) else null
        if (alarm == null) {
            finishAndRemoveTask()
            return
        }
        currentAlarm = alarm
        DiagLog.record(this, "打开答题页", "%02d:%02d 第 %d 次".format(alarm.hour, alarm.minute, currentRound))

        ringTime.text = "%02d:%02d".format(alarm.hour, alarm.minute)

        taskTypes.clear()
        if (alarm.mathEnabled) taskTypes += TYPE_MATH
        if (alarm.shakeEnabled) {
            if (hasAccelerometer()) {
                taskTypes += TYPE_SHAKE
            } else if (!alarm.mathEnabled) {
                taskTypes += TYPE_MATH
            }
        }

        taskIndex = 0
        closing = false
        finishing = false
        updateRoundText()
        showStart()
    }

    private fun updateRoundText() {
        val alarm = currentAlarm ?: return
        ringLabel.text = if (currentRound > 0) {
            getString(R.string.confirm_round_label, currentRound)
        } else {
            alarm.label.ifBlank { "闹钟" }
        }
    }

    private fun hasAccelerometer(): Boolean =
        getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

    private fun showStart() {
        handler.removeCallbacks(taskTicker)
        handler.removeCallbacks(confirmTicker)
        taskContainer.removeAllViews()
        startCard.visibility = View.VISIBLE
        taskCard.visibility = View.GONE
        confirmCard.visibility = View.GONE
        taskStatus.text = getString(R.string.solve_to_dismiss)
    }

    // 点按钮就静音，不等答完；答题限时内没完成会自动恢复铃声
    private fun beginTask() {
        val alarm = currentAlarm ?: return
        if (taskTypes.isEmpty()) return
        startService(
            Intent(this, AlarmService::class.java)
                .setAction(AlarmService.ACTION_MUTE)
                .putExtra(AlarmService.EXTRA_ID, alarmId)
        )
        val limit = limitSeconds(alarm)
        DiagLog.record(this, "开始答题", "第 $currentRound 次，静音限时 $limit 秒")
        startCard.visibility = View.GONE
        confirmCard.visibility = View.GONE
        taskCard.visibility = View.VISIBLE
        taskIndex = 0
        closing = false
        taskContainer.removeAllViews()
        taskDeadline = SystemClock.elapsedRealtime() + limit * 1000L
        taskStatus.text = getString(R.string.task_time_left, limit)
        showNextTask(alarm)
        handler.removeCallbacks(taskTicker)
        handler.post(taskTicker)
    }

    private fun limitSeconds(alarm: Alarm): Int {
        var seconds = 0
        for (type in taskTypes) {
            when (type) {
                TYPE_MATH -> seconds += alarm.mathCount * SEC_PER_MATH
                TYPE_SHAKE -> seconds += alarm.shakeSeconds + 5
            }
        }
        return seconds.coerceAtLeast(5)
    }

    private fun showNextTask(alarm: Alarm) {
        if (taskIndex >= taskTypes.size) {
            finishTasks()
            return
        }
        taskContainer.removeAllViews()
        when (taskTypes[taskIndex]) {
            TYPE_MATH -> taskContainer.addView(
                MathTaskView(this, alarm.mathCount, alarm.mathHard) {
                    taskIndex++
                    showNextTask(alarm)
                }
            )
            TYPE_SHAKE -> taskContainer.addView(
                ShakeTaskView(this, alarm.shakeSeconds) {
                    taskIndex++
                    showNextTask(alarm)
                }
            )
        }
    }

    // 任务全部完成才进入下一步；最后一轮先弹起床确认，其余轮次直接进入复查
    private fun finishTasks() {
        if (closing) return
        closing = true
        handler.removeCallbacks(taskTicker)
        taskContainer.removeAllViews()
        val alarm = currentAlarm
        val next = if (alarm != null) ConfirmPlan.next(alarm, currentRound, AlarmStore.confirmLeft(this)) else null
        DiagLog.record(this, "任务完成", "第 $currentRound 次，id=$alarmId")
        if (alarm != null && alarm.confirmEnabled && next == null) {
            showConfirm()
            return
        }
        taskStatus.text = if (next != null) {
            getString(R.string.task_done_confirm, alarm!!.confirmMinutes)
        } else {
            getString(R.string.task_done)
        }
        sendTaskDone()
    }

    private fun showConfirm() {
        taskCard.visibility = View.GONE
        confirmCard.visibility = View.VISIBLE
        taskStatus.text = getString(R.string.wake_confirm_footer)
        confirmDeadline = SystemClock.elapsedRealtime() + CONFIRM_MS
        confirmHint.text = getString(R.string.wake_confirm_hint, (CONFIRM_MS / 1000).toInt())
        handler.removeCallbacks(confirmTicker)
        handler.post(confirmTicker)
        DiagLog.record(this, "弹出起床确认", "第 $currentRound 次，限时 5 秒")
    }

    private fun confirmWake() {
        if (finishing) return
        handler.removeCallbacks(confirmTicker)
        DiagLog.record(this, "确认已起床", "第 $currentRound 次，id=$alarmId")
        sendTaskDone()
    }

    private fun sendTaskDone() {
        finishing = true
        handler.removeCallbacks(taskTicker)
        handler.removeCallbacks(confirmTicker)
        startService(
            Intent(this, AlarmService::class.java)
                .setAction(AlarmService.ACTION_TASK_DONE)
                .putExtra(AlarmService.EXTRA_ID, alarmId)
                .putExtra(AlarmService.EXTRA_ROUND, currentRound)
        )
        AlarmStore.clearRinging(this)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        taskStatus.postDelayed({ finishAndRemoveTask() }, 800)
    }

    // 超时或切走时回到初始状态，铃声重新响
    private fun backToStart(resumeRing: Boolean) {
        handler.removeCallbacks(taskTicker)
        handler.removeCallbacks(confirmTicker)
        closing = false
        taskIndex = 0
        showStart()
        if (resumeRing) {
            startService(
                Intent(this, AlarmService::class.java)
                    .setAction(AlarmService.ACTION_RESUME)
                    .putExtra(AlarmService.EXTRA_ID, alarmId)
            )
        }
    }

    override fun onBackPressed() {
        // 兼容旧返回路径；dispatcher 已注册回调，这里再兜一层
        Toast.makeText(this, R.string.solve_to_dismiss, Toast.LENGTH_SHORT).show()
    }
}
