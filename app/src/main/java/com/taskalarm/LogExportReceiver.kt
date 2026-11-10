package com.taskalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File

// adb 调试入口：adb shell am broadcast -a com.taskalarm.DUMP_LOG
// 触发后日志落到应用外部目录，直接 adb pull 取走
class LogExportReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val dir = context.getExternalFilesDir(null) ?: return
        val file = File(dir, "diag_log.json")
        file.writeText(DiagLog.exportJson(context))
        Log.i("TaskAlarm", "日志已导出 ${DiagLog.all(context).size} 条到 ${file.absolutePath}")
    }
}
