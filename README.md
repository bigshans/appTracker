# 应用监控 AppTracker

一个轻量的 Android 前台应用**使用时长**统计工具。

核心目标：以“时间表”的形式展示几点几分哪个应用在前台、持续了多久，并汇总每天每个应用的总时长。

## 设计：时长优先，低耗电

数据来自 Android 系统自己的 `UsageStats` 前台时长统计：

```text
UsageStatsManager.queryEvents(...)
→ 计算每个应用每天累计的前台时长
```

这意味着：

- 不需要无障碍服务
- 不需要前台服务常驻
- 不需要通知栏常驻
- 不需要每秒轮询
- 应用关闭后，系统仍会继续累计时长

应用只在以下时机同步：

1. 打开应用时同步最近 7 天
2. 每天一次 `WorkManager` 后台同步最近 7 天
3. 停留在概览页时每 30 秒刷新今天的数据

因此后台耗电非常低。

## 权限清单

应用实际申请/合并的权限：

| 权限 | 用途 |
|---|---|
| `PACKAGE_USAGE_STATS` | 读取系统 UsageStats，核心功能 |
| `QUERY_ALL_PACKAGES` | 黑名单列表显示应用名称/图标 |
| `RECEIVE_BOOT_COMPLETED` | 开机后恢复同步任务 |
| `WAKE_LOCK` | WorkManager 执行短同步任务时短暂持有 |
| `ACCESS_NETWORK_STATE` | WorkManager 库自带；应用无网络访问 |
| Shizuku API 权限 | 仅在主动使用 Shizuku 授权时 |

明确**不申请**：

- 无障碍服务权限
- `FOREGROUND_SERVICE`
- `POST_NOTIFICATIONS`
- `INTERNET`（应用没有联网权限）
- 每秒轮询、前台服务、通知常驻

使用情况访问可以通过系统设置或 Shizuku 一键授权；Android 16 上部分系统不允许 shell 修改该权限，此时会提示手动授权。

## 功能

- **概览**
  - 今日前台总时长
  - 今日使用过的应用数量
  - 应用排行、时长和占比
  - 上次同步时间 / 立即同步
- **记录**
  - 前台应用时间表：`开始时间 – 结束时间  应用  持续时间`，按时间倒序（最新在最上方）
  - 左右箭头切换日期，点击日期或日历图标可直接选择历史日期
  - 例如：`19:46 – 21:32  Phoenix  1小时46分`
  - 任意日期（受系统/本地数据保留限制）
  - 顶部汇总当天总时长和应用数量
- **应用详情**
  - 单个应用的日期切换（左右箭头 / 日期选择器）
  - 选中日该应用的前台时间表（几点到几点、持续多久）
  - 选中日时长、近 7 天总时长、日均
- **时间表数据**
  - 由 UsageStats 事件重建，相邻同应用时段会自动合并
  - CSV 导出包含日期、开始时间、结束时间、应用、包名、时长
- **统计页**
  - 查询范围：最近 7 / 14 / 30 天，或自定义日期范围（最多 90 天）
  - 总时长、日均、应用数、活跃天数
  - 每日前台时长柱状图
  - 区间内应用排行和占比
- **显示过滤**
  - 默认隐藏短于 5 秒的时段/应用排行
  - 可选：不隐藏、5 秒、10 秒、30 秒、1 分钟、3 分钟
  - 只影响显示，原始数据仍保留
- **应用黑名单**
  - 支持搜索应用名/包名
  - 支持显示全部系统组件
  - 黑名单应用不计入统计；取消后已保存的数据会重新显示
  - 应用详情页菜单也可直接加入/移出黑名单
- **数据**
  - 立即同步
  - 导出 CSV（日期、应用、包名、前台时长）
  - 清空本地数据

所有数据仅保存在本机，应用没有声明 `INTERNET` 权限。

## 统计口径

- 只统计普通应用的前台时长
- 桌面/Launcher、本应用自身、输入法、系统界面不计入
- 同一应用同一天多次使用会累计
- 跨午夜的会话会按实际重叠时间拆分到两天
- 单个应用当天时长低于 1 秒的零碎使用会被忽略
- 默认显示过滤阈值为 5 秒，可在设置中调整；过滤只影响界面和排行，不删除数据
- 时间表会将同一应用、间隔不超过 5 秒的相邻时段合并，减少 Activity 切换造成的碎片

## 构建

要求：JDK 17+、Android SDK 36、Gradle 9.x。

```bash
# Debug
./gradlew assembleDebug

# Release（已签名）
./gradlew assembleRelease bundleRelease
```

产物：

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
app/build/outputs/bundle/release/app-release.aab
```

安装 Release：

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

> 如果手机上装的是 Debug 版，Release 使用不同签名，无法直接覆盖安装。需要先卸载 Debug（会清空应用数据），或先导出 CSV 备份。

### Release 签名

签名文件不会提交到仓库：

```text
keystore/apptracker-release.jks
keystore.properties
```

`keystore.properties` 格式参考 `keystore.properties.example`。这两个文件需要单独备份，丢失后无法继续发布相同签名的新版本。

当前正式版：

```text
versionCode: 20
versionName: 2.5.1
minSdk: 26
targetSdk: 36
```

## 代码结构

```text
app/src/main/java/dev/aerian/apptracker/
├── MainActivity.java                 底部导航、每日同步任务调度
├── data/
│   ├── UsageSync.java                从 UsageStats 事件计算每日时长
│   ├── UsageSyncWorker.java          每天一次的后台同步
│   ├── UsageStore.java               每日时长 + 前台时段 SQLite 存储
│   ├── UsageInterval.java           前台时段模型
│   ├── DisplayPrefs.java            显示过滤阈值
│   ├── DailyTotal.java              每日总时长（图表）
│   ├── AppUsage.java / DayUsage.java 数据模型
│   ├── BlacklistStore.java           黑名单持久化
│   └── AppLabelResolver.java         应用名称缓存
├── ui/
│   ├── OverviewFragment.java         今日概览
│   ├── HistoryFragment.java          按天查看应用时长
│   ├── AppDetailActivity.java        单应用最近 14 天
│   ├── StatsFragment.java            范围统计、柱状图、应用排行
│   ├── SettingsFragment.java         权限、同步、过滤、导出、黑名单入口
│   ├── BlacklistActivity.java        黑名单管理
│   ├── view/DailyBarChartView.java   每日时长柱状图
│   └── adapter/                      应用排行 / 时间表适配器
└── util/
    ├── AppUtils.java                 使用情况访问权限、应用分类
    ├── HomePackages.java             所有 Launcher 排除
    ├── ShizukuHelper.java            Shizuku 授权
    ├── TimeUtils.java                时长格式化
    └── DayUtils.java                 本地时区日边界
```

## 异形屏适配

- 主界面、记录、设置、应用详情、黑名单页都使用 `systemBars + displayCutout` 安全区
- 顶部取状态栏和挖孔/刘海安全区的最大值，左右也处理了横屏挖孔
- 底部预留导航栏安全区

## 已知限制

- 数据同步不是实时的：打开应用时和每天一次；停留在应用内时每 30 秒刷新今天的数据。
- 历史数据依赖系统 `UsageStats` 的保留周期；首次同步最多回溯最近约 7 天。之后每次自动同步都会把数据保存到本地，因此历史会持续累积，不限于一周。
- 如果应用超过一周没有打开，且系统的后台任务也被完全限制，则中间未同步的日期可能无法补回。
- 系统可能在极少数情况下合并/丢弃活动事件，导致时长有少量误差。
- 多窗口/分屏下按系统上报的前台事件统计。
- 桌面、本应用自身、输入法、系统界面不统计。
- 黑名单只隐藏统计，不删除已保存的历史数据；CSV 导出也不包含黑名单应用。
