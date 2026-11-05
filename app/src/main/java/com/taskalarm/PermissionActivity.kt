package com.taskalarm

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionActivity : AppCompatActivity() {
    private lateinit var container: LinearLayout
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission)
        container = findViewById(R.id.permissionContainer)
        status = findViewById(R.id.permissionStatus)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        container.removeAllViews()

        addRow(
            getString(R.string.exact_alarm),
            getString(R.string.exact_alarm_desc),
            !PermissionHelper.needsExactAlarm(this),
        ) { PermissionHelper.openExactAlarm(this) }

        addRow(
            getString(R.string.notifications),
            getString(R.string.notifications_desc),
            !PermissionHelper.needsNotifications(this),
        ) {
            if (Build.VERSION.SDK_INT >= 33) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2
                )
            } else {
                PermissionHelper.openNotificationSettings(this)
            }
        }

        addRow(
            getString(R.string.fullscreen_intent),
            getString(R.string.fullscreen_intent_desc),
            !PermissionHelper.needsFullScreen(this),
        ) { PermissionHelper.openFullScreen(this) }

        addRow(
            getString(R.string.battery_optimization),
            getString(R.string.battery_optimization_desc),
            !PermissionHelper.needsBattery(this),
        ) { PermissionHelper.openBattery(this) }

        addRow(
            getString(R.string.vendor_guide),
            getString(R.string.vendor_guide_desc),
            true,
        ) { PermissionHelper.openVendorSettings(this) }

        val missing = PermissionHelper.missingCount(this)
        status.text = if (missing == 0) {
            getString(R.string.permission_all_ok)
        } else {
            getString(R.string.permission_missing)
        }
        status.setTextColor(
            ContextCompat.getColor(
                this,
                if (missing == 0) R.color.success else R.color.danger
            )
        )
    }

    private fun addRow(title: String, description: String, granted: Boolean, action: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            setPadding(16, 16, 16, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12 }
        }

        row.addView(TextView(this).apply {
            text = title
            setTextColor(ContextCompat.getColor(this@PermissionActivity, R.color.text_primary))
            textSize = 17f
        })

        row.addView(TextView(this).apply {
            text = description
            setTextColor(ContextCompat.getColor(this@PermissionActivity, R.color.text_secondary))
            textSize = 13f
            setPadding(0, 6, 0, 10)
        })

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        footer.addView(TextView(this).apply {
            text = if (granted) "已开启" else "未开启"
            setTextColor(
                if (granted) Color.parseColor("#2E9E5B") else Color.parseColor("#D64545")
            )
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        if (!granted) {
            footer.addView(Button(this).apply {
                text = getString(R.string.open_settings)
                setOnClickListener { action() }
            })
        }

        row.addView(footer)
        container.addView(row)
    }
}
