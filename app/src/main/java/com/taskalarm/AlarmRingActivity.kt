package com.taskalarm

import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.view.WindowManager
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

        // 响铃服务据此判断答题页是否真的在前台，没在前台就继续尝试拉起
        @Volatile
        var visible = false
    }

    private lateinit var taskContainer: LinearLayout
    private lateinit var taskStatus: TextView
    private lateinit var ringTime: TextView
    private lateinit var ringLabel: TextView

    private var alarmId = -1
    private var currentAlarm: Alarm? = null
    private var currentRound = 0
    private val taskTypes = mutableListOf<Int>()
    private var taskIndex = 0
    private var closing = false

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

        taskContainer = findViewById(R.id.taskContainer)
        taskStatus = findViewById(R.id.taskStatus)
        ringTime = findViewById(R.id.ringTime)
        ringLabel = findViewById(R.id.ringLabel)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(this@AlarmRingActivity, R.string.solve_to_dismiss, Toast.LENGTH_SHORT).show()
            }
        })
        loadAlarm(intent)
    }

    override fun onResume() {
        super.onResume()
        visible = true
    }

    override fun onPause() {
        visible = false
        super.onPause()
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
        // 全屏通知和显式启动可能同时拉起同一个 singleTask 页面，保留已答对的进度
        if (newId == alarmId && !closing) {
            if (taskContainer.childCount > 0) {
                currentRound = newRound
                updateRoundText()
            }
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
        updateRoundText()
        taskContainer.removeAllViews()

        if (taskTypes.isEmpty()) {
            finishTasks()
        } else {
            showNextTask(alarm)
        }
    }

    private fun updateRoundText() {
        val alarm = currentAlarm ?: return
        ringLabel.text = if (currentRound > 0) {
            getString(R.string.confirm_round_label, currentRound)
        } else {
            alarm.label.ifBlank { "闹钟" }
        }
        taskStatus.text = if (currentRound > 0) {
            getString(R.string.confirm_round_hint, currentRound)
        } else {
            getString(R.string.solve_to_dismiss)
        }
    }

    private fun hasAccelerometer(): Boolean =
        getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

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

    // 任务全部完成才自动关闭；页面不提供手动关闭入口，避免提前绕过
    private fun finishTasks() {
        if (closing) return
        closing = true
        taskContainer.removeAllViews()
        val alarm = currentAlarm
        val willConfirm = alarm != null && ConfirmPlan.next(alarm, currentRound, AlarmStore.confirmLeft(this)) != null
        taskStatus.text = if (willConfirm) {
            getString(R.string.task_done_confirm, alarm!!.confirmMinutes)
        } else {
            getString(R.string.task_done)
        }
        DiagLog.record(this, "任务完成", "第 $currentRound 次，id=$alarmId")
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

    override fun onBackPressed() {
        // 兼容旧返回路径；dispatcher 已注册回调，这里再兜一层
        Toast.makeText(this, R.string.solve_to_dismiss, Toast.LENGTH_SHORT).show()
    }
}
