package com.taskalarm

import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import kotlin.math.sqrt

class ShakeTaskView(
    context: Context,
    seconds: Int,
    private val onSolved: () -> Unit,
) : LinearLayout(context), SensorEventListener {

    private val requiredMs = seconds.coerceAtLeast(5).toLong() * 1000L
    private var activeMs = 0L
    private var lastEventAt = 0L
    private var finished = false

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
    private val progressText = TextView(context)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, 12, 0, 12)

        val title = TextView(context).apply {
            text = "用力摇动手机"
            setTextColor(Color.parseColor("#F5F3FF"))
            textSize = 22f
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(context).apply {
            text = "需要持续摇动 $seconds 秒"
            setTextColor(Color.parseColor("#C9C4F2"))
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 22)
        }
        progressBar.max = 100
        progressBar.progress = 0
        progressText.apply {
            setTextColor(Color.parseColor("#F5F3FF"))
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 0)
        }

        addView(title)
        addView(subtitle)
        addView(progressBar, LayoutParams(LayoutParams.MATCH_PARENT, 24))
        addView(progressText)
        updateProgress()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        sensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        if (sensor == null) {
            progressText.text = "设备没有加速度传感器"
        }
    }

    override fun onDetachedFromWindow() {
        sensorManager?.unregisterListener(this)
        super.onDetachedFromWindow()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (finished) return
        val now = SystemClock.elapsedRealtime()
        val magnitude = sqrt(
            event.values[0] * event.values[0] +
                event.values[1] * event.values[1] +
                event.values[2] * event.values[2]
        )
        if (lastEventAt != 0L && magnitude > 8f) {
            activeMs += (now - lastEventAt).coerceAtMost(150L)
        }
        lastEventAt = now
        updateProgress()
        if (activeMs >= requiredMs) {
            finished = true
            onSolved()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun updateProgress() {
        val percent = ((activeMs * 100) / requiredMs).toInt().coerceIn(0, 100)
        progressBar.progress = percent
        progressText.text = "已完成 $percent%"
    }
}
