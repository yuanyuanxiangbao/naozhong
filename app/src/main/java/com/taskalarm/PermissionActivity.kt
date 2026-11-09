package com.taskalarm

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
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
        findViewById<View>(android.R.id.content).applySystemBarsPadding()
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
            getString(R.string.overlay_permission),
            getString(R.string.overlay_permission_desc),
            !PermissionHelper.needsOverlay(this),
        ) { PermissionHelper.openOverlay(this) }

        addRow(
            getString(R.string.battery_optimization),
            getString(R.string.battery_optimization_desc),
            !PermissionHelper.needsBattery(this),
        ) { PermissionHelper.openBattery(this) }

        // 厂商限制无法自动检测，只作为引导入口，不显示开启状态
        addGuideRow(
            getString(R.string.vendor_guide),
            getString(R.string.vendor_guide_desc),
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

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.fade_in, R.anim.slide_out)
    }

    private fun addRow(title: String, description: String, granted: Boolean, action: () -> Unit) {
        val row = buildCard()
        val dot = if (granted) R.drawable.dot_success else R.drawable.dot_danger
        row.addView(buildHeader(title, if (granted) "已开启" else "未开启", granted, dot))
        row.addView(buildDescription(description))
        if (!granted) row.addView(buildAction(action))
        container.addView(row)
    }

    private fun addGuideRow(title: String, description: String, action: () -> Unit) {
        val row = buildCard()
        row.addView(buildHeader(title, null, true, R.drawable.dot_neutral))
        row.addView(buildDescription(description))
        row.addView(buildAction(action))
        container.addView(row)
    }

    private fun buildCard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.bg_card)
        setPadding(18.dp(), 16.dp(), 18.dp(), 16.dp())
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 10.dp() }
    }

    private fun buildHeader(
        title: String,
        state: String?,
        granted: Boolean,
        dotRes: Int,
    ): LinearLayout {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(View(this).apply {
            setBackgroundResource(dotRes)
            layoutParams = LinearLayout.LayoutParams(8.dp(), 8.dp())
        })
        header.addView(TextView(this).apply {
            text = title
            setTextColor(ContextCompat.getColor(this@PermissionActivity, R.color.text_primary))
            textSize = 16f
            setPadding(10.dp(), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        if (state != null) {
            header.addView(TextView(this).apply {
                text = state
                setTextColor(
                    ContextCompat.getColor(
                        this@PermissionActivity,
                        if (granted) R.color.success else R.color.danger
                    )
                )
                textSize = 12f
                letterSpacing = 0.04f
            })
        }
        return header
    }

    private fun buildDescription(description: String) = TextView(this).apply {
        text = description
        setTextColor(ContextCompat.getColor(this@PermissionActivity, R.color.text_secondary))
        textSize = 13f
        setLineSpacing(6f, 1f)
        setPadding(18.dp(), 8.dp(), 0, 0)
    }

    private fun buildAction(action: () -> Unit) = TextView(this).apply {
        text = getString(R.string.open_settings)
        setTextColor(ContextCompat.getColor(this@PermissionActivity, R.color.primary_dark))
        textSize = 13f
        gravity = Gravity.CENTER
        setBackgroundResource(R.drawable.bg_card_soft)
        setPadding(20.dp(), 10.dp(), 20.dp(), 10.dp())
        isClickable = true
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 12.dp()
            marginStart = 18.dp()
        }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
