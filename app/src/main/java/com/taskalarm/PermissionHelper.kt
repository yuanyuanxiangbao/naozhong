package com.taskalarm

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

object PermissionHelper {
    fun needsExactAlarm(context: Context): Boolean =
        !context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun needsNotifications(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        } else {
            false
        }

    fun needsFullScreen(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 34) {
            !context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        } else {
            false
        }

    fun needsOverlay(context: Context): Boolean = !Settings.canDrawOverlays(context)

    fun needsBattery(context: Context): Boolean =
        !context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)

    fun missingCount(context: Context): Int =
        listOf(
            needsExactAlarm(context),
            needsNotifications(context),
            needsFullScreen(context),
            needsOverlay(context),
            needsBattery(context),
        ).count { it }

    fun openExactAlarm(activity: Activity) {
        try {
            activity.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${activity.packageName}")
                )
            )
        } catch (e: Exception) {
            openAppDetails(activity)
        }
    }

    fun openFullScreen(activity: Activity) {
        try {
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:${activity.packageName}")
                )
            )
        } catch (e: Exception) {
            openAppDetails(activity)
        }
    }

    fun openOverlay(activity: Activity) {
        try {
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${activity.packageName}")
                )
            )
        } catch (e: Exception) {
            openAppDetails(activity)
        }
    }

    fun openBattery(activity: Activity) {
        try {
            activity.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${activity.packageName}")
                )
            )
        } catch (e: Exception) {
            openAppDetails(activity)
        }
    }

    fun openNotificationSettings(activity: Activity) {
        try {
            activity.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
            )
        } catch (e: Exception) {
            openAppDetails(activity)
        }
    }

    fun openAppDetails(activity: Activity) {
        try {
            activity.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${activity.packageName}")
                )
            )
        } catch (e: Exception) {
            // 无法打开设置时保持当前页面
        }
    }

    fun openVendorSettings(activity: Activity): Boolean {
        val components = listOf(
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            ),
            ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            ),
            ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            ),
            ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            ),
        )
        for (component in components) {
            val intent = Intent().setComponent(component)
            if (intent.resolveActivity(activity.packageManager) != null) {
                activity.startActivity(intent)
                return true
            }
        }
        openAppDetails(activity)
        return false
    }
}
