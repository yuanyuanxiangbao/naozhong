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
    }

    private lateinit var taskContainer: LinearLayout
    private lateinit var taskStatus: TextView
    private lateinit var ringTime: TextView
    private lateinit var ringLabel: TextView

    private var alarmId = -1
    private var currentAlarm: Alarm? = null
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        loadAlarm(intent)
    }

    private fun loadAlarm(intent: Intent) {
        alarmId = intent.getIntExtra(EXTRA_ID, -1)
        if (alarmId < 0) alarmId = AlarmStore.ringingId(this)
        val alarm = if (alarmId >= 0) AlarmStore.find(this, alarmId) else null
        if (alarm == null) {
            finishAndRemoveTask()
            return
        }
        currentAlarm = alarm
        DiagLog.record(this, "打开答题页", "%02d:%02d %s".format(alarm.hour, alarm.minute, alarm.label.ifBlank { "无标签" }))

        ringTime.text = "%02d:%02d".format(alarm.hour, alarm.minute)
        ringLabel.text = alarm.label.ifBlank { "闹钟" }

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
        taskStatus.text = getString(R.string.solve_to_dismiss)
        taskContainer.removeAllViews()

        if (taskTypes.isEmpty()) {
            finishTasks()
        } else {
            showNextTask(alarm)
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
        taskStatus.text = getString(R.string.task_done)
        DiagLog.record(this, "任务完成", "自动关闭闹钟 id=$alarmId")
        startService(
            Intent(this, AlarmService::class.java).setAction(AlarmService.ACTION_STOP)
        )
        AlarmStore.clearRinging(this)
        if (currentAlarm?.isTest == true) {
            AlarmStore.delete(this, alarmId)
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        taskStatus.postDelayed({ finishAndRemoveTask() }, 800)
    }

    override fun onBackPressed() {
        // 兼容旧返回路径；dispatcher 已注册回调，这里再兜一层
        Toast.makeText(this, R.string.solve_to_dismiss, Toast.LENGTH_SHORT).show()
    }
}
