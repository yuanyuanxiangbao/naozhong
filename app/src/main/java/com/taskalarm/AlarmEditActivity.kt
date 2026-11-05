package com.taskalarm

import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import java.time.ZonedDateTime

class AlarmEditActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_ID = "alarm_id"
        private val MATH_COUNTS = listOf(1, 2, 3, 4, 5)
        private val SHAKE_SECONDS = listOf(10, 15, 20, 30, 45)
    }

    private var alarmId = -1
    private var hour = 7
    private var minute = 0

    private lateinit var timeButton: Button
    private lateinit var labelInput: EditText
    private lateinit var mathSwitch: SwitchMaterial
    private lateinit var shakeSwitch: SwitchMaterial
    private lateinit var mathCountSpinner: Spinner
    private lateinit var difficultySpinner: Spinner
    private lateinit var shakeSecondsSpinner: Spinner
    private lateinit var nextRingText: TextView
    private val dayBoxes = mutableListOf<CheckBox>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm_edit)

        timeButton = findViewById(R.id.timeButton)
        labelInput = findViewById(R.id.labelInput)
        mathSwitch = findViewById(R.id.mathSwitch)
        shakeSwitch = findViewById(R.id.shakeSwitch)
        mathCountSpinner = findViewById(R.id.mathCountSpinner)
        difficultySpinner = findViewById(R.id.difficultySpinner)
        shakeSecondsSpinner = findViewById(R.id.shakeSecondsSpinner)
        nextRingText = findViewById(R.id.nextRingText)

        dayBoxes += findViewById<CheckBox>(R.id.day1)
        dayBoxes += findViewById<CheckBox>(R.id.day2)
        dayBoxes += findViewById<CheckBox>(R.id.day3)
        dayBoxes += findViewById<CheckBox>(R.id.day4)
        dayBoxes += findViewById<CheckBox>(R.id.day5)
        dayBoxes += findViewById<CheckBox>(R.id.day6)
        dayBoxes += findViewById<CheckBox>(R.id.day7)

        mathCountSpinner.adapter = SimpleSpinnerAdapter(MATH_COUNTS.map { "$it 题" })
        difficultySpinner.adapter = SimpleSpinnerAdapter(listOf("加减", "加减乘"))
        shakeSecondsSpinner.adapter = SimpleSpinnerAdapter(SHAKE_SECONDS.map { "$it 秒" })

        alarmId = intent.getIntExtra(EXTRA_ID, -1)
        val existing = if (alarmId >= 0) AlarmStore.find(this, alarmId) else null

        if (existing == null) {
            hour = (java.time.LocalTime.now().hour + 1) % 24
            minute = 0
            mathSwitch.isChecked = true
            shakeSwitch.isChecked = false
            mathCountSpinner.setSelection(1)
            difficultySpinner.setSelection(0)
            shakeSecondsSpinner.setSelection(1)
        } else {
            hour = existing.hour
            minute = existing.minute
            labelInput.setText(existing.label)
            mathSwitch.isChecked = existing.mathEnabled
            shakeSwitch.isChecked = existing.shakeEnabled
            mathCountSpinner.setSelection(MATH_COUNTS.indexOf(existing.mathCount).coerceAtLeast(0))
            difficultySpinner.setSelection(if (existing.mathHard) 1 else 0)
            shakeSecondsSpinner.setSelection(
                SHAKE_SECONDS.indexOf(existing.shakeSeconds).coerceAtLeast(0)
            )
            for (i in dayBoxes.indices) {
                dayBoxes[i].isChecked = existing.repeatDays and (1 shl i) != 0
            }
        }

        timeButton.text = "%02d:%02d".format(hour, minute)
        timeButton.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                hour = h
                minute = m
                timeButton.text = "%02d:%02d".format(hour, minute)
                updatePreview()
            }, hour, minute, true).show()
        }

        dayBoxes.forEach { it.setOnCheckedChangeListener { _, _ -> updatePreview() } }
        mathSwitch.setOnCheckedChangeListener { _, _ -> updatePreview() }
        shakeSwitch.setOnCheckedChangeListener { _, _ -> updatePreview() }

        findViewById<Button>(R.id.saveButton).setOnClickListener { save() }
        val deleteButton = findViewById<Button>(R.id.deleteButton)
        if (existing != null) {
            deleteButton.visibility = android.view.View.VISIBLE
            deleteButton.setOnClickListener { confirmDelete() }
        }

        updatePreview()
    }

    private fun selectedRepeatDays(): Int {
        var repeat = 0
        for (i in dayBoxes.indices) {
            if (dayBoxes[i].isChecked) repeat = repeat or (1 shl i)
        }
        return repeat
    }

    private fun updatePreview() {
        val candidate = Alarm(
            hour = hour,
            minute = minute,
            repeatDays = selectedRepeatDays(),
            enabled = true,
        )
        val next = NextTrigger.next(candidate, ZonedDateTime.now())
        nextRingText.text = if (next == null) {
            ""
        } else {
            getString(R.string.next_ring, NextTrigger.format(next))
        }
    }

    private fun save() {
        if (!mathSwitch.isChecked && !shakeSwitch.isChecked) {
            Toast.makeText(this, R.string.need_task_toast, Toast.LENGTH_LONG).show()
            return
        }
        val existing = if (alarmId >= 0) AlarmStore.find(this, alarmId) else null
        val alarm = existing?.copy() ?: Alarm()
        if (alarmId < 0) alarmId = AlarmStore.nextId(this)
        alarm.id = alarmId
        alarm.hour = hour
        alarm.minute = minute
        alarm.label = labelInput.text.toString().trim()
        alarm.repeatDays = selectedRepeatDays()
        alarm.mathEnabled = mathSwitch.isChecked
        alarm.mathCount = MATH_COUNTS[mathCountSpinner.selectedItemPosition]
        alarm.mathHard = difficultySpinner.selectedItemPosition == 1
        alarm.shakeEnabled = shakeSwitch.isChecked
        alarm.shakeSeconds = SHAKE_SECONDS[shakeSecondsSpinner.selectedItemPosition]
        alarm.enabled = true

        AlarmStore.upsert(this, alarm)
        AlarmScheduler.apply(this, alarm)
        DiagLog.record(
            this,
            if (existing == null) "创建闹钟" else "修改闹钟",
            "%02d:%02d %s · %s".format(
                alarm.hour, alarm.minute, alarm.repeatText(), alarm.tasksText()
            )
        )
        finish()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("删除闹钟")
            .setMessage("删除后无法恢复")
            .setPositiveButton("删除") { _, _ ->
                DiagLog.record(this, "删除闹钟", "%02d:%02d".format(hour, minute))
                AlarmScheduler.cancel(this, alarmId)
                AlarmStore.delete(this, alarmId)
                finish()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private inner class SimpleSpinnerAdapter(items: List<String>) :
        android.widget.ArrayAdapter<String>(
            this@AlarmEditActivity,
            android.R.layout.simple_spinner_item,
            items
        ) {
        init {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }
}
