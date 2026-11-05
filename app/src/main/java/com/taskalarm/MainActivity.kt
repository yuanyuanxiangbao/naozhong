package com.taskalarm

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {
    private lateinit var listView: ListView
    private lateinit var emptyText: TextView
    private lateinit var permissionBanner: TextView
    private lateinit var adapter: AlarmAdapter
    private var alarms: MutableList<Alarm> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        listView = findViewById(R.id.alarmList)
        emptyText = findViewById(R.id.emptyText)
        permissionBanner = findViewById(R.id.permissionBanner)

        adapter = AlarmAdapter()
        listView.adapter = adapter

        findViewById<Button>(R.id.newAlarmButton).setOnClickListener { openEditor(-1) }
        findViewById<TextView>(R.id.testButton).setOnClickListener { createTestAlarm() }
        findViewById<TextView>(R.id.logButton).setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }
        findViewById<TextView>(R.id.permissionButton).setOnClickListener {
            startActivity(Intent(this, PermissionActivity::class.java))
        }
        permissionBanner.setOnClickListener {
            startActivity(Intent(this, PermissionActivity::class.java))
        }

        requestNotificationPermission()
        DiagLog.record(this, "打开应用")
    }

    override fun onResume() {
        super.onResume()
        refresh()
        AlarmScheduler.reconcile(this)
    }

    private fun refresh() {
        alarms = AlarmStore.load(this)
        adapter.notifyDataSetChanged()
        val isEmpty = alarms.isEmpty()
        listView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        emptyText.visibility = if (isEmpty) View.VISIBLE else View.GONE
        permissionBanner.visibility =
            if (PermissionHelper.missingCount(this) > 0) View.VISIBLE else View.GONE
    }

    private fun openEditor(id: Int) {
        startActivity(
            Intent(this, AlarmEditActivity::class.java)
                .putExtra(AlarmEditActivity.EXTRA_ID, id)
        )
    }

    private fun createTestAlarm() {
        val target = java.time.ZonedDateTime.now().plusMinutes(1)
        val alarm = Alarm(
            id = AlarmStore.nextId(this),
            hour = target.hour,
            minute = target.minute,
            label = "测试闹钟",
            repeatDays = 0,
            mathEnabled = true,
            mathCount = 1,
            mathHard = true,
            isTest = true,
        )
        AlarmStore.upsert(this, alarm)
        AlarmScheduler.apply(this, alarm)
        DiagLog.record(this, "创建测试闹钟", "%02d:%02d".format(target.hour, target.minute))
        Toast.makeText(this, R.string.test_alarm_toast, Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1
            )
        }
    }

    private inner class AlarmAdapter : BaseAdapter() {
        override fun getCount(): Int = alarms.size

        override fun getItem(position: Int): Alarm = alarms[position]

        override fun getItemId(position: Int): Long = alarms[position].id.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView
                ?: LayoutInflater.from(this@MainActivity)
                    .inflate(R.layout.item_alarm, parent, false)
            val alarm = alarms[position]

            view.findViewById<TextView>(R.id.alarmTime).text =
                "%02d:%02d".format(alarm.hour, alarm.minute)
            val suffix = if (alarm.enabled) "" else " · 已关闭"
            view.findViewById<TextView>(R.id.alarmInfo).text =
                "${alarm.repeatText()} · ${alarm.tasksText()}$suffix"

            val switch = view.findViewById<SwitchMaterial>(R.id.alarmSwitch)
            switch.setOnCheckedChangeListener(null)
            switch.isChecked = alarm.enabled
            switch.setOnCheckedChangeListener { _, checked ->
                val target = AlarmStore.find(this@MainActivity, alarm.id)
                if (target != null) {
                    target.enabled = checked
                    AlarmStore.upsert(this@MainActivity, target)
                    AlarmScheduler.apply(this@MainActivity, target)
                    DiagLog.record(
                        this@MainActivity,
                        if (checked) "启用闹钟" else "关闭闹钟",
                        "%02d:%02d".format(target.hour, target.minute)
                    )
                    refresh()
                }
            }

            view.setOnClickListener { openEditor(alarm.id) }
            return view
        }
    }
}
