package com.taskalarm

import android.content.Context
import android.content.res.ColorStateList
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
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
            setTextColor(ContextCompat.getColor(context, R.color.ring_text))
            textSize = 22f
            gravity = Gravity.CENTER
            letterSpacing = 0.04f
        }
        val subtitle = TextView(context).apply {
            text = "需要持续摇动 $seconds 秒"
            setTextColor(ContextCompat.getColor(context, R.color.ring_text_dim))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 26)
        }
        progressBar.max = 100
        progressBar.progress = 0
        progressBar.progressTintList = ColorStateList.valueOf(
            ContextCompat.getColor(context, R.color.ring_accent)
        )
        progressBar.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(context, R.color.ring_card)
        )
        progressText.apply {
            setTextColor(ContextCompat.getColor(context, R.color.ring_text))
            textSize = 16f
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
