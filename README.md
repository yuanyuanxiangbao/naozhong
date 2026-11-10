# 任务闹钟

一个原生 Kotlin 编写的 Android 闹钟。响铃后没有任何"关闭"按钮，必须完成数学题或摇一摇才会停，用来对付睡迷糊时随手把闹钟关掉的情况。

## 功能

- 响铃后全屏弹出答题页，只能通过完成任务停止，没有跳过入口
- 数学题：1 到 5 道，可选加减或加减乘
- 摇一摇：持续摇动 10 到 45 秒
- 起床复查：任务完成后不立即结束，按设定的间隔再响若干次（次数可填 1 到 20）
- 当天锁定：闹钟当天响过之后，到次日零点前不能关闭或删除，防止复查间隙手动取消
- 重复规则：按星期选择，或仅响一次
- 运行日志：记录创建、排定、触发、响铃、复查、拒绝关闭等事件，最多 300 条
- 测试闹钟：应用内一键创建 1 分钟后响铃的临时闹钟，完成后自动删除

## 环境要求

- 设备：Android 12（API 31）及以上
- JDK：17 及以上（开发时使用 JDK 21）
- Android SDK：compileSdk 35，需要已安装对应平台和 build-tools
- 构建工具：Gradle 9.4.1，已通过 wrapper 锁定，不需要单独安装

## 构建

```
git clone https://github.com/yuanyuanxiangbao/naozhong.git
cd naozhong
```

用 Android Studio 直接打开工程即可，会自动生成 `local.properties`。

命令行构建需要先指定 SDK 路径，在项目根目录创建 `local.properties`：

```
sdk.dir=/path/to/Android/Sdk
```

然后执行：

```
./gradlew assembleDebug
```

Windows 使用 `gradlew.bat assembleDebug`。

产物位置：`app/build/outputs/apk/debug/app-debug.apk`

构建 release 版本：

```
./gradlew assembleRelease
```

release 使用 debug 签名，方便自用直接安装，不适合上架应用商店。

## 安装

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

首次覆盖安装如果签名不一致，需要先卸载旧版本。

## 必须开启的系统权限

应用内"权限检查"页面会逐项检测，缺失项可以直接跳转设置。不开启的话闹钟可能不响，或者响铃时答题页弹不出来。

- 精确闹钟：`SCHEDULE_EXACT_ALARM`，没有它系统会延迟甚至丢弃闹钟
- 通知权限：`POST_NOTIFICATIONS`，Android 13 起需要手动授予，用于显示响铃通知
- 全屏通知：`USE_FULL_SCREEN_INTENT`，锁屏时直接弹出答题页
- 后台弹出界面：`SYSTEM_ALERT_WINDOW`，正在使用其他应用时也能把答题页盖到最前
- 忽略电池优化：防止后台被系统清理后闹钟静音

小米、红米等机型还需要在手机管家里单独开启自启动、省电无限制、后台弹出界面、锁屏显示。应用内提供了跳转入口，但部分机型的开关位置不同，需要手动查找。

## 项目结构

```
app/src/main/java/com/taskalarm/
├── Alarm.kt              闹钟数据模型与锁定判断
├── AlarmStore.kt         配置与运行状态持久化
├── NextTrigger.kt        下一次响铃时间计算
├── AlarmScheduler.kt     排定、触发、复查、漏响补偿
├── AlarmReceiver.kt      闹钟广播与复查广播
├── AlarmService.kt       前台响铃服务、音量控制、答题页拉起
├── AlarmRingActivity.kt  锁屏答题页
├── MathTaskView.kt       数学题任务
├── ShakeTaskView.kt      摇一摇任务
├── ConfirmPlan.kt        复查轮次计算
├── NotificationHelper.kt 响铃通知与复查通知
├── MainActivity.kt       闹钟列表
├── AlarmEditActivity.kt  新建与编辑
├── PermissionActivity.kt 权限检查
├── LogActivity.kt        运行日志
├── DiagLog.kt            日志存储
└── LogExportReceiver.kt  adb 日志导出入口
```

## 单元测试

```
./gradlew testDebugUnitTest
```

覆盖下一次响铃时间计算和复查轮次计算。

## 调试日志导出

应用内日志页最多保留 300 条。需要一次性取走全部记录时，用广播触发导出，再直接拉文件，不用截图：

```
adb shell am broadcast -n com.taskalarm/.LogExportReceiver -a com.taskalarm.DUMP_LOG
adb pull /sdcard/Android/data/com.taskalarm/files/diag_log.json
```

导出的是 JSON 数组，按时间升序，字段 `t` 为毫秒时间戳，`e` 为事件，`d` 为细节。应用处于未启动状态时，广播需要加 `--include-stopped-packages`。

## 已知限制

- 摇一摇任务用加速度向量模长判断是否在摇动，静止时受重力影响进度也会缓慢增长，判定还不够严格
- 每轮响铃没有时长上限，不答题会一直响到任务完成
- 当天锁定只能约束应用内的关闭和删除，如果从系统设置里强制停止应用则无法拦截
