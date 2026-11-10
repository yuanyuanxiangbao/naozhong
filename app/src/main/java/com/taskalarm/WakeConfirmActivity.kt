package com.taskalarm

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

// 给清醒的人一条出路：长按 + 等 30 秒 + 再确认，睡迷糊时走不完这套流程
class WakeConfirmActivity : AppCompatActivity() {
    companion object {
        private const val HOLD_MS = 3000L
        private const val WAIT_MS = 30_000L
        private const val TICK_MS = 100L
        private const val TAP_STEP_MS = 1000L

        // 响铃服务据此暂停兜底拉页，避免把确认页顶掉
        @Volatile
        var visible = false
    }

    private lateinit var holdProgress: ProgressBar
    private lateinit var holdButton: Button
    private lateinit var finalButton: Button
    private lateinit var hint: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var holdStartAt = 0L
    private var holding = false
    private var waiting = false
    private var waitDone = false
    private var finishing = false
    private var waitDeadline = 0L
    private var accelerateCount = 0
    private var lastShownSec = -1
    private var alarmId = -1

    private val holdTicker = object : Runnable {
        override fun run() {
            val held = SystemClock.elapsedRealtime() - holdStartAt
            holdProgress.progress = (held * 100 / HOLD_MS).toInt().coerceAtMost(100)
            if (held >= HOLD_MS) {
                startWait()
            } else {
                handler.postDelayed(this, 50)
            }
        }
    }

    // 用截止时间戳算剩余，避免重复调度把倒计时跑快
    private val waitTicker = object : Runnable {
        override fun run() {
            val left = waitDeadline - SystemClock.elapsedRealtime()
            if (left <= 0) {
                finishWait()
            } else {
                showWaitLeft(left)
                handler.postDelayed(this, TICK_MS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_wake_confirm)
        findViewById<android.view.View>(android.R.id.content).applySystemBarsPadding()

        holdProgress = findViewById(R.id.holdProgress)
        holdButton = findViewById(R.id.holdButton)
        finalButton = findViewById(R.id.finalButton)
        hint = findViewById(R.id.wakeHint)
        alarmId = AlarmStore.ringingId(this)

        holdButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (waiting) {
                        tapAccelerate()
                    } else if (!waitDone) {
                        holding = true
                        holdStartAt = SystemClock.elapsedRealtime()
                        holdProgress.progress = 0
                        hint.text = getString(R.string.wake_hold)
                        handler.post(holdTicker)
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (holding) {
                        holding = false
                        handler.removeCallbacks(holdTicker)
                        if (!waiting && !waitDone) {
                            holdProgress.progress = 0
                            hint.text = getString(R.string.wake_hold_short)
                        }
                    }
                    true
                }

                else -> false
            }
        }

        finalButton.setOnClickListener { finishWake() }
        findViewById<TextView>(R.id.wakeCancel).setOnClickListener {
            finishing = true
            finish()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishing = true
                finish()
            }
        })
    }

    private fun startWait() {
        handler.removeCallbacks(holdTicker)
        holding = false
        waiting = true
        waitDeadline = SystemClock.elapsedRealtime() + WAIT_MS
        accelerateCount = 0
        lastShownSec = -1
        holdProgress.progress = 100
        showWaitLeft(WAIT_MS)
        handler.removeCallbacks(waitTicker)
        handler.postDelayed(waitTicker, TICK_MS)
        DiagLog.record(this, "开始等待确认", "id=$alarmId ${WAIT_MS / 1000} 秒")
    }

    private fun tapAccelerate() {
        waitDeadline -= TAP_STEP_MS
        accelerateCount++
        val left = waitDeadline - SystemClock.elapsedRealtime()
        if (left <= 0) {
            finishWait()
        } else {
            showWaitLeft(left)
        }
    }

    private fun showWaitLeft(leftMs: Long) {
        val sec = ((leftMs + 999) / 1000).toInt().coerceAtLeast(0)
        if (sec == lastShownSec) return
        lastShownSec = sec
        hint.text = getString(R.string.wake_wait, sec)
        holdButton.text = getString(R.string.wake_tap_accel, sec)
    }

    private fun finishWait() {
        handler.removeCallbacks(waitTicker)
        val left = waitDeadline - SystemClock.elapsedRealtime()
        waiting = false
        waitDone = true
        holdProgress.progress = 100
        holdButton.isEnabled = false
        holdButton.text = getString(R.string.wake_hold_done)
        hint.text = getString(R.string.wake_ready)
        finalButton.isEnabled = true
        DiagLog.record(this, "等待结束", "id=$alarmId 剩余 ${left}ms 加速 $accelerateCount 次")
    }

    private fun finishWake() {
        finishing = true
        startService(
            Intent(this, AlarmService::class.java)
                .setAction(AlarmService.ACTION_GIVE_UP)
                .putExtra(AlarmService.EXTRA_ID, alarmId)
        )
        setResult(RESULT_OK)
        finish()
    }

    override fun onResume() {
        super.onResume()
        visible = true
    }

    // 中途切走说明注意力不在这里，整套确认从头再来
    override fun onPause() {
        visible = false
        super.onPause()
        if (!finishing) {
            resetToStart()
        }
    }

    private fun resetToStart() {
        handler.removeCallbacks(holdTicker)
        handler.removeCallbacks(waitTicker)
        holding = false
        waiting = false
        waitDone = false
        lastShownSec = -1
        accelerateCount = 0
        holdProgress.progress = 0
        holdButton.isEnabled = true
        holdButton.text = getString(R.string.wake_hold)
        finalButton.isEnabled = false
        hint.text = getString(R.string.wake_interrupted)
        Toast.makeText(this, R.string.wake_interrupted, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        handler.removeCallbacks(holdTicker)
        handler.removeCallbacks(waitTicker)
        super.onDestroy()
    }
}
